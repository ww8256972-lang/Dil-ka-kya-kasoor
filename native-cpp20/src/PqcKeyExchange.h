#ifndef WCI_PQC_KEY_EXCHANGE_H
#define WCI_PQC_KEY_EXCHANGE_H

#include <cstdint>
#include <cstddef>
#include <vector>

namespace wci::crypto {

// Hybrid ML-KEM-768 + X25519 Constants
constexpr size_t X25519_KEY_SIZE = 32;
constexpr size_t MLKEM768_PUBLIC_KEY_SIZE = 1184;
constexpr size_t MLKEM768_CIPHERTEXT_SIZE = 1088;
constexpr size_t MLKEM768_SHARED_SECRET_SIZE = 32;

constexpr size_t HYBRID_PUBLIC_KEY_SIZE = X25519_KEY_SIZE + MLKEM768_PUBLIC_KEY_SIZE; // 1216 bytes
constexpr size_t HYBRID_CIPHERTEXT_SIZE = X25519_KEY_SIZE + MLKEM768_CIPHERTEXT_SIZE; // 1120 bytes
constexpr size_t DERIVED_SECRET_SIZE = 32; // SHA-256 output

struct HybridKeyPair {
    std::vector<uint8_t> publicKey;  // 1216 bytes: X25519 (32) + ML-KEM-768 (1184)
    std::vector<uint8_t> privateKey; // 2432 bytes: X25519 (32) + ML-KEM-768 (2400)
};

struct HybridEncapsulationResult {
    std::vector<uint8_t> ciphertext;   // 1120 bytes: X25519 (32) + ML-KEM-768 (1088)
    std::vector<uint8_t> sharedSecret; // 32 bytes derived master secret
};

class PqcKeyExchange {
public:
    /**
     * Generates a hybrid ML-KEM-768 + X25519 keypair using /dev/urandom CSPRNG.
     */
    static HybridKeyPair generateKeyPair();

    /**
     * Encapsulates a shared secret against recipient's hybrid public key.
     * @param recipientPublicKey 1216-byte hybrid public key.
     * @return Encapsulation result containing ciphertext and 32-byte shared secret.
     */
    static HybridEncapsulationResult encapsulate(const std::vector<uint8_t>& recipientPublicKey);

    /**
     * Decapsulates the shared secret using recipient's hybrid private key and received ciphertext.
     * @param ciphertext 1120-byte hybrid ciphertext.
     * @param privateKey 2432-byte hybrid private key.
     * @return 32-byte derived master secret.
     */
    static std::vector<uint8_t> decapsulate(
        const std::vector<uint8_t>& ciphertext,
        const std::vector<uint8_t>& privateKey
    );

private:
    static void fillSecureRandom(uint8_t* dest, size_t size);
    static void hkdfExtractExpand(
        const uint8_t* ikm1, size_t ikm1Len,
        const uint8_t* ikm2, size_t ikm2Len,
        const uint8_t* info, size_t infoLen,
        uint8_t* outKey, size_t outKeyLen
    );
};

} // namespace wci::crypto

#endif // WCI_PQC_KEY_EXCHANGE_H
