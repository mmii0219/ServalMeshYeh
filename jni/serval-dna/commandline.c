/*
Serval DNA command-line functions
Copyright (C) 2010-2013 Serval Project Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

#include "serval.h"
#include "conf.h"
#include "mdp_client.h"
#include "cli.h"
#include "keyring.h"
#include "dataformats.h"
#include "commandline.h"
#include "server.h"

DEFINE_CMD(commandline_usage,CLIFLAG_PERMISSIVE_CONFIG,
  "Display command usage.",
  "help|-h|--help","...");
static int commandline_usage(const struct cli_parsed *parsed, struct cli_context *UNUSED(context))
{
  printf("Serval DNA version %s\nUsage:\n", version_servald);
  return cli_usage_parsed(parsed, XPRINTF_STDIO(stdout));
}

DEFINE_CMD(version_message,CLIFLAG_PERMISSIVE_CONFIG,
  "Display copyright information.",
  "version|copyright");
static int version_message(const struct cli_parsed *UNUSED(parsed), struct cli_context *UNUSED(context))
{
  printf("Serval DNA version %s\n%s\n", version_servald, copyright_servald);
  printf("\
License GPLv2+: GNU GPL version 2 or later <http://gnu.org/licenses/gpl.html>.\n\
This is free software: you are free to change and redistribute it.\n\
There is NO WARRANTY, to the extent permitted by law.\n\
");
  return 0;
}

/* Data structures for accumulating output of a single JNI call.
*/

#ifdef HAVE_JNI_H

#define OUTV_BUFFER_ALLOCSIZE	(8192)

jclass IJniResults = NULL;
jmethodID startResultSet, setColumnName, putString, putBlob, putLong, putDouble, totalRowCount;

static int outv_growbuf(struct cli_context *context, size_t needed)
{
  assert(context->outv_current <= context->outv_limit);
  size_t remaining = (size_t)(context->outv_limit - context->outv_current);
  if (remaining < needed) {
    size_t cursize = context->outv_current - context->outv_buffer;
    size_t newsize = cursize + needed;
    // Round up to nearest multiple of OUTV_BUFFER_ALLOCSIZE.
    newsize = newsize + OUTV_BUFFER_ALLOCSIZE - ((newsize - 1) % OUTV_BUFFER_ALLOCSIZE + 1);
    assert(newsize > cursize);
    assert((size_t)(newsize - cursize) >= needed);
    context->outv_buffer = realloc(context->outv_buffer, newsize);
    if (context->outv_buffer == NULL)
      return WHYF("Out of memory allocating %lu bytes", (unsigned long) newsize);
    context->outv_current = context->outv_buffer + cursize;
    context->outv_limit = context->outv_buffer + newsize;
  }
  return 0;
}

static int put_blob(struct cli_context *context, jbyte *value, jsize length){
  jbyteArray arr = NULL;
  if (context->jni_exception)
    return -1;
  if (value && length>0){
    arr = (*context->jni_env)->NewByteArray(context->jni_env, length);
    if (arr == NULL || (*context->jni_env)->ExceptionCheck(context->jni_env)) {
      context->jni_exception = 1;
      return WHY("Exception thrown from NewByteArray()");
    }
    (*context->jni_env)->SetByteArrayRegion(context->jni_env, arr, 0, length, value);
    if ((*context->jni_env)->ExceptionCheck(context->jni_env)) {
      context->jni_exception = 1;
      return WHYF("Exception thrown from SetByteArrayRegion()");
    }
  }
  (*context->jni_env)->CallVoidMethod(context->jni_env, context->jniResults, putBlob, arr);
  if ((*context->jni_env)->ExceptionCheck(context->jni_env)) {
    context->jni_exception = 1;
    return WHY("Exception thrown from CallVoidMethod(putBlob)");
  }
  if (arr)
    (*context->jni_env)->DeleteLocalRef(context->jni_env, arr);
  return 0;
}

