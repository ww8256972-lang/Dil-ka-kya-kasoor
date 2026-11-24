package com.waqar.security.advanced

import java.security.MessageDigest

/**
 * MerklePatriciaAuditTree: Cryptographic Tamper-Evident Audit Log Verification Tree.
 *
 * Implements a binary SHA-256 Merkle Tree over offline transaction records, attendance stamps,
 * and security events to guarantee cryptographic tamper-evidence:
 * 1. Hashes individual records into leaf digests: Leaf = SHA256(0x00 || record_bytes).
 * 2. Recursively merges adjacent nodes: Parent = SHA256(0x01 || left || right).
 * 3. Derives a single 32-byte Merkle Root Hash summarizing the entire state.
 * 4. Generates compact logarithmic inclusion audit proofs (O(log N)).
 * 5. Instant verifyIntegrity() check detects even a single-bit alteration in local SQLite/Room data.
 */
class MerklePatriciaAuditTree(records: List<ByteArray>) {

    data class AuditProofStep(
        val isRightSibling: Boolean,
        val siblingHash: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AuditProofStep) return false
            return isRightSibling == other.isRightSibling && siblingHash.contentEquals(other.siblingHash)
        }

        override fun hashCode(): Int = 31 * isRightSibling.hashCode() + siblingHash.contentHashCode()
    }

    private val leafHashes: List<ByteArray>
    val rootHash: ByteArray

    init {
        require(records.isNotEmpty()) { "Merkle tree requires at least 1 record" }
        leafHashes = records.map { hashLeaf(it) }
        rootHash = buildTreeRoot(leafHashes)
    }

    /**
     * Generates an inclusion audit proof for the record at recordIndex.
     */
    fun generateInclusionProof(recordIndex: Int): List<AuditProofStep> {
        require(recordIndex in leafHashes.indices) { "Index out of bounds" }
        val proof = mutableListOf<AuditProofStep>()
        var curLevel = leafHashes
        var idx = recordIndex

        while (curLevel.size > 1) {
            val nextLevel = mutableListOf<ByteArray>()
            for (i in 0 until curLevel.size step 2) {
                val left = curLevel[i]
                val right = if (i + 1 < curLevel.size) curLevel[i + 1] else left

                if (i == idx || i + 1 == idx) {
                    val sibling = if (idx % 2 == 0) right else left
                    val isRight = (idx % 2 == 0)
                    proof.add(AuditProofStep(isRightSibling = isRight, siblingHash = sibling))
                }
                nextLevel.add(hashInternal(left, right))
            }
            idx /= 2
            curLevel = nextLevel
        }
        return proof
    }

    companion object {
        private const val LEAF_PREFIX: Byte = 0x00
        private const val INTERNAL_PREFIX: Byte = 0x01

        fun hashLeaf(record: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(LEAF_PREFIX)
            return md.digest(record)
        }

        fun hashInternal(left: ByteArray, right: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(INTERNAL_PREFIX)
            md.update(left)
            md.update(right)
            return md.digest()
        }

        private fun buildTreeRoot(leaves: List<ByteArray>): ByteArray {
            var curLevel = leaves
            while (curLevel.size > 1) {
                val nextLevel = mutableListOf<ByteArray>()
                for (i in 0 until curLevel.size step 2) {
                    val left = curLevel[i]
                    val right = if (i + 1 < curLevel.size) curLevel[i + 1] else left
                    nextLevel.add(hashInternal(left, right))
                }
                curLevel = nextLevel
            }
            return curLevel.first()
        }

        /**
         * Verifies that targetRecord is included under expectedRootHash given proof.
         */
        fun verifyIntegrity(
            targetRecord: ByteArray,
            proof: List<AuditProofStep>,
            expectedRootHash: ByteArray
        ): Boolean {
            var currentHash = hashLeaf(targetRecord)
            for (step in proof) {
                currentHash = if (step.isRightSibling) {
                    hashInternal(currentHash, step.siblingHash)
                } else {
                    hashInternal(step.siblingHash, currentHash)
                }
            }
            return currentHash.contentEquals(expectedRootHash)
        }
    }
}
