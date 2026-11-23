package com.waqar.institute.ledger

import android.content.Context
import androidx.annotation.Keep
import com.example.data.firestore.InstituteRepository
import com.example.data.model.StudentRecord
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.lsposed.lsparanoid.Obfuscate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Data model for `/fee_receipts/{receiptId}` in Cloud Firestore.
 * Strictly offline manual campus-counter receipt records (Zero Online Payment Gateways).
 */
@Keep
data class OfflineFeeReceiptRecord(
    val id: String = "",
    val receipt_number: String = "",
    val student_id: String = "",
    val student_name: String = "",
    val course_name: String = "",
    val amount: Double = 0.0,
    val amount_paise: Long = 0L,
    val payment_mode: String = "MANUAL_CASH_COUNTER", // MANUAL_CASH_COUNTER | BANK_DEPOSIT | DEMAND_DRAFT
    val collector_admin_uid: String = "",
    val campusId: String = "WCI_UNNAO_MAIN",
    val remarks: String = "",
    val ed25519_signature: String = "",
    val prev_record_hash: String = "GENESIS_WCI_FEE_LEDGER_2026",
    val record_sha256_hash: String = "",
    val chain_index: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Timestamp = Timestamp.now()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "receipt_number" to receipt_number,
        "student_id" to student_id,
        "student_name" to student_name,
        "course_name" to course_name,
        "amount" to amount,
        "amount_paise" to amount_paise,
        "payment_mode" to payment_mode,
        "collector_admin_uid" to collector_admin_uid,
        "campusId" to campusId,
        "remarks" to remarks,
        "ed25519_signature" to ed25519_signature,
        "prev_record_hash" to prev_record_hash,
        "record_sha256_hash" to record_sha256_hash,
        "chain_index" to chain_index,
        "timestamp" to timestamp,
        "createdAt" to createdAt
    )
}

/**
 * Data model for `/student_fees_ledger/{studentId}` in Cloud Firestore.
 * Strictly transparent flat course fee minus verified manual receipts or approved waivers.
 * ZERO EMI, ZERO Installments, ZERO Recurring Auto-Debit.
 */
@Keep
data class StudentFeesLedgerRecord(
    val id: String = "",
    val student_id: String = "",
    val student_name: String = "",
    val enrollment_no: String = "",
    val course_name: String = "",
    val batch_name: String = "",
    val total_fee_paise: Long = 0L,
    val paid_fee_paise: Long = 0L,
    val waiver_paise: Long = 0L,
    val payment_status: String = "PENDING", // CLEARED | PENDING
    val last_receipt_number: String = "",
    val last_receipt_date: String = "",
    val campusId: String = "WCI_UNNAO_MAIN",
    val owner_uid: String = "admin_root",
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Timestamp = Timestamp.now()
) {
    val balancePaise: Long
        get() = (total_fee_paise - paid_fee_paise - waiver_paise).coerceAtLeast(0L)

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "student_id" to student_id,
        "student_name" to student_name,
        "enrollment_no" to enrollment_no,
        "course_name" to course_name,
        "batch_name" to batch_name,
        "total_fee_paise" to total_fee_paise,
        "paid_fee_paise" to paid_fee_paise,
        "waiver_paise" to waiver_paise,
        "payment_status" to payment_status,
        "last_receipt_number" to last_receipt_number,
        "last_receipt_date" to last_receipt_date,
        "campusId" to campusId,
        "owner_uid" to owner_uid.ifBlank { "admin_root" },
        "updatedAt" to updatedAt,
        "createdAt" to createdAt
    )
}

/**
 * Audit log entry for financial and administrative events.
 */
@Keep
data class InstituteAuditLogRecord(
    val id: String = "",
    val actor_uid: String = "",
    val action_type: String = "",
    val details: String = "",
    val record_id: String = "",
    val campusId: String = "WCI_UNNAO_MAIN",
    val timestamp_ms: Long = System.currentTimeMillis(),
    val createdAt: Timestamp = Timestamp.now()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "actor_uid" to actor_uid,
        "action_type" to action_type,
        "details" to details,
        "record_id" to record_id,
        "campusId" to campusId,
        "timestamp_ms" to timestamp_ms,
        "createdAt" to createdAt
    )
}