static int outv_end_field(struct cli_context *context)
{
  jsize length = context->outv_current - context->outv_buffer;
  context->outv_current = context->outv_buffer;
  return put_blob(context, (jbyte *)context->outv_buffer, length);
}

int Throw(JNIEnv *env, const char *class, const char *msg)
{
  jclass exceptionClass = NULL;
  if ((exceptionClass = (*env)->FindClass(env, class)) == NULL)
    return -1; // exception
  (*env)->ThrowNew(env, exceptionClass, msg);
  return -1;
}

/* JNI entry point to command line.  See org.servalproject.servald.ServalD class for the Java side.
   JNI method descriptor: "(Ljava/util/List;[Ljava/lang/String;)I"
*/
JNIEXPORT jint JNICALL Java_org_servalproject_servaldna_ServalDCommand_rawCommand(JNIEnv *env, jobject UNUSED(this), jobject outv, jobjectArray args)
{
  struct cli_context context;
  bzero(&context, sizeof(context));

  // find jni results methods
  if (!IJniResults){
    IJniResults = (*env)->FindClass(env, "org/servalproject/servaldna/IJniResults");
    if (IJniResults==NULL)
      return Throw(env, "java/lang/IllegalStateException", "Unable to locate class org.servalproject.servaldna.IJniResults");
    startResultSet = (*env)->GetMethodID(env, IJniResults, "startResultSet", "(I)V");
    if (startResultSet==NULL)
      return Throw(env, "java/lang/IllegalStateException", "Unable to locate method startResultSet");
    setColumnName = (*env)->GetMethodID(env, IJniResults, "setColumnName", "(ILjava/lang/String;)V");
    if (setColumnName==NULL)
      return Throw(env, "java/lang/IllegalStateException", "Unable to locate method setColumnName");
    putString = (*env)->GetMethodID(env, IJniResults, "putString", "(Ljava/lang/String;)V");
    if (putString==NULL)
      return Throw(env, "java/lang/IllegalStateException", "Unable to locate method putString");
    putBlob = (*env)->GetMethodID(env, IJniResults, "putBlob", "([B)V");
    if (putBlob==NULL)
      return Throw(env, "java/lang/IllegalStateException", "Unable to locate method putBlob");
    putLong = (*env)->GetMethodID(env, IJniResults, "putLong", "(J)V");
    if (putLong==NULL)
      return Throw(env, "java/lang/IllegalStateException", "Unable to locate method putLong");
    putDouble = (*env)->GetMethodID(env, IJniResults, "putDouble", "(D)V");
    if (putDouble==NULL)
      return Throw(env, "java/lang/IllegalStateException", "Unable to locate method putDouble");
    totalRowCount = (*env)->GetMethodID(env, IJniResults, "totalRowCount", "(I)V");
    if (totalRowCount==NULL)
      return Throw(env, "java/lang/IllegalStateException", "Unable to locate method totalRowCount");
  }

  unsigned char status = 0; // to match what the shell gets: 0..255

  // Construct argv, argc from this method's arguments.
  jsize len = (*env)->GetArrayLength(env, args);
  const char **argv = alloca(sizeof(char*) * (len + 1));
  if (argv == NULL)
    return Throw(env, "java/lang/OutOfMemoryError", "alloca() returned NULL");
  jsize i;
  for (i = 0; i <= len; ++i)
    argv[i] = NULL;
  int argc = len;
  // From now on, in case of an exception we have to free some resources before
  // returning.
  for (i = 0; !context.jni_exception && i < len; ++i) {
    const jstring arg = (jstring)(*env)->GetObjectArrayElement(env, args, i);
    if ((*env)->ExceptionCheck(env))
      context.jni_exception = 1;
    else if (arg == NULL) {
      Throw(env, "java/lang/NullPointerException", "null element in argv");
      context.jni_exception = 1;
    }
    else {
      const char *str = (*env)->GetStringUTFChars(env, arg, NULL);
      if (str == NULL)
	context.jni_exception = 1;
      else
	argv[i] = str;
    }
  }
  if (!context.jni_exception) {
    // Set up the output buffer.
    context.jniResults = outv;
    context.outv_current = context.outv_buffer;
    // Execute the command.
    context.jni_env = env;
    status = parseCommandLine(&context, NULL, argc, argv);
  }

  // free any temporary output buffer
  if (context.outv_buffer)
    free(context.outv_buffer);

  // Release argv Java string buffers.
  for (i = 0; i < len; ++i) {
    if (argv[i]) {
      const jstring arg = (jstring)(*env)->GetObjectArrayElement(env, args, i);
      (*env)->ReleaseStringUTFChars(env, arg, argv[i]);
    }
  }

  // Deal with Java exceptions: NewStringUTF out of memory in outv_end_field().
  if (context.jni_exception || (context.outv_current != context.outv_buffer && outv_end_field(&context) == -1))
    return -1;

  return (jint) status;
}

