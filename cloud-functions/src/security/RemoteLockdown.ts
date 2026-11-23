import * as admin from "firebase-admin";

export class RemoteLockdown {
  private db: admin.firestore.Firestore;

  constructor(db?: admin.firestore.Firestore) {
    this.db = db || admin.firestore();
  }

  /**
   * Activates immediate institute-wide emergency lockdown (S-45).
   */
  async triggerLockdown(adminUid: string, reason: string): Promise<void> {
    const lockdownRef = this.db
      .collection("campuses")
      .document("WCI_UNNAO_MAIN")
      .collection("security_lockdown")
      .doc("active_state");

    await lockdownRef.set({
      lockdownActive: true,
      reason,
      triggeredByAdminUid: adminUid,
      timestampMs: Date.now(),
      activatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
  }

  /**
   * Lifts emergency lockdown following administrative security review.
   */
  async liftLockdown(adminUid: string): Promise<void> {
    const lockdownRef = this.db
      .collection("campuses")
      .document("WCI_UNNAO_MAIN")
      .collection("security_lockdown")
      .doc("active_state");

    await lockdownRef.set({
      lockdownActive: false,
      liftedByAdminUid: adminUid,
      timestampMs: Date.now(),
      liftedAt: admin.firestore.FieldValue.serverTimestamp()
    });
  }
}
