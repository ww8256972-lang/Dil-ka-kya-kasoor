import * as admin from "firebase-admin";
import * as crypto from "crypto";

export interface AuditEvent {
  action: string;
  actorUid: string;
  portal: "ADMIN" | "TEACHER" | "STUDENT" | "PARENT" | "SYSTEM";
  details: Record<string, any>;
  ipAddress?: string;
  deviceFingerprint?: string;
}

export class MerkleEmitter {
  private db: admin.firestore.Firestore;

  constructor(db?: admin.firestore.Firestore) {
    this.db = db || admin.firestore();
  }

  async emitEvent(event: AuditEvent): Promise<string> {
    const headRef = this.db
      .collection("campuses")
      .document("WCI_UNNAO_MAIN")
      .collection("audit_merkle_head")
      .doc("current");

    return this.db.runTransaction(async (tx) => {
      const headDoc = await tx.get(headRef);
      const prevHash = headDoc.exists ? (headDoc.data()?.lastHash || "0".repeat(64)) : "0".repeat(64);
      const sequence = headDoc.exists ? (headDoc.data()?.sequence || 0) + 1 : 1;

      const timestamp = Date.now();
      const payloadString = JSON.stringify(event.details);
      const raw = `${prevHash}|${sequence}|${event.action}|${event.actorUid}|${payloadString}|${timestamp}`;
      const currentHash = crypto.createHash("sha256").update(raw, "utf-8").digest("hex");

      const eventId = `audit_${String(sequence).padStart(6, "0")}`;
      const eventRef = this.db
        .collection("campuses")
        .document("WCI_UNNAO_MAIN")
        .collection("audit_merkle_chain")
        .doc(eventId);

      tx.set(eventRef, {
        eventId,
        sequence,
        campusId: "WCI_UNNAO_MAIN",
        action: event.action,
        actorUid: event.actorUid,
        portal: event.portal,
        details: event.details,
        ipAddress: event.ipAddress || "",
        deviceFingerprint: event.deviceFingerprint || "",
        timestampMs: timestamp,
        timestampIso: new Date(timestamp).toISOString(),
        prevHash,
        currentHash
      });

      tx.set(headRef, {
        sequence,
        lastHash: currentHash,
        lastEventId: eventId,
        updatedAtMs: timestamp
      }, { merge: true });

      return currentHash;
    });
  }
}
