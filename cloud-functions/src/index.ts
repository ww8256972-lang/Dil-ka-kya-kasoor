import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { assertCampusScope } from "./auth/CampusScopeGuard";
import { OfflineFeeReceiptEngine } from "./ledger/OfflineFeeReceiptEngine";
import { verifyBiometricChallengeProof } from "./auth/BioChallengeVerify";
import { T30AutoGrader } from "./exams/T30AutoGrader";
import { RemoteLockdown } from "./security/RemoteLockdown";
import { IntrusionSentinel } from "./security/IntrusionSentinel";
import { MerkleEmitter } from "./audit/MerkleEmitter";

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const feeEngine = new OfflineFeeReceiptEngine(db);
const autoGrader = new T30AutoGrader(db);
const lockdown = new RemoteLockdown(db);
const sentinel = new IntrusionSentinel(db);
const auditEmitter = new MerkleEmitter(db);

/**
 * 1. issueOfflineFeeReceipt (Section 3.4 & Module 1.4)
 * Strict offline manual campus counter receipt minting with Merkle hash & Ed25519 signature.
 */
export const issueOfflineFeeReceipt = onCall({ region: "asia-east1" }, async (request) => {
  if (!request.auth || request.auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only verified Admin can issue fee receipts.");
  }

  const { campusId, studentUid, studentRollNo, amountPaisa, paymentMode, remarks } = request.data;
  assertCampusScope(campusId);

  try {
    const receipt = await feeEngine.issueReceipt({
      campusId,
      studentUid,
      studentRollNo,
      amountPaisa: Number(amountPaisa),
      paymentMode,
      remarks,
      cashierUid: request.auth.uid
    });

    await auditEmitter.emitEvent({
      action: "FEE_RECEIPT_ISSUED",
      actorUid: request.auth.uid,
      portal: "ADMIN",
      details: { receiptNo: receipt.receiptNo, studentRollNo, amountPaisa }
    });

    return { success: true, receipt };
  } catch (err: any) {
    throw new HttpsError("invalid-argument", err.message);
  }
});

/**
 * 2. verifyBiometricChallenge (S-36, S-50)
 * Verifies single-use hardware biometric challenge proofs.
 */
export const verifyBiometricChallenge = onCall({ region: "asia-east1" }, async (request) => {
  const { proof, expectedNonce } = request.data;
  const result = verifyBiometricChallengeProof(proof, expectedNonce);

  if (!result.verified) {
    throw new HttpsError("unauthenticated", result.error || "Biometric challenge verification failed");
  }

  return { verified: true };
});

/**
 * 3. submitExamResponses (Section 4 & S-49)
 * Receives live exam submission, verifies Merkle commitments, and triggers T+30 auto-grader.
 */
export const submitExamResponses = onCall({ region: "asia-east1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required to submit exam");
  }

  const submission = request.data;
  try {
    const result = await autoGrader.gradeSession(submission);

    await auditEmitter.emitEvent({
      action: "EXAM_SUBMITTED_AND_GRADED",
      actorUid: request.auth.uid,
      portal: "STUDENT",
      details: { examId: submission.examId, scorePercentage: result.scorePercentage, grade: result.grade }
    });

    return { success: true, result };
  } catch (err: any) {
    throw new HttpsError("invalid-argument", err.message);
  }
});

/**
 * 4. triggerRemoteLockdown (S-45)
 */
export const triggerRemoteLockdown = onCall({ region: "asia-east1" }, async (request) => {
  if (!request.auth || request.auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Admin role required to trigger emergency lockdown");
  }

  const { reason } = request.data;
  await lockdown.triggerLockdown(request.auth.uid, reason || "Administrative Security Emergency");

  await auditEmitter.emitEvent({
    action: "EMERGENCY_LOCKDOWN_TRIGGERED",
    actorUid: request.auth.uid,
    portal: "ADMIN",
    details: { reason }
  });

  return { success: true, lockdownActive: true };
});

/**
 * 5. liftRemoteLockdown (S-45)
 */
export const liftRemoteLockdown = onCall({ region: "asia-east1" }, async (request) => {
  if (!request.auth || request.auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Admin role required to lift emergency lockdown");
  }

  await lockdown.liftLockdown(request.auth.uid);

  await auditEmitter.emitEvent({
    action: "EMERGENCY_LOCKDOWN_LIFTED",
    actorUid: request.auth.uid,
    portal: "ADMIN",
    details: {}
  });

  return { success: true, lockdownActive: false };
});

/**
 * 6. recordAuthFailure (F-014)
 */
export const recordAuthFailure = onCall({ region: "asia-east1" }, async (request) => {
  const { deviceFingerprint, ipAddress } = request.data;
  if (!deviceFingerprint) {
    throw new HttpsError("invalid-argument", "Missing device fingerprint");
  }

  const result = await sentinel.recordFailure(deviceFingerprint, ipAddress || "0.0.0.0");
  return result;
});