/**
 * Enterprise Offline Campus Fee Ledger Engine (Master PRD Module 4: F-066..F-080).
 * Hard constraints enforced:
 * - 100% PURE NATIVE ANDROID ONLY
 * - ZERO Online Payment Gateways (No Razorpay, Stripe, Paytm, PhonePe, UPI Intent)
 * - ZERO Installment / EMI System (Flat course fees minus manual receipts)
 * - Single-Campus Hard-Lock: "WCI_UNNAO_MAIN" (26.538162, 80.484187, 50m radius)
 */
@Obfuscate
@Keep
object OfflineFeeLedgerEngine {

    const val CAMPUS_ID: String = "WCI_UNNAO_MAIN"
    const val CAMPUS_ADDRESS: String = "188, DSN College Marg, Ab Nagar, Unnao, Uttar Pradesh 209801, India"
    const val CAMPUS_LATITUDE: Double = 26.538162
    const val CAMPUS_LONGITUDE: Double = 80.484187
    const val CAMPUS_GEOFENCE_RADIUS_METERS: Double = 50.0

    const val GENESIS_HASH = "GENESIS_WCI_FEE_LEDGER_2026"

    val localOfflineReceiptsCache = ConcurrentHashMap<String, OfflineFeeReceiptRecord>()
    val localStudentFeesLedgerCache = ConcurrentHashMap<String, StudentFeesLedgerRecord>()
    val localInstituteAuditLogsCache = ConcurrentHashMap<String, InstituteAuditLogRecord>()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Computes cryptographic SHA-256 hash chaining previous record to current receipt.
     */
    fun computeChainedFeeRecordHash(
        receiptId: String,
        studentId: String,
        amountPaise: Long,
        paymentMode: String,
        timestamp: Long,
        prevRecordHash: String
    ): String {
        val payload = "$receiptId|$studentId|$amountPaise|$paymentMode|$timestamp|$prevRecordHash|$CAMPUS_ID"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(payload.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies cryptographic chain continuity across fee records.
     */
    fun verifyFeeLedgerHashChain(records: List<OfflineFeeReceiptRecord>): Boolean {
        if (records.isEmpty()) return true
        val sorted = records.sortedBy { it.chain_index }
        var expectedPrev = GENESIS_HASH
        for (rec in sorted) {
            if (rec.prev_record_hash != expectedPrev && expectedPrev != GENESIS_HASH) {
                return false
            }
            val expectedHash = computeChainedFeeRecordHash(
                receiptId = rec.id,
                studentId = rec.student_id,
                amountPaise = rec.amount_paise,
                paymentMode = rec.payment_mode,
                timestamp = rec.timestamp,
                prevRecordHash = rec.prev_record_hash
            )
            if (rec.record_sha256_hash.isNotBlank() && rec.record_sha256_hash != expectedHash) {
                return false
            }
            expectedPrev = rec.record_sha256_hash
        }
        return true
    }

    /**
     * Generates a sequential tamper-evident receipt number: WCI-REC-2026-XXXXX
     */
    fun generateSequentialReceiptNumber(sequence: Long): String {
        return "WCI-REC-2026-%05d".format(Locale.US, sequence)
    }

    /**
     * Records a manual offline campus counter fee receipt (F-067).
     */
    suspend fun recordOfflineCounterReceipt(
        db: FirebaseFirestore,
        adminUid: String,
        student: StudentRecord,
        amountPaise: Long,
        paymentMode: String,
        remarks: String
    ): Result<OfflineFeeReceiptRecord> = withContext(Dispatchers.IO) {
        runCatching {
            require(amountPaise > 0L) { "Receipt amount must be strictly greater than zero." }
            val allowedModes = setOf("MANUAL_CASH_COUNTER", "BANK_DEPOSIT", "DEMAND_DRAFT")
            require(paymentMode in allowedModes) { "Payment mode must be offline: $allowedModes" }

            val receiptId = "rec_" + UUID.randomUUID().toString().replace("-", "").take(16)
            val now = System.currentTimeMillis()
            val chainIndex = (localOfflineReceiptsCache.size + 1).toLong()
            val receiptNumber = generateSequentialReceiptNumber(chainIndex)

            val prevHash = localOfflineReceiptsCache.values
                .maxByOrNull { it.chain_index }
                ?.record_sha256_hash ?: GENESIS_HASH

            val hash = computeChainedFeeRecordHash(
                receiptId = receiptId,
                studentId = student.id,
                amountPaise = amountPaise,
                paymentMode = paymentMode,
                timestamp = now,
                prevRecordHash = prevHash
            )

            val record = OfflineFeeReceiptRecord(
                id = receiptId,
                receipt_number = receiptNumber,
                student_id = student.id,
                student_name = student.fullName,
                course_name = student.courseName,
                amount = amountPaise / 100.0,
                amount_paise = amountPaise,
                payment_mode = paymentMode,
                collector_admin_uid = adminUid,
                campusId = CAMPUS_ID,
                remarks = remarks,
                prev_record_hash = prevHash,
                record_sha256_hash = hash,
                chain_index = chainIndex,
                timestamp = now,
                createdAt = Timestamp.now()
            )

            // Write to Cloud Firestore `/fee_receipts`
            db.collection("fee_receipts").document(receiptId).set(record.toFirestoreMap()).await()
            localOfflineReceiptsCache[receiptId] = record

            // Update student ledger
            val existingLedger = localStudentFeesLedgerCache[student.id]
            val updatedLedger = buildOrSyncStudentLedger(student, existingLedger, additionalPaidPaise = amountPaise, receiptNum = receiptNumber)
            db.collection("student_fees_ledger").document(student.id).set(updatedLedger.toFirestoreMap()).await()
            localStudentFeesLedgerCache[student.id] = updatedLedger

            // Append immutable audit log
            logAuditEventInternal(db, adminUid, "OFFLINE_FEE_RECEIPT_ISSUED", "Issued $receiptNumber for ₹${amountPaise/100} to ${student.fullName}", receiptId)

            record
        }
    }

    /**
     * Builds or syncs student fee ledger balance (F-066).
     */
    fun buildOrSyncStudentLedger(
        student: StudentRecord,
        existing: StudentFeesLedgerRecord? = null,
        additionalPaidPaise: Long = 0L,
        receiptNum: String = ""
    ): StudentFeesLedgerRecord {
        val totalFee = if (existing != null && existing.total_fee_paise > 0L) {
            existing.total_fee_paise
        } else {
            student.totalFeePaise.coerceAtLeast(0L)
        }

        val basePaid = existing?.paid_fee_paise ?: student.paidFeePaise.coerceAtLeast(0L)
        val paidFee = basePaid + additionalPaidPaise
        val waiver = existing?.waiver_paise ?: 0L
        val balance = (totalFee - paidFee - waiver).coerceAtLeast(0L)
        val status = if (balance == 0L && totalFee > 0L) "CLEARED" else "PENDING"
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        return StudentFeesLedgerRecord(
            id = student.id,
            student_id = student.id,
            student_name = student.fullName,
            enrollment_no = student.enrollmentNo.ifBlank { student.id.take(8).uppercase() },
            course_name = student.courseName,
            batch_name = student.batchName,
            total_fee_paise = totalFee,
            paid_fee_paise = paidFee,
            waiver_paise = waiver,
            payment_status = status,
            last_receipt_number = receiptNum.ifBlank { existing?.last_receipt_number.orEmpty() },
            last_receipt_date = if (receiptNum.isNotBlank()) dateStr else existing?.last_receipt_date.orEmpty(),
            campusId = CAMPUS_ID,
            owner_uid = "admin_root",
            updatedAt = System.currentTimeMillis(),
            createdAt = existing?.createdAt ?: Timestamp.now()
        )
    }

    /**
     * Real-time Firestore flow for offline fee receipts.
     */
    fun observeOfflineReceipts(db: FirebaseFirestore): Flow<List<OfflineFeeReceiptRecord>> = callbackFlow {
        val registration = db.collection("fee_receipts")
            .whereEqualTo("campusId", CAMPUS_ID)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(localOfflineReceiptsCache.values.toList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        runCatching {
                            OfflineFeeReceiptRecord(
                                id = doc.getString("id") ?: doc.id,
                                receipt_number = doc.getString("receipt_number").orEmpty(),
                                student_id = doc.getString("student_id").orEmpty(),
                                student_name = doc.getString("student_name").orEmpty(),
                                course_name = doc.getString("course_name").orEmpty(),
                                amount = doc.getDouble("amount") ?: 0.0,
                                amount_paise = doc.getLong("amount_paise") ?: 0L,
                                payment_mode = doc.getString("payment_mode") ?: "MANUAL_CASH_COUNTER",
                                collector_admin_uid = doc.getString("collector_admin_uid").orEmpty(),
                                campusId = doc.getString("campusId") ?: CAMPUS_ID,
                                remarks = doc.getString("remarks").orEmpty(),
                                ed25519_signature = doc.getString("ed25519_signature").orEmpty(),
                                prev_record_hash = doc.getString("prev_record_hash") ?: GENESIS_HASH,
                                record_sha256_hash = doc.getString("record_sha256_hash").orEmpty(),
                                chain_index = doc.getLong("chain_index") ?: 0L,
                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            )
                        }.getOrNull()
                    }
                    list.forEach { localOfflineReceiptsCache[it.id] = it }
                    trySend(list)
                }
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    /**
     * Real-time Firestore flow for student fee ledger balances.
     */
    fun observeStudentFeesLedger(db: FirebaseFirestore): Flow<List<StudentFeesLedgerRecord>> = callbackFlow {
        val registration = db.collection("student_fees_ledger")
            .whereEqualTo("campusId", CAMPUS_ID)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(localStudentFeesLedgerCache.values.toList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        runCatching {
                            StudentFeesLedgerRecord(
                                id = doc.getString("id") ?: doc.id,
                                student_id = doc.getString("student_id").orEmpty(),
                                student_name = doc.getString("student_name").orEmpty(),
                                enrollment_no = doc.getString("enrollment_no").orEmpty(),
                                course_name = doc.getString("course_name").orEmpty(),
                                batch_name = doc.getString("batch_name").orEmpty(),
                                total_fee_paise = doc.getLong("total_fee_paise") ?: 0L,
                                paid_fee_paise = doc.getLong("paid_fee_paise") ?: 0L,
                                waiver_paise = doc.getLong("waiver_paise") ?: 0L,
                                payment_status = doc.getString("payment_status") ?: "PENDING",
                                last_receipt_number = doc.getString("last_receipt_number").orEmpty(),
                                last_receipt_date = doc.getString("last_receipt_date").orEmpty(),
                                campusId = doc.getString("campusId") ?: CAMPUS_ID,
                                owner_uid = doc.getString("owner_uid") ?: "admin_root",
                                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                            )
                        }.getOrNull()
                    }
                    list.forEach { localStudentFeesLedgerCache[it.id] = it }
                    trySend(list)
                }
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    /**
     * Real-time Firestore flow for administrative audit logs.
     */
    fun observeInstituteAuditLogs(db: FirebaseFirestore): Flow<List<InstituteAuditLogRecord>> = callbackFlow {
        val registration = db.collection("institute_audit_logs")
            .whereEqualTo("campusId", CAMPUS_ID)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(localInstituteAuditLogsCache.values.toList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        runCatching {
                            InstituteAuditLogRecord(
                                id = doc.getString("id") ?: doc.id,
                                actor_uid = doc.getString("actor_uid").orEmpty(),
                                action_type = doc.getString("action_type").orEmpty(),
                                details = doc.getString("details").orEmpty(),
                                record_id = doc.getString("record_id").orEmpty(),
                                campusId = doc.getString("campusId") ?: CAMPUS_ID,
                                timestamp_ms = doc.getLong("timestamp_ms") ?: System.currentTimeMillis()
                            )
                        }.getOrNull()
                    }
                    list.forEach { localInstituteAuditLogsCache[it.id] = it }
                    trySend(list)
                }
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    /**
     * Internal audit event logger.
     */
    fun logAuditEventInternal(
        db: FirebaseFirestore,
        actorUid: String,
        actionType: String,
        details: String,
        recordId: String
    ) {
        engineScope.launch {
            runCatching {
                val logId = "log_" + UUID.randomUUID().toString().replace("-", "").take(16)
                val log = InstituteAuditLogRecord(
                    id = logId,
                    actor_uid = actorUid,
                    action_type = actionType,
                    details = details,
                    record_id = recordId,
                    campusId = CAMPUS_ID,
                    timestamp_ms = System.currentTimeMillis(),
                    createdAt = Timestamp.now()
                )
                db.collection("institute_audit_logs").document(logId).set(log.toFirestoreMap()).await()
                localInstituteAuditLogsCache[logId] = log
            }
        }
    }
}