#endif /* HAVE_JNI_H */

/* The argc and argv arguments must be passed verbatim from main(argc, argv), so argv[0] is path to
   executable.
*/
int parseCommandLine(struct cli_context *context, const char *argv0, int argc, const char *const *args)
{
  fd_clearstats();
  IN();
  
  struct cli_parsed parsed;
  int result = cli_parse(argc, args, __start_commands, __stop_commands, &parsed);
  switch (result) {
  case 0:
    // Do not run the command if the configuration does not load ok.
    if (((parsed.commands[parsed.cmdi].flags & CLIFLAG_PERMISSIVE_CONFIG) ? cf_reload_permissive() : cf_reload()) != -1)
      result = cli_invoke(&parsed, context);
    else {
      strbuf b = strbuf_alloca(160);
      strbuf_append_argv(b, argc, args);
      result = WHYF("configuration defective, not running command: %s", strbuf_str(b));
    }
    break;
  case 1:
  case 2:
    // Load configuration so that log messages can get out.
    cf_reload_permissive();
    NOWHENCE(HINTF("Run \"%s help\" for more information.", argv0 ? argv0 : "servald"));
    result =-1;
    break;
  default:
    // Load configuration so that log error messages can get out.
    cf_reload_permissive();
    break;
  }

  /* clean up after ourselves */
  rhizome_close_db();
  free_subscribers();
  assert(keyring==NULL);
  
  OUT();
  
  if (config.debug.timing)
    fd_showstats();
  return result;
}

/* Write a buffer of data to output.  If in a JNI call, then this appends the data to the
   current output field, including any embedded nul characters.  Returns a non-negative integer on
   success, EOF on error.
 */
int cli_write(struct cli_context *UNUSED(context), const unsigned char *buf, size_t len)
{
#ifdef HAVE_JNI_H
  if (context && context->jni_env) {
    size_t avail = context->outv_limit - context->outv_current;
    if (avail < len) {
      memcpy(context->outv_current, buf, avail);
      context->outv_current = context->outv_limit;
      if (outv_growbuf(context, len) == -1)
	return EOF;
      len -= avail;
      buf += avail;
    }
    memcpy(context->outv_current, buf, len);
    context->outv_current += len;
    return 0;
  }
#endif
  return fwrite(buf, len, 1, stdout);
}

/* Write a null-terminated string to output.  If in a JNI call, then this appends the string to the
   current output field.  The terminating null is not included.  Returns a non-negative integer on
   success, EOF on error.
 */
int cli_puts(struct cli_context *UNUSED(context), const char *str)
{
#ifdef HAVE_JNI_H
  if (context && context->jni_env)
    return cli_write(context, (const unsigned char *) str, strlen(str));
  else
#endif
    return fputs(str, stdout);
}

