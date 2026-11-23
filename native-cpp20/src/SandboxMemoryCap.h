#ifndef WCI_SANDBOX_MEMORY_CAP_H
#define WCI_SANDBOX_MEMORY_CAP_H

#include <cstdint>
#include <cstddef>

namespace wci::security {

class SandboxMemoryCap {
public:
    /**
     * Applies hard virtual memory address space limit (RLIMIT_AS).
     * @param maxBytes Maximum virtual memory in bytes (default 512MB).
     * @return true if limit was successfully set.
     */
    static bool enforceVirtualMemoryLimit(uint64_t maxBytes = 512ULL * 1024ULL * 1024ULL);

    /**
     * Sets stack size limit (RLIMIT_STACK) to prevent stack overflow exploits.
     * @param maxStackBytes Maximum stack size in bytes (default 512KB).
     * @return true if limit was successfully set.
     */
    static bool enforceStackLimit(uint64_t maxStackBytes = 512ULL * 1024ULL);

    /**
     * Reads current process Resident Set Size (RSS) in bytes from /proc/self/statm.
     * @return RSS in bytes, or 0 on failure.
     */
    static uint64_t getCurrentRssBytes();

    /**
     * Verifies if current memory consumption is safely below maximum threshold.
     */
    static bool isMemoryUnderThreshold(uint64_t maxBytes);
};

} // namespace wci::security

#endif // WCI_SANDBOX_MEMORY_CAP_H
