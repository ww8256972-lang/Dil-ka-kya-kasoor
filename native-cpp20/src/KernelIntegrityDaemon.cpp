#include "KernelIntegrityDaemon.h"

#include <link.h>
#include <cstring>
#include <atomic>

namespace wci::security {

static std::atomic<uint32_t> g_baselineSentinelCrc{0};
static std::atomic<bool> g_baselineCaptured{false};

uint32_t KernelIntegrityDaemon::computeCrc32(const uint8_t* data, size_t length) {
    if (!data || length == 0) return 0;

    uint32_t crc = 0xFFFFFFFF;
    for (size_t i = 0; i < length; ++i) {
        crc ^= data[i];
        for (int j = 0; j < 8; ++j) {
            uint32_t mask = -(crc & 1);
            crc = (crc >> 1) ^ (0xEDB88320 & mask);
        }
    }
    return ~crc;
}

struct PhdrContext {
    const char* targetSoName;
    uint32_t computedCrc;
    bool found;
};

static int phdrCallback(struct dl_phdr_info* info, size_t size, void* data) {
    (void)size;
    auto* ctx = static_cast<PhdrContext*>(data);

    if (!info->dlpi_name) return 0;
    if (strstr(info->dlpi_name, ctx->targetSoName) == nullptr) {
        return 0;
    }

    ctx->found = true;
    uint32_t aggregatedCrc = 0xFFFFFFFF;

    for (int i = 0; i < info->dlpi_phnum; ++i) {
        const ElfW(Phdr)& phdr = info->dlpi_phdr[i];
        // Examine executable segments (PT_LOAD with PF_X)
        if (phdr.p_type == PT_LOAD && (phdr.p_flags & PF_X)) {
            const auto* segStart = reinterpret_cast<const uint8_t*>(info->dlpi_addr + phdr.p_vaddr);
            size_t segSize = phdr.p_memsz;
            if (segSize > 0) {
                uint32_t segCrc = KernelIntegrityDaemon::computeCrc32(segStart, segSize);
                aggregatedCrc ^= segCrc;
            }
        }
    }

    ctx->computedCrc = aggregatedCrc;
    return 1; // Stop traversal
}

void KernelIntegrityDaemon::captureBaselineChecksum() {
    PhdrContext ctx{"libwci_sentinel.so", 0, false};
    dl_iterate_phdr(phdrCallback, &ctx);
    if (ctx.found && ctx.computedCrc != 0) {
        g_baselineSentinelCrc.store(ctx.computedCrc, std::memory_order_release);
        g_baselineCaptured.store(true, std::memory_order_release);
    }
}

bool KernelIntegrityDaemon::verifyElfIntegrity() {
    if (!g_baselineCaptured.load(std::memory_order_acquire)) {
        captureBaselineChecksum();
        return true;
    }

    PhdrContext ctx{"libwci_sentinel.so", 0, false};
    dl_iterate_phdr(phdrCallback, &ctx);

    if (!ctx.found) {
        return false;
    }

    uint32_t expected = g_baselineSentinelCrc.load(std::memory_order_acquire);
    return (ctx.computedCrc == expected);
}

} // namespace wci::security
