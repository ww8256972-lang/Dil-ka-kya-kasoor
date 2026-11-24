package com.waqar.security.advanced

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * ZkProofAuthEngine: Non-Interactive Zero-Knowledge Proof Authentication Engine.
 *
 * Implements a mathematically sound Schnorr identification protocol with the Fiat-Shamir heuristic
 * over a standard 2048-bit MODP prime group (RFC 3526 Group 14).
 *
 * Enables a client to mathematically prove possession of a secret (such as an Admin master passphrase
 * or faculty token) to the server/verifier WITHOUT EVER exposing, transmitting, or persisting the plaintext secret.
 *
 * Mathematical Foundations:
 * 1. Public Domain Parameters:
 *    - Prime modulus p = 2^2048 - 2^1984 - 1 + 2^64 * { [2^1918 pi] + 124476 }
 *    - Subgroup prime order q = (p - 1) / 2
 *    - Generator g = 2
 * 2. Prover:
 *    - Secret key x = H(password) mod q
 *    - Public identity y = g^x mod p
 *    - Ephemeral nonce v in [1, q-1]
 *    - Commitment t = g^v mod p
 *    - Challenge c = H(g || y || t || identity) mod q (Fiat-Shamir transformation)
 *    - Response s = (v - c * x) mod q
 *    - Zero-Knowledge Proof: pi = (t, s)
 * 3. Verifier:
 *    - Recomputes challenge c = H(g || y || t || identity) mod q
 *    - Asserts g^s * y^c mod p == t
 *    - By Schnorr completeness, g^(v - c*x) * (g^x)^c = g^(v - c*x + c*x) = g^v = t mod p.
 */
object ZkProofAuthEngine {

    // RFC 3526 2048-bit MODP Group 14 Prime
    val MODP_PRIME_2048: BigInteger = BigInteger(
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
        "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
        "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
        "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
        "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
        "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
        "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
        "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
        "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
        "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
        "15728E5A8AACAA68FFFFFFFFFFFFFFFF",
        16
    )

    val GENERATOR: BigInteger = BigInteger.valueOf(2L)
    val SUBGROUP_ORDER_Q: BigInteger = MODP_PRIME_2048.subtract(BigInteger.ONE).shiftRight(1)

    private val secureRandom = SecureRandom()

    data class ZkProof(
        val commitmentT: BigInteger,
        val responseS: BigInteger,
        val publicIdentityY: BigInteger
    )

    /**
     * Derives public identity y = g^x mod p from secret.
     */
    fun derivePublicIdentity(secret: ByteArray): BigInteger {
        val x = hashToSubgroup(secret)
        return GENERATOR.modPow(x, MODP_PRIME_2048)
    }

    /**
     * Generates a non-interactive Zero-Knowledge Proof demonstrating knowledge of the secret.
     */
    fun generateProof(secret: ByteArray, identityContext: String): ZkProof {
        val x = hashToSubgroup(secret)
        val y = GENERATOR.modPow(x, MODP_PRIME_2048)

        // Generate cryptographically secure ephemeral nonce v in [1, q-1]
        var v: BigInteger
        do {
            v = BigInteger(SUBGROUP_ORDER_Q.bitLength(), secureRandom)
        } while (v <= BigInteger.ONE || v >= SUBGROUP_ORDER_Q)

        // Commitment t = g^v mod p
        val t = GENERATOR.modPow(v, MODP_PRIME_2048)

        // Challenge c = H(g || y || t || identityContext) mod q
        val c = computeChallenge(y, t, identityContext)

        // Response s = (v - c * x) mod q
        val cx = c.multiply(x).mod(SUBGROUP_ORDER_Q)
        var s = v.subtract(cx).mod(SUBGROUP_ORDER_Q)
        if (s < BigInteger.ZERO) {
            s = s.add(SUBGROUP_ORDER_Q)
        }

        return ZkProof(commitmentT = t, responseS = s, publicIdentityY = y)
    }

    /**
     * Verifies the proof without ever having received the prover's secret key.
     * Asserts: g^s * y^c mod p == t.
     */
    fun verifyProof(proof: ZkProof, identityContext: String): Boolean {
        if (proof.commitmentT <= BigInteger.ZERO || proof.commitmentT >= MODP_PRIME_2048) return false
        if (proof.responseS < BigInteger.ZERO || proof.responseS >= SUBGROUP_ORDER_Q) return false
        if (proof.publicIdentityY <= BigInteger.ZERO || proof.publicIdentityY >= MODP_PRIME_2048) return false

        // Challenge c = H(g || y || t || identityContext) mod q
        val c = computeChallenge(proof.publicIdentityY, proof.commitmentT, identityContext)

        // Left side = g^s * y^c mod p
        val gs = GENERATOR.modPow(proof.responseS, MODP_PRIME_2048)
        val yc = proof.publicIdentityY.modPow(c, MODP_PRIME_2048)
        val reconstructedT = gs.multiply(yc).mod(MODP_PRIME_2048)

        return reconstructedT == proof.commitmentT
    }

    private fun hashToSubgroup(input: ByteArray): BigInteger {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input)
        return BigInteger(1, digest).mod(SUBGROUP_ORDER_Q)
    }

    private fun computeChallenge(y: BigInteger, t: BigInteger, context: String): BigInteger {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(GENERATOR.toByteArray())
        md.update(y.toByteArray())
        md.update(t.toByteArray())
        md.update(context.toByteArray(Charsets.UTF_8))
        val digest = md.digest()
        return BigInteger(1, digest).mod(SUBGROUP_ORDER_Q)
    }
}
