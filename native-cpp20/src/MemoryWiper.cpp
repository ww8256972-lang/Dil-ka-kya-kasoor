#include "MemoryWiper.h"

namespace wci::security {

void MemoryWiper::zeroize(void* ptr, size_t size) {
    if (!ptr || size == 0) return;
    volatile uint8_t* p = static_cast<volatile uint8_t*>(ptr);
    while (size--) {
        *p++ = 0;
    }
    // Compiler memory barrier preventing dead-store elimination
    __asm__ __volatile__("" : : "r"(ptr) : "memory");
}

bool MemoryWiper::constantTimeCompare(const uint8_t* a, const uint8_t* b, size_t length) {
    if (!a || !b) return false;
    uint8_t result = 0;
    for (size_t i = 0; i < length; ++i) {
        result |= (a[i] ^ b[i]);
    }
    return (result == 0);
}

} // namespace wci::security