/* Write a formatted string to output.  If in a JNI call, then this appends the string to the
   current output field, excluding the terminating null.
 */
void cli_printf(struct cli_context *UNUSED(context), const char *fmt, ...)
{
  va_list ap;
#ifdef HAVE_JNI_H
  if (context && context->jni_env) {
    assert(context->outv_current <= context->outv_limit);
    size_t avail = context->outv_limit - context->outv_current;
    va_start(ap, fmt);
    int count = vsnprintf(context->outv_current, avail, fmt, ap);
    va_end(ap);
    if (count < 0) {
      WHYF("vsnprintf(%p,%zu,%s,...) failed", context->outv_current, avail, alloca_str_toprint(fmt));
      return;
    } else if ((size_t)count < avail) {
      context->outv_current += count;
      return;
    }
    if (outv_growbuf(context, count) == -1)
      return;
    avail = context->outv_limit - context->outv_current;
    va_start(ap, fmt);
    count = vsprintf(context->outv_current, fmt, ap);
    va_end(ap);
    if (count < 0) {
      WHYF("vsprintf(%p,%s,...) failed", context->outv_current, alloca_str_toprint(fmt));
      return;
    }
    assert((size_t)count < avail);
    context->outv_current += (size_t)count;
  } else
#endif
  {
    va_start(ap, fmt);
    if (vfprintf(stdout, fmt, ap) < 0)
      WHYF("vfprintf(stdout,%s,...) failed", alloca_str_toprint(fmt));
    va_end(ap);
  }
}

void cli_columns(struct cli_context *context, int columns, const char *names[])
{
#ifdef HAVE_JNI_H
  if (context && context->jni_env) {
    if (context->jni_exception)
      return;
      
    (*context->jni_env)->CallVoidMethod(context->jni_env, context->jniResults, startResultSet, columns);
    if ((*context->jni_env)->ExceptionCheck(context->jni_env)) {
      context->jni_exception = 1;
      WHY("Exception thrown from CallVoidMethod(startResultSet)");
      return;
    }
    int i;
    for (i=0;i<columns;i++){
      jstring str = (jstring)(*context->jni_env)->NewStringUTF(context->jni_env, names[i]);
      if (str == NULL) {
	context->jni_exception = 1;
	WHY("Exception thrown from NewStringUTF()");
	return;
      }
      (*context->jni_env)->CallVoidMethod(context->jni_env, context->jniResults, setColumnName, i, str);
      (*context->jni_env)->DeleteLocalRef(context->jni_env, str);
      if ((*context->jni_env)->ExceptionCheck(context->jni_env)) {
	context->jni_exception = 1;
	WHY("Exception thrown from CallVoidMethod(setColumnName)");
	return;
      }
    }
    return;
  }
#endif
  cli_printf(context, "%d", columns);
  cli_delim(context, "\n");
  int i;
  for (i=0;i<columns;i++){
    cli_puts(context, names[i]);
    if (i+1==columns)
      cli_delim(context, "\n");
    else
      cli_delim(context, ":");
  }
}

void cli_field_name(struct cli_context *context, const char *name, const char *delim)
{
#ifdef HAVE_JNI_H
  if (context && context->jni_env) {
    if (context->jni_exception)
      return;
    jstring str = (jstring)(*context->jni_env)->NewStringUTF(context->jni_env, name);
    if (str == NULL) {
      context->jni_exception = 1;
      WHY("Exception thrown from NewStringUTF()");
      return;
    }
    (*context->jni_env)->CallVoidMethod(context->jni_env, context->jniResults, setColumnName, -1, str);
    (*context->jni_env)->DeleteLocalRef(context->jni_env, str);
    if ((*context->jni_env)->ExceptionCheck(context->jni_env)) {
      context->jni_exception = 1;
      WHY("Exception thrown from CallVoidMethod(setColumnName)");
      return;
    }
    return;
  }
#endif
  cli_puts(context, name);
  cli_delim(context, delim);
}

