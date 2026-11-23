import * as admin from "firebase-admin";

export interface ZanzibarTuple {
  namespace: string;
  objectId: string;
  relation: string;
  userType: "user" | "userset";
  userId: string;
}

/**
 * ZanzibarEvaluator (Section 7.3 & PRD Zanzibar FGAC)
 * Evaluates relationship-based access control tuples stored in Firestore
 * collection `campuses/WCI_UNNAO_MAIN/zanzibar_tuples`.
 */
export class ZanzibarEvaluator {
  private db: admin.firestore.Firestore;

  constructor(db?: admin.firestore.Firestore) {
    this.db = db || admin.firestore();
  }

  /**
   * Checks if user has specific relation to an object.
   */
  async checkPermission(
    namespace: string,
    objectId: string,
    relation: string,
    userId: string
  ): Promise<boolean> {
    // 1. Institute Admin has universal access
    const adminCheck = await this.db
      .collection("campuses")
      .document("WCI_UNNAO_MAIN")
      .collection("zanzibar_tuples")
      .where("namespace", "==", "institute")
      .where("objectId", "==", "main")
      .where("relation", "==", "admin")
      .where("userId", "==", userId)
      .limit(1)
      .get();

    if (!adminCheck.empty) {
      return true;
    }

    // 2. Direct tuple check
    const directTuple = await this.db
      .collection("campuses")
      .document("WCI_UNNAO_MAIN")
      .collection("zanzibar_tuples")
      .where("namespace", "==", namespace)
      .where("objectId", "==", objectId)
      .where("relation", "==", relation)
      .where("userId", "==", userId)
      .limit(1)
      .get();

    return !directTuple.empty;
  }

  /**
   * Writes a new relationship tuple.
   */
  async grantRelation(
    namespace: string,
    objectId: string,
    relation: string,
    userId: string
  ): Promise<void> {
    const tupleId = `${namespace}_${objectId}_${relation}_${userId}`;
    await this.db
      .collection("campuses")
      .document("WCI_UNNAO_MAIN")
      .collection("zanzibar_tuples")
      .doc(tupleId)
      .set({
        namespace,
        objectId,
        relation,
        userId,
        grantedAt: admin.firestore.FieldValue.serverTimestamp()
      });
  }

  /**
   * Revokes an existing relationship tuple.
   */
  async revokeRelation(
    namespace: string,
    objectId: string,
    relation: string,
    userId: string
  ): Promise<void> {
    const tupleId = `${namespace}_${objectId}_${relation}_${userId}`;
    await this.db
      .collection("campuses")
      .document("WCI_UNNAO_MAIN")
      .collection("zanzibar_tuples")
      .doc(tupleId)
      .delete();
  }
}
