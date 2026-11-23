import * as admin from "firebase-admin";
import { assertCampusScope } from "../auth/CampusScopeGuard";
import { ConfidentialGrader, ExamSessionSubmission } from "./ConfidentialGrader";

export class T30AutoGrader {
  private db: admin.firestore.Firestore;

  constructor(db?: admin.firestore.Firestore) {
    this.db = db || admin.firestore();
  }

  /**
   * Executes T+30 auto-grading upon exam cutoff.
   */
  async gradeSession(submission: ExamSessionSubmission): Promise<{
    resultId: string;
    totalMarks: number;
    scorePercentage: number;
    grade: string;
  }> {
    assertCampusScope(submission.campusId);

    // 1. Verify S-49 Homomorphic Commitment Integrity
    const isIntegrityVerified = ConfidentialGrader.verifyCommitments(submission);
    if (!isIntegrityVerified) {
      throw new Error("EXAM_FRAUD_DETECTED: Commitment Merkle root mismatch");
    }

    // 2. Fetch Answer Key for the Exam
    const examDoc = await this.db.collection("exams").doc(submission.examId).get();
    if (!examDoc.exists) {
      throw new Error(`Exam not found: ${submission.examId}`);
    }

    const answerKey = examDoc.data()?.answerKey || {}; // Map<questionId, correctOption>
    let correctCount = 0;
    let wrongCount = 0;
    const totalQuestions = submission.responses.length;

    for (const resp of submission.responses) {
      const correctOption = answerKey[resp.questionId];
      if (correctOption) {
        if (resp.selectedOption.trim().toUpperCase() === correctOption.trim().toUpperCase()) {
          correctCount++;
        } else {
          wrongCount++;
        }
      }
    }

    const marksObtained = correctCount; // 1 mark per correct
    const totalPossibleMarks = totalQuestions > 0 ? totalQuestions : 100;
    const scorePercentage = Math.round((marksObtained / totalPossibleMarks) * 10000) / 100;

    const grade =
      scorePercentage >= 85 ? "A+" :
      scorePercentage >= 75 ? "A" :
      scorePercentage >= 65 ? "B" :
      scorePercentage >= 50 ? "C" : "F";

    const resultId = `res_${submission.examId}_${submission.studentRollNo}`;
    const resultRecord = {
      resultId,
      campusId: "WCI_UNNAO_MAIN",
      examId: submission.examId,
      studentUid: submission.studentUid,
      studentRollNo: submission.studentRollNo,
      marksObtained,
      totalMarks: totalPossibleMarks,
      scorePercentage,
      grade,
      correctCount,
      wrongCount,
      gradedAtMs: Date.now(),
      gradedDate: new Date().toISOString(),
      merkleRootVerified: true
    };

    await this.db.collection("examResults").doc(resultId).set(resultRecord);

    // Trigger parent push notification record in Firestore
    await this.db.collection("parent_notifications").add({
      campusId: "WCI_UNNAO_MAIN",
      studentRollNo: submission.studentRollNo,
      type: "EXAM_RESULT_PUBLISHED",
      title: "Exam Result Declared",
      message: `Result for Roll No ${submission.studentRollNo}: Scored ${scorePercentage}% (Grade ${grade}) in Exam ${submission.examId}.`,
      createdAtMs: Date.now()
    });

    return {
      resultId,
      totalMarks: marksObtained,
      scorePercentage,
      grade
    };
  }
}