void cli_put_long(struct cli_context *context, int64_t value, const char *delim){
#ifdef HAVE_JNI_H
  if (context && context->jni_env) {
    if (context->jni_exception)
      return;
    (*context->jni_env)->CallVoidMethod(context->jni_env, context->jniResults, putLong, value);
    if ((*context->jni_env)->ExceptionCheck(context->jni_env)) {
      context->jni_exception = 1;
      WHY("Exception thrown from CallVoidMethod(putLong)");
    }
    return;
  }
#endif
  cli_printf(context, "%" PRId64, value);
  cli_delim(context, delim);
}

void cli_put_string(struct cli_context *context, const char *value, const char *delim){
#ifdef HAVE_JNI_H
  if (context && context->jni_env) {
    if (context->jni_exception)
      return;
    jstring str = NULL;
    if (value){
      str = (jstring)(*context->jni_env)->NewStringUTF(context->jni_env, value);
      if (str == NULL) {
	context->jni_exception = 1;
	WHY("Exception thrown from NewStringUTF()");
	return;
      }
    }
    (*context->jni_env)->CallVoidMethod(context->jni_env, context->jniResults, putString, str);
    (*context->jni_env)->DeleteLocalRef(context->jni_env, str);
    if ((*context->jni_env)->ExceptionCheck(context->jni_env)) {
      context->jni_exception = 1;
      WHY("Exception thrown from CallVoidMethod(putLong)");
    }
    return;
  }
#endif
  if (value)
    cli_puts(context, value);
  cli_delim(context, delim);
}

void cli_put_hexvalue(struct cli_context *context, const unsigned char *value, int length, const char *delim){
#ifdef HAVE_JNI_H
  if (context && context->jni_env) {
    put_blob(context, (jbyte*)value, length);
    return;
  }
#endif
  if (value)
    cli_puts(context, alloca_tohex(value, length));
  cli_delim(context, delim);
}

void cli_row_count(struct cli_context *UNUSED(context), int UNUSED(rows)){
#ifdef HAVE_JNI_H
  if (context && context->jni_env) {
    if (context->jni_exception)
      return;
    (*context->jni_env)->CallVoidMethod(context->jni_env, context->jniResults, totalRowCount, rows);
    if ((*context->jni_env)->ExceptionCheck(context->jni_env)) {
      context->jni_exception = 1;
      WHY("Exception thrown from CallVoidMethod()");
    }
    return;
  }
#endif
}

/* Delimit the current output field.  This closes the current field, so that the next cli_ output
   function will start appending to a new field.  Returns 0 on success, -1 on error.  If not in a
   JNI call, then this simply writes a newline to standard output (or the value of the
   SERVALD_OUTPUT_DELIMITER env var if set).
 */
int cli_delim(struct cli_context *UNUSED(context), const char *opt)
{
#ifdef HAVE_JNI_H
  if (context && context->jni_env)
    return outv_end_field(context);
#endif
  const char *delim = getenv("SERVALD_OUTPUT_DELIMITER");
  if (delim == NULL)
    delim = opt ? opt : "\n";
  fputs(delim, stdout);
  return 0;
}

/* Flush the output fields if they are being written to standard output.
 */
void cli_flush(struct cli_context *UNUSED(context))
{
#ifdef HAVE_JNI_H
  if (context && context->jni_env)
    return;
#endif
  fflush(stdout);
}

DEFINE_CMD(app_echo,CLIFLAG_PERMISSIVE_CONFIG,
  "Output the supplied string.",
  "echo","[-e]","[--]","...");
