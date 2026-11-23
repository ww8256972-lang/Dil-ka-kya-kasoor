#ifndef WCI_MEMORY_WIPER_H
#define WCI_MEMORY_WIPER_H

#include <cstddef>
#include <cstdint>

namespace wci::security {

class MemoryWiper {
public:
    /**
     * Constant-time memory scrubbing with volatile pointer traversal
     * and compiler optimization barrier to prevent dead-store elimination.
     */
    static void zeroize(void* ptr, size_t size);

    /**
     * Constant-time byte array comparison to prevent timing attacks.
     * Returns true if equal, false otherwise.
     */
    static bool constantTimeCompare(const uint8_t* a, const uint8_t* b, size_t length);
};

} // namespace wci::security

#endif // WCI_MEMORY_WIPER_H
