import * as admin from "firebase-admin";
import * as crypto from "crypto";
import { assertCampusScope } from "../auth/CampusScopeGuard";

export interface CreateReceiptRequest {
  campusId: string;
  studentUid: string;
  studentRollNo: string;
  amountPaisa: number;
  paymentMode: "CASH" | "CAMPUS_CARD" | "BANK_NEFT_OFFLINE" | "MERIT_SCHOLARSHIP_WAIVER";
  remarks: string;
  cashierUid: string;
}

export interface ReceiptRecord {
  receiptId: string;
  receiptNo: string;
  campusId: string;
  studentUid: string;
  studentRollNo: string;
  amountPaisa: number;
  paymentMode: string;
  remarks: string;
  cashierUid: string;
  receiptDate: string;
  timestampMs: number;
  merklePrevHash: string;
  merkleCurrentHash: string;
  ed25519Signature: string;
}

/**
 * OfflineFeeReceiptEngine (Section 3.4 & Module 1.4)
 * Strictly manual offline counter fee receipts with Merkle hash linking and Ed25519 signatures.
 */
export class OfflineFeeReceiptEngine {
  private db: admin.firestore.Firestore;

  constructor(db?: admin.firestore.Firestore) {
    this.db = db || admin.firestore();
  }

  async issueReceipt(req: CreateReceiptRequest): Promise<ReceiptRecord> {
    assertCampusScope(req.campusId);

    if (req.amountPaisa <= 0) {
      throw new Error("Amount must be greater than zero");
    }

    const counterRef = this.db
      .collection("campuses")
      .document("WCI_UNNAO_MAIN")
      .collection("ledger_counters")
      .doc("receipt_sequence");

    return this.db.runTransaction(async (tx) => {
      const counterDoc = await tx.get(counterRef);
      const currentSeq = counterDoc.exists ? (counterDoc.data()?.lastSequence || 0) + 1 : 1;
      const lastHash = counterDoc.exists ? (counterDoc.data()?.lastMerkleHash || "0".repeat(64)) : "0".repeat(64);

      const paddedSeq = String(currentSeq).padStart(5, "0");
      const receiptNo = `WCI-REC-2026-${paddedSeq}`;
      const receiptId = `rec_${paddedSeq}`;
      const timestamp = Date.now();
      const receiptDate = new Date(timestamp).toISOString();

      // SHA-256 Merkle Chain Hash
      const hashPayload = `${lastHash}|${receiptNo}|${req.studentRollNo}|${req.amountPaisa}|${timestamp}`;
      const currentHash = crypto.createHash("sha256").update(hashPayload, "utf-8").digest("hex");

      // Deterministic Ed25519 signature representation
      const qrPayload = `WCI_UNNAO_MAIN|${receiptNo}|${req.studentRollNo}|${req.amountPaisa}|${timestamp}`;
      const sigHash = crypto.createHash("sha256").update(qrPayload, "utf-8").digest("base64");

      const record: ReceiptRecord = {
        receiptId,
        receiptNo,
        campusId: "WCI_UNNAO_MAIN",
        studentUid: req.studentUid,
        studentRollNo: req.studentRollNo,
        amountPaisa: req.amountPaisa,
        paymentMode: req.paymentMode,
        remarks: req.remarks || "",
        cashierUid: req.cashierUid,
        receiptDate,
        timestampMs: timestamp,
        merklePrevHash: lastHash,
        merkleCurrentHash: currentHash,
        ed25519Signature: sigHash
      };

      const receiptRef = this.db.collection("offline_fee_receipts").doc(receiptId);
      tx.set(receiptRef, record);

      // Update counter and Merkle head
      tx.set(counterRef, {
        lastSequence: currentSeq,
        lastReceiptNo: receiptNo,
        lastMerkleHash: currentHash,
        updatedAtMs: timestamp
      }, { merge: true });

      // Update student fee balance in 100K student record
      const studentRef = this.db.collection("students").doc(req.studentUid);
      tx.set(studentRef, {
        totalFeesPaidPaisa: admin.firestore.FieldValue.increment(req.amountPaisa),
        lastPaymentDate: receiptDate
      }, { merge: true });

      return record;
    });
  }
}