static int app_echo(const struct cli_parsed *parsed, struct cli_context *context)
{
  if (config.debug.verbose)
    DEBUG_cli_parsed(parsed);
  int escapes = !cli_arg(parsed, "-e", NULL, NULL, NULL);
  unsigned i;
  for (i = parsed->varargi; i < parsed->argc; ++i) {
    const char *arg = parsed->args[i];
    if (config.debug.verbose)
      DEBUGF("echo:argv[%d]=\"%s\"", i, arg);
    if (escapes) {
      unsigned char buf[strlen(arg)];
      size_t len = strn_fromprint(buf, sizeof buf, arg, 0, '\0', NULL);
      cli_write(context, buf, len);
    } else
      cli_puts(context, arg);
    cli_delim(context, NULL);
  }
  return 0;
}

DEFINE_CMD(app_log,CLIFLAG_PERMISSIVE_CONFIG,
  "Log the supplied message at given level.",
  "log","error|warn|hint|info|debug","<message>");
static int app_log(const struct cli_parsed *parsed, struct cli_context *UNUSED(context))
{
  if (config.debug.verbose)
    DEBUG_cli_parsed(parsed);
  assert(parsed->argc == 3);
  const char *lvl = parsed->args[1];
  const char *msg = parsed->args[2];
  int level = string_to_log_level(lvl);
  if (level == LOG_LEVEL_INVALID)
    return WHYF("invalid log level: %s", lvl);
  logMessage(level, __NOWHERE__, "%s", msg);
  return 0;
}

static void lookup_send_request(int mdp_sockfd, const sid_t *srcsid, int srcport, const sid_t *dstsid, const char *did)
{
  overlay_mdp_frame mdp;
  bzero(&mdp,sizeof(mdp));
  
  /* set source address to the local address and port */
  mdp.out.src.port = srcport;
  mdp.out.src.sid = *srcsid;
  
  /* Send to destination address and DNA lookup port */
  if (dstsid) {
    /* Send an encrypted unicast packet */
    mdp.packetTypeAndFlags=MDP_TX;
    mdp.out.dst.sid = *dstsid;
  }else{
    /* Send a broadcast packet, flooding across the local mesh network */
    mdp.packetTypeAndFlags=MDP_TX|MDP_NOCRYPT;
    mdp.out.dst.sid = SID_BROADCAST;
  }  
  mdp.out.dst.port=MDP_PORT_DNALOOKUP;
  
  /* put DID into packet */
  bcopy(did,&mdp.out.payload[0],strlen(did)+1);
  mdp.out.payload_length=strlen(did)+1;
  
  overlay_mdp_send(mdp_sockfd, &mdp, 0, 0);
  
  /* Also send an encrypted unicast request to a configured directory service */
  if (!dstsid){
    if (!is_sid_t_any(config.directory.service)) {
      mdp.out.dst.sid = config.directory.service;
      mdp.packetTypeAndFlags=MDP_TX;
      overlay_mdp_send(mdp_sockfd, &mdp,0,0);
    }
  }
}

DEFINE_CMD(app_dna_lookup, 0,
  "Lookup the subscribers (SID) with the supplied telephone number (DID).",
  "dna","lookup","<did>","[<timeout>]");
