import * as crypto from "crypto";

export interface DpopVerificationResult {
  valid: boolean;
  jwkThumbprint?: string;
  error?: string;
}

/**
 * DpopVerifier (S-10, S-16)
 * Server-side verifier for RFC 9449 DPoP proofs submitted by native clients.
 */
export function verifyDpopProof(
  dpopHeader: string,
  expectedMethod: string,
  expectedUri: string,
  accessToken?: string
): DpopVerificationResult {
  try {
    const parts = dpopHeader.split(".");
    if (parts.length !== 3) {
      return { valid: false, error: "Invalid DPoP JWT format (must have 3 parts)" };
    }

    const header = JSON.parse(Buffer.from(parts[0], "base64url").toString("utf-8"));
    const claims = JSON.parse(Buffer.from(parts[1], "base64url").toString("utf-8"));

    if (header.typ !== "dpop+jwt" || header.alg !== "ES256" || !header.jwk) {
      return { valid: false, error: "Invalid DPoP header parameters" };
    }

    if (claims.htm?.toUpperCase() !== expectedMethod.toUpperCase()) {
      return { valid: false, error: `Method mismatch: expected ${expectedMethod}, got ${claims.htm}` };
    }

    if (claims.htu !== expectedUri) {
      return { valid: false, error: `URI mismatch: expected ${expectedUri}, got ${claims.htu}` };
    }

    // Verify token freshness (must be within +/- 5 minutes)
    const nowSec = Math.floor(Date.now() / 1000);
    if (!claims.iat || Math.abs(nowSec - claims.iat) > 300) {
      return { valid: false, error: "DPoP proof expired or clock skewed" };
    }

    // Verify access token binding (ath claim)
    if (accessToken) {
      const expectedAth = crypto
        .createHash("sha256")
        .update(accessToken, "ascii")
        .digest("base64url");
      if (claims.ath !== expectedAth) {
        return { valid: false, error: "Access token hash (ath) mismatch" };
      }
    }

    // Compute JWK thumbprint
    const jwkCanonical = JSON.stringify({
      crv: header.jwk.crv,
      kty: header.jwk.kty,
      x: header.jwk.x,
      y: header.jwk.y
    });
    const jkt = crypto.createHash("sha256").update(jwkCanonical).digest("base64url");

    return { valid: true, jwkThumbprint: jkt };
  } catch (err: any) {
    return { valid: false, error: err.message || "Failed to verify DPoP proof" };
  }
}
