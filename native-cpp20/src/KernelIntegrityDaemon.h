#ifndef WCI_KERNEL_INTEGRITY_DAEMON_H
#define WCI_KERNEL_INTEGRITY_DAEMON_H

#include <cstdint>
#include <cstddef>
#include <string>

namespace wci::security {

class KernelIntegrityDaemon {
public:
    /**
     * Initializes baseline ELF text segment checksums.
     */
    static void captureBaselineChecksum();

    /**
     * Verifies loaded ELF text segments against baseline to detect inline hooking or patching.
     * Returns true if intact, false if code tampering detected.
     */
    static bool verifyElfIntegrity();

    /**
     * Standard IEEE 802.3 CRC32 calculation.
     */
    static uint32_t computeCrc32(const uint8_t* data, size_t length);
};

} // namespace wci::security

#endif // WCI_KERNEL_INTEGRITY_DAEMON_H
