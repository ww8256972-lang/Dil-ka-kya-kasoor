import * as admin from "firebase-admin";
import * as crypto from "crypto";
import { assertCampusScope } from "../auth/CampusScopeGuard";

export interface QuestionResponse {
  questionIndex: number;
  questionId: string;
  selectedOption: string;
  blindingFactorHex: string;
  commitmentHex: string;
}

export interface ExamSessionSubmission {
  campusId: string;
  studentUid: string;
  studentRollNo: string;
  examId: string;
  responses: QuestionResponse[];
  aggregateMerkleRootHex: string;
}

export class ConfidentialGrader {
  /**
   * Verifies homomorphic answer commitments (S-49) against the submitted Merkle Root.
   */
  static verifyCommitments(submission: ExamSessionSubmission): boolean {
    assertCampusScope(submission.campusId);

    // 1. Verify each commitment
    for (const r of submission.responses) {
      const blinding = Buffer.from(r.blindingFactorHex, "hex");
      const hmac = crypto.createHmac("sha256", blinding);
      const dataToCommit = `WCI_UNNAO_MAIN:${submission.studentUid}:${submission.examId}:${r.questionId}:${r.selectedOption}`;
      hmac.update(dataToCommit, "utf-8");
      const calculatedCommitment = hmac.digest("hex");

      if (calculatedCommitment !== r.commitmentHex) {
        return false;
      }
    }

    // 2. Verify Merkle root
    let currentLevel = submission.responses.map(r => Buffer.from(r.commitmentHex, "hex"));
    if (currentLevel.length === 0) return true;

    while (currentLevel.length > 1) {
      const nextLevel: Buffer[] = [];
      for (let i = 0; i < currentLevel.length; i += 2) {
        const left = currentLevel[i];
        const right = i + 1 < currentLevel.length ? currentLevel[i + 1] : left;
        const md = crypto.createHash("sha256");
        md.update(left);
        md.update(right);
        nextLevel.push(md.digest());
      }
      currentLevel = nextLevel;
    }

    const computedRoot = currentLevel[0].toString("hex");
    return computedRoot === submission.aggregateMerkleRootHex;
  }
}
