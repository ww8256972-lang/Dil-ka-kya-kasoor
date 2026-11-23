#include "SandboxMemoryCap.h"
#include <sys/resource.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstdio>
#include <cstdlib>

namespace wci::security {

bool SandboxMemoryCap::enforceVirtualMemoryLimit(uint64_t maxBytes) {
    struct rlimit rl;
    rl.rlim_cur = maxBytes;
    rl.rlim_max = maxBytes;
    return setrlimit(RLIMIT_AS, &rl) == 0;
}

bool SandboxMemoryCap::enforceStackLimit(uint64_t maxStackBytes) {
    struct rlimit rl;
    rl.rlim_cur = maxStackBytes;
    rl.rlim_max = maxStackBytes;
    return setrlimit(RLIMIT_STACK, &rl) == 0;
}

uint64_t SandboxMemoryCap::getCurrentRssBytes() {
    int fd = open("/proc/self/statm", O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return 0;
    }

    char buffer[128];
    ssize_t bytesRead = read(fd, buffer, sizeof(buffer) - 1);
    close(fd);

    if (bytesRead <= 0) {
        return 0;
    }
    buffer[bytesRead] = '\0';

    unsigned long size = 0;
    unsigned long resident = 0;
    if (sscanf(buffer, "%lu %lu", &size, &resident) == 2) {
        long pageSize = sysconf(_SC_PAGESIZE);
        if (pageSize <= 0) pageSize = 4096;
        return static_cast<uint64_t>(resident) * static_cast<uint64_t>(pageSize);
    }

    return 0;
}

bool SandboxMemoryCap::isMemoryUnderThreshold(uint64_t maxBytes) {
    uint64_t currentRss = getCurrentRssBytes();
    if (currentRss == 0) return true; // Non-fatal if statm is restricted
    return currentRss <= maxBytes;
}

} // namespace wci::security