static int app_dna_lookup(const struct cli_parsed *parsed, struct cli_context *context)
{
  int mdp_sockfd;
  if (config.debug.verbose)
    DEBUG_cli_parsed(parsed);

  /* Create the instance directory if it does not yet exist */
  if (create_serval_instance_dir() == -1)
    return -1;

  int uri_count=0;
#define MAXREPLIES 256
#define MAXURILEN 256
  char uris[MAXREPLIES][MAXURILEN];

  const char *did, *delay;
  if (cli_arg(parsed, "did", &did, cli_lookup_did, "*") == -1)
    return -1;
  if (cli_arg(parsed, "timeout", &delay, NULL, "3000") == -1)
    return -1;
  
  int idelay=atoi(delay);
  int one_reply=0;
  
  // Ugly hack, if timeout is negative, stop after first reply
  if (idelay<0){
    one_reply=1;
    idelay=-idelay;
  }

  if ((mdp_sockfd = overlay_mdp_client_socket()) < 0)
    return WHY("Cannot create MDP socket");

  /* Bind to MDP socket and await confirmation */
  sid_t srcsid;
  mdp_port_t port=32768+(random()&32767);
  if (overlay_mdp_getmyaddr(mdp_sockfd, 0, &srcsid)) {
    overlay_mdp_client_close(mdp_sockfd);
    return WHY("Could not get local address");
  }
  if (overlay_mdp_bind(mdp_sockfd, &srcsid, port)) {
    overlay_mdp_client_close(mdp_sockfd);
    return WHY("Could not bind to MDP socket");
  }

  /* use MDP to send the lookup request to MDP_PORT_DNALOOKUP, and wait for
     replies. */

  /* Now repeatedly send resolution request and collect results until we reach
     timeout. */
  time_ms_t timeout = gettime_ms() + idelay;
  time_ms_t last_tx = 0;
  time_ms_t now;
  int interval=125;
  
  const char *names[]={
    "uri",
    "did",
    "name"
  };
  cli_columns(context, 3, names);
  size_t rowcount = 0;
  
  while (timeout > (now = gettime_ms())){
    if ((last_tx+interval)<now){
      lookup_send_request(mdp_sockfd, &srcsid, port, NULL, did);
      last_tx=now;
      interval+=interval>>1;
    }
    time_ms_t short_timeout=125;
    while(short_timeout>0) {
      if (overlay_mdp_client_poll(mdp_sockfd, short_timeout)){
	overlay_mdp_frame rx;
	int ttl;
	if (overlay_mdp_recv(mdp_sockfd, &rx, port, &ttl)==0){
	  if (rx.packetTypeAndFlags==MDP_ERROR){
	    WHYF("       Error message: %s", rx.error.message);
	  } else if ((rx.packetTypeAndFlags&MDP_TYPE_MASK)==MDP_TX) {
	    /* Extract DID, Name, URI from response. */
	    if (strlen((char *)rx.out.payload)<512) {
	      char sidhex[SID_STRLEN + 1];
	      char did[DID_MAXSIZE + 1];
	      char name[64];
	      char uri[512];
	      if ( !parseDnaReply((char *)rx.out.payload, rx.out.payload_length, sidhex, did, name, uri, NULL)
		|| !str_is_subscriber_id(sidhex)
		|| !str_is_did(did)
		|| !str_is_uri(uri)
	      ) {
		WHYF("Received malformed DNA reply: %s", alloca_toprint(160, (const char *)rx.out.payload, rx.out.payload_length));
	      } else {
		/* Have we seen this response before? */
		int i;
		for(i=0;i<uri_count;i++)
		  if (!strcmp(uri,uris[i])) break;
		if (i==uri_count) {
		  /* Not previously seen, so report it */
		  cli_put_string(context, uri, ":");
		  cli_put_string(context, did, ":");
		  cli_put_string(context, name, "\n");
		  rowcount++;
		  
		  if (one_reply){
		    timeout=now;
		    short_timeout=0;
		  }
		  
		  /* Remember that we have seen it */
		  if (uri_count<MAXREPLIES&&strlen(uri)<MAXURILEN) {
		    strcpy(uris[uri_count++],uri);
		  }
		}
	      }
	    }
	  }
	  else WHYF("packettype=0x%x",rx.packetTypeAndFlags);
	}
      }
      short_timeout=125-(gettime_ms()-now);
    }
  }

  overlay_mdp_client_close(mdp_sockfd);
  cli_row_count(context, rowcount);
  return 0;
}

DEFINE_CMD(app_reverse_lookup, 0,
  "Lookup the phone number (DID) and name of a given subscriber (SID)",
  "reverse", "lookup", "<sid>", "[<timeout>]");
