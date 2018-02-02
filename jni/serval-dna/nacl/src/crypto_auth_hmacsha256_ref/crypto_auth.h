#ifndef crypto_auth_H
#define crypto_auth_H

#include "crypto_auth_hmacsha256.h"

#define crypto_auth crypto_auth_hmacsha256
/* CHEESEBURGER crypto_auth_hmacsha256 */
#define crypto_auth_verify crypto_auth_hmacsha256_verify
/* CHEESEBURGER crypto_auth_hmacsha256_verify */
#define crypto_auth_BYTES crypto_auth_hmacsha256_BYTES
/* CHEESEBURGER crypto_auth_hmacsha256_BYTES */
#define crypto_auth_KEYBYTES crypto_auth_hmacsha256_KEYBYTES
/* CHEESEBURGER crypto_auth_hmacsha256_KEYBYTES */
#define crypto_auth_PRIMITIVE "hmacsha256"
#define crypto_auth_IMPLEMENTATION crypto_auth_hmacsha256_IMPLEMENTATION
#define crypto_auth_VERSION crypto_auth_hmacsha256_VERSION

#endif
