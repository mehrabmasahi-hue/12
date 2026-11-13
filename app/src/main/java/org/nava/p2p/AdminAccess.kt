package org.nava.p2p

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Fixed admin credential verifier.
 * The plaintext password is intentionally not stored in the APK.
 * This is a local app gate, not a server-grade authorization boundary:
 * a determined APK modifier can patch client-side checks.
 */
object AdminAccess {
    private const val ITERATIONS = 310_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_B64 = "wi8QhYR7/yUx7ygOHLHQjg=="
    private const val EXPECTED_B64 = "mCIlFfarcDKhURdSgpXpGn3g6IRGjYU4B0/pNg+jSB0="

    fun verify(candidate: CharArray): Boolean {
        val salt = Base64.decode(SALT_B64, Base64.NO_WRAP)
        val expected = Base64.decode(EXPECTED_B64, Base64.NO_WRAP)
        val spec = PBEKeySpec(candidate, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            MessageDigest.isEqual(expected, actual)
        } finally {
            spec.clearPassword()
            candidate.fill('\u0000')
        }
    }
}
