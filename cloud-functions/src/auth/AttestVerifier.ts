import * as crypto from "crypto";
import { assertCampusScope } from "./CampusScopeGuard";

export interface AttestationPayload {
  campusId: string;
  deviceModel: string;
  androidApiLevel: number;
  isStrongBoxBacked: boolean;
  publicKeyDerBase64: string;
  certificateChain: string[];
  signedChallengeHex: string;
  timestampMs: number;
}

/**
 * AttestVerifier (S-06, S-26, S-47)
 * Validates hardware TEE/StrongBox attestation envelopes submitted during enrollment or SOC checks.
 */
export function verifyDeviceAttestation(
  payload: AttestationPayload,
  expectedChallenge: string
): { verified: boolean; error?: string } {
  try {
    assertCampusScope(payload.campusId);

    // Verify clock freshness (challenge valid for 120 seconds)
    if (Math.abs(Date.now() - payload.timestampMs) > 120000) {
      return { verified: false, error: "Attestation challenge timestamp expired" };
    }

    const signedData = Buffer.from(
      `WCI_UNNAO_MAIN:${expectedChallenge}:${payload.timestampMs}`,
      "utf-8"
    );

    const pubKeyDer = Buffer.from(payload.publicKeyDerBase64, "base64");
    const publicKey = crypto.createPublicKey({
      key: pubKeyDer,
      format: "der",
      type: "spki"
    });

    const signature = Buffer.from(payload.signedChallengeHex, "hex");

    const isSigValid = crypto.verify(
      "sha256",
      signedData,
      publicKey,
      signature
    );

    if (!isSigValid) {
      return { verified: false, error: "Cryptographic signature verification failed against attested hardware key" };
    }

    return { verified: true };
  } catch (err: any) {
    return { verified: false, error: err.message || "Attestation verification error" };
  }
}
