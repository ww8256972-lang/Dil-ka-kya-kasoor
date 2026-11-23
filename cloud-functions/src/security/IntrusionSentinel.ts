import * as admin from "firebase-admin";

export class IntrusionSentinel {
  private db: admin.firestore.Firestore;

  constructor(db?: admin.firestore.Firestore) {
    this.db = db || admin.firestore();
  }

  /**
   * Records a failed authentication attempt and evaluates auto-ban policy.
   * After 8 attempts, automatically blacklists the device fingerprint.
   */
  async recordFailure(deviceFingerprint: string, ipAddress: string): Promise<{ banned: boolean; attempts: number }> {
    const sentinelRef = this.db
      .collection("campuses")
      .document("WCI_UNNAO_MAIN")
      .collection("intrusion_sentinel")
      .doc(deviceFingerprint);

    return this.db.runTransaction(async (tx) => {
      const doc = await tx.get(sentinelRef);
      const currentAttempts = doc.exists ? (doc.data()?.attempts || 0) + 1 : 1;

      const isBanned = currentAttempts >= 8;

      tx.set(sentinelRef, {
        deviceFingerprint,
        lastIp: ipAddress,
        attempts: currentAttempts,
        lastFailedAtMs: Date.now(),
        isBanned
      }, { merge: true });

      if (isBanned) {
        const banRef = this.db
          .collection("campuses")
          .document("WCI_UNNAO_MAIN")
          .collection("banned_devices")
          .doc(deviceFingerprint);

        tx.set(banRef, {
          deviceFingerprint,
          reason: "AUTOMATED_BRUTE_FORCE_AUTO_BAN (>= 8 failed auth attempts)",
          bannedAt: admin.firestore.FieldValue.serverTimestamp()
        });
      }

      return { banned: isBanned, attempts: currentAttempts };
    });
  }

  /**
   * Resets counter on successful verified login.
   */
  async clearFailures(deviceFingerprint: string): Promise<void> {
    await this.db
      .collection("campuses")
      .document("WCI_UNNAO_MAIN")
      .collection("intrusion_sentinel")
      .doc(deviceFingerprint)
      .delete();
  }
}
