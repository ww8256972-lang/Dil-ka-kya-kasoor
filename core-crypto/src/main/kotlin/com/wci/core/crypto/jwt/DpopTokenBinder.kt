package com.wci.core.crypto.jwt

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * DpopTokenBinder (S-10, S-16)
 * Implements RFC 9449 Demonstrating Proof-of-Possession at the Application Layer.
 * Produces cryptographic DPoP proof JWTs binding access tokens and HTTP targets to an EC private key.
 */
class DpopTokenBinder(customKey: ECKey? = null) {

    private val ecKey: ECKey = customKey ?: ECKeyGenerator(Curve.P_256)
        .keyID(UUID.randomUUID().toString())
        .generate()

    private val signer: ECDSASigner = ECDSASigner(ecKey)
    private val publicJwk: ECKey = ecKey.toPublicJWK()

    /**
     * Computes the base64url-encoded SHA-256 hash of an access token (ath).
     */
    fun computeAccessTokenHash(accessToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(accessToken.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    /**
     * Generates a signed RFC 9449 DPoP Proof JWT.
     * @param httpMethod HTTP method in uppercase (e.g., "POST", "GET")
     * @param httpUri Absolute request URI target
     * @param accessToken Optional access token to compute 'ath' claim
     * @param nonce Optional server-supplied DPoP-Nonce
     * @return Compact serialized DPoP proof string
     */
    fun generateDpopProof(
        httpMethod: String,
        httpUri: String,
        accessToken: String? = null,
        nonce: String? = null
    ): String {
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("dpop+jwt"))
            .jwk(publicJwk)
            .build()

        val claimsBuilder = JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .claim("htm", httpMethod.uppercase())
            .claim("htu", httpUri)
            .issueTime(Date())

        if (!accessToken.isNullOrEmpty()) {
            claimsBuilder.claim("ath", computeAccessTokenHash(accessToken))
        }

        if (!nonce.isNullOrEmpty()) {
            claimsBuilder.claim("nonce", nonce)
        }

        val claims = claimsBuilder.build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(signer)
        return signedJwt.serialize()
    }

    /**
     * Returns the JWK thumbprint (SHA-256) for DPoP binding ('cnf.jkt').
     */
    fun getJwkThumbprint(): String {
        return publicJwk.computeThumbprint().toString()
    }
}
