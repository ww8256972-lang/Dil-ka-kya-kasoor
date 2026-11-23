import * as crypto from "crypto";
import { assertCampusScope } from "./CampusScopeGuard";

export interface BiometricChallengeProofPayload {
  campusId: string;
  uid: string;
  action: string;
  serverNonce: string;
  timestampMs: number;
  signatureBase64: string;
  publicKeyDerBase64: string;
}

/**
 * BioChallengeVerify (S-36, S-50)
 * Validates single-use biometric challenge responses from BiometricPrompt.CryptoObject.
 */
export function verifyBiometricChallengeProof(
  proof: BiometricChallengeProofPayload,
  activeServerNonce: string
): { verified: boolean; error?: string } {
  try {
    assertCampusScope(proof.campusId);

    if (proof.serverNonce !== activeServerNonce) {
      return { verified: false, error: "Server nonce mismatch or replayed challenge" };
    }

    if (Math.abs(Date.now() - proof.timestampMs) > 60000) {
      return { verified: false, error: "Biometric proof expired (> 60s)" };
    }

    const payload = Buffer.from(
      `WCI_UNNAO_MAIN:${proof.uid}:${proof.action}:${proof.serverNonce}:${proof.timestampMs}`,
      "utf-8"
    );

    const pubKeyDer = Buffer.from(proof.publicKeyDerBase64, "base64");
    const publicKey = crypto.createPublicKey({
      key: pubKeyDer,
      format: "der",
      type: "spki"
    });

    const signature = Buffer.from(proof.signatureBase64, "base64");

    const isValid = crypto.verify("sha256", payload, publicKey, signature);
    if (!isValid) {
      return { verified: false, error: "Biometric signature invalid" };
    }

    return { verified: true };
  } catch (err: any) {
    return { verified: false, error: err.message || "Biometric challenge verification failure" };
  }
}