static int app_reverse_lookup(const struct cli_parsed *parsed, struct cli_context *context)
{
  int mdp_sockfd;
  if (config.debug.verbose)
    DEBUG_cli_parsed(parsed);
  const char *sidhex, *delay;
  if (cli_arg(parsed, "sid", &sidhex, str_is_subscriber_id, "") == -1)
    return -1;
  if (cli_arg(parsed, "timeout", &delay, NULL, "3000") == -1)
    return -1;

  mdp_port_t port=32768+(random()&0xffff);

  sid_t srcsid;
  sid_t dstsid;

  if (str_to_sid_t(&dstsid, sidhex) == -1)
    return WHY("str_to_sid_t() failed");

  if ((mdp_sockfd = overlay_mdp_client_socket()) < 0)
    return WHY("Cannot create MDP socket");
    
  if (overlay_mdp_getmyaddr(mdp_sockfd, 0, &srcsid)){
    overlay_mdp_client_close(mdp_sockfd);
    return WHY("Unable to get my address");
  }
  if (overlay_mdp_bind(mdp_sockfd, &srcsid, port)){
    overlay_mdp_client_close(mdp_sockfd);
    return WHY("Unable to bind port");
  }
  
  time_ms_t now = gettime_ms();
  time_ms_t timeout = now + atoi(delay);
  time_ms_t next_send = now;
  overlay_mdp_frame mdp_reply;
  
  while (now < timeout){
    now=gettime_ms();
    
    if (now >= next_send){
      /* Send a unicast packet to this node, asking for any did */
      lookup_send_request(mdp_sockfd, &srcsid, port, &dstsid, "");
      next_send+=125;
      continue;
    }
    
    time_ms_t poll_timeout = (next_send>timeout?timeout:next_send) - now;
    if (overlay_mdp_client_poll(mdp_sockfd, poll_timeout)<=0)
      continue;
    
    int ttl=-1;
    if (overlay_mdp_recv(mdp_sockfd, &mdp_reply, port, &ttl))
      continue;
    
    if ((mdp_reply.packetTypeAndFlags&MDP_TYPE_MASK)==MDP_ERROR){
      // TODO log error?
      continue;
    }
    
    if (mdp_reply.packetTypeAndFlags!=MDP_TX) {
      WHYF("MDP returned an unexpected message (type=0x%x)",
	   mdp_reply.packetTypeAndFlags);
      
      if (mdp_reply.packetTypeAndFlags==MDP_ERROR) 
	WHYF("MDP message is return/error: %d:%s",
	     mdp_reply.error.error,mdp_reply.error.message);
      continue;
    }
    
    // we might receive a late response from an ealier request on the same socket, ignore it
    if (cmp_sid_t(&mdp_reply.out.src.sid, &dstsid) != 0) {
      WHYF("Unexpected result from SID %s", alloca_tohex_sid_t(mdp_reply.out.src.sid));
      continue;
    }
      
    {
      char sidhex[SID_STRLEN + 1];
      char did[DID_MAXSIZE + 1];
      char name[64];
      char uri[512];
      if ( !parseDnaReply((char *)mdp_reply.out.payload, mdp_reply.out.payload_length, sidhex, did, name, uri, NULL)
	  || !str_is_subscriber_id(sidhex)
	  || !str_is_did(did)
	  || !str_is_uri(uri)
	  ) {
	WHYF("Received malformed DNA reply: %s", 
	     alloca_toprint(160, (const char *)mdp_reply.out.payload, mdp_reply.out.payload_length));
	continue;
      }
      
      /* Got a good DNA reply, copy it into place and stop polling */
      cli_field_name(context, "sid", ":");
      cli_put_string(context, sidhex, "\n");
      cli_field_name(context, "did", ":");
      cli_put_string(context, did, "\n");
      cli_field_name(context, "name", ":");
      cli_put_string(context, name, "\n");
      overlay_mdp_client_close(mdp_sockfd);
      return 0;
    }
  }
  overlay_mdp_client_close(mdp_sockfd);
  return 1;
}

