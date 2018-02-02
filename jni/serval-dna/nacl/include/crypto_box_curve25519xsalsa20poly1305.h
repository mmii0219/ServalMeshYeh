#ifndef crypto_box_curve25519xsalsa20poly1305_H
#define crypto_box_curve25519xsalsa20poly1305_H

#define crypto_box_curve25519xsalsa20poly1305_ref_PUBLICKEYBYTES 32
#define crypto_box_curve25519xsalsa20poly1305_ref_SECRETKEYBYTES 32
#define crypto_box_curve25519xsalsa20poly1305_ref_BEFORENMBYTES 32
#define crypto_box_curve25519xsalsa20poly1305_ref_NONCEBYTES 24
#define crypto_box_curve25519xsalsa20poly1305_ref_ZEROBYTES 32
#define crypto_box_curve25519xsalsa20poly1305_ref_BOXZEROBYTES 16
#ifdef __cplusplus
#include <string>
extern std::string crypto_box_curve25519xsalsa20poly1305_ref(const std::string &,const std::string &,const std::string &,const std::string &);
extern std::string crypto_box_curve25519xsalsa20poly1305_ref_open(const std::string &,const std::string &,const std::string &,const std::string &);
extern std::string crypto_box_curve25519xsalsa20poly1305_ref_keypair(std::string *);
extern "C" {
#endif
extern int crypto_box_curve25519xsalsa20poly1305_ref(unsigned char *,const unsigned char *,unsigned long long,const unsigned char *,const unsigned char *,const unsigned char *);
extern int crypto_box_curve25519xsalsa20poly1305_ref_open(unsigned char *,const unsigned char *,unsigned long long,const unsigned char *,const unsigned char *,const unsigned char *);
extern int crypto_box_curve25519xsalsa20poly1305_ref_keypair(unsigned char *,unsigned char *);
extern int crypto_box_curve25519xsalsa20poly1305_ref_beforenm(unsigned char *,const unsigned char *,const unsigned char *);
extern int crypto_box_curve25519xsalsa20poly1305_ref_afternm(unsigned char *,const unsigned char *,unsigned long long,const unsigned char *,const unsigned char *);
extern int crypto_box_curve25519xsalsa20poly1305_ref_open_afternm(unsigned char *,const unsigned char *,unsigned long long,const unsigned char *,const unsigned char *);
#ifdef __cplusplus
}
#endif

#define crypto_box_curve25519xsalsa20poly1305 crypto_box_curve25519xsalsa20poly1305_ref
/* POTATO crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305 */
#define crypto_box_curve25519xsalsa20poly1305_open crypto_box_curve25519xsalsa20poly1305_ref_open
/* POTATO crypto_box_curve25519xsalsa20poly1305_ref_open crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305 */
#define crypto_box_curve25519xsalsa20poly1305_keypair crypto_box_curve25519xsalsa20poly1305_ref_keypair
/* POTATO crypto_box_curve25519xsalsa20poly1305_ref_keypair crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305 */
#define crypto_box_curve25519xsalsa20poly1305_beforenm crypto_box_curve25519xsalsa20poly1305_ref_beforenm
/* POTATO crypto_box_curve25519xsalsa20poly1305_ref_beforenm crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305 */
#define crypto_box_curve25519xsalsa20poly1305_afternm crypto_box_curve25519xsalsa20poly1305_ref_afternm
/* POTATO crypto_box_curve25519xsalsa20poly1305_ref_afternm crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305 */
#define crypto_box_curve25519xsalsa20poly1305_open_afternm crypto_box_curve25519xsalsa20poly1305_ref_open_afternm
/* POTATO crypto_box_curve25519xsalsa20poly1305_ref_open_afternm crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305 */
#define crypto_box_curve25519xsalsa20poly1305_PUBLICKEYBYTES crypto_box_curve25519xsalsa20poly1305_ref_PUBLICKEYBYTES
/* POTATO crypto_box_curve25519xsalsa20poly1305_ref_PUBLICKEYBYTES crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305 */
#define crypto_box_curve25519xsalsa20poly1305_SECRETKEYBYTES crypto_box_curve25519xsalsa20poly1305_ref_SECRETKEYBYTES
/* POTATO crypto_box_curve25519xsalsa20poly1305_ref_SECRETKEYBYTES crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305 */
#define crypto_box_curve25519xsalsa20poly1305_BEFORENMBYTES crypto_box_curve25519xsalsa20poly1305_ref_BEFORENMBYTES
/* POTATO crypto_box_curve25519xsalsa20poly1305_ref_BEFORENMBYTES crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305 */
#define crypto_box_curve25519xsalsa20poly1305_NONCEBYTES crypto_box_curve25519xsalsa20poly1305_ref_NONCEBYTES
/* POTATO crypto_box_curve25519xsalsa20poly1305_ref_NONCEBYTES crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305 */
#define crypto_box_curve25519xsalsa20poly1305_ZEROBYTES crypto_box_curve25519xsalsa20poly1305_ref_ZEROBYTES
/* POTATO crypto_box_curve25519xsalsa20poly1305_ref_ZEROBYTES crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305 */
#define crypto_box_curve25519xsalsa20poly1305_BOXZEROBYTES crypto_box_curve25519xsalsa20poly1305_ref_BOXZEROBYTES
/* POTATO crypto_box_curve25519xsalsa20poly1305_ref_BOXZEROBYTES crypto_box_curve25519xsalsa20poly1305_ref crypto_box_curve25519xsalsa20poly1305 */
#define crypto_box_curve25519xsalsa20poly1305_IMPLEMENTATION "crypto_box/curve25519xsalsa20poly1305/ref"
#ifndef crypto_box_curve25519xsalsa20poly1305_ref_VERSION
#define crypto_box_curve25519xsalsa20poly1305_ref_VERSION "-"
#endif
#define crypto_box_curve25519xsalsa20poly1305_VERSION crypto_box_curve25519xsalsa20poly1305_ref_VERSION

#endif
