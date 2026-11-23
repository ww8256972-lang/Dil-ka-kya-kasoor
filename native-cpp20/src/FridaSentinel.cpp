#include "FridaSentinel.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <cerrno>

namespace wci::security {

ThreatReport FridaSentinel::scanForTampering() {
    ThreatReport report;
    std::string artifact;

    if (scanProcMaps(artifact)) {
        report.fridaDetected = true;
        report.detectedArtifact = artifact;
    }

    uint32_t tpid = 0;
    if (checkTracerPid(tpid)) {
        report.tracerDetected = true;
        report.tracerPid = tpid;
    }

    if (probeFridaPorts()) {
        report.portDetected = true;
    }

    if (checkWchanState()) {
        report.wchanSuspended = true;
    }

    return report;
}

bool FridaSentinel::scanProcMaps(std::string& outArtifact) {
    int fd = open("/proc/self/maps", O_RDONLY);
    if (fd < 0) {
        return false;
    }

    char buffer[4096];
    ssize_t bytesRead = 0;
    std::string line;

    const char* const signatures[] = {
        "frida-agent",
        "frida-gadget",
        "frida-server",
        "libgumjs",
        "libfrida",
        "substrate",
        "xposed",
        "edxposed",
        "linjector",
        "gum-js-loop"
    };

    while ((bytesRead = read(fd, buffer, sizeof(buffer) - 1)) > 0) {
        buffer[bytesRead] = '\0';
        for (const char* sig : signatures) {
            if (strstr(buffer, sig) != nullptr) {
                outArtifact = sig;
                close(fd);
                return true;
            }
        }
    }

    close(fd);
    return false;
}

bool FridaSentinel::checkTracerPid(uint32_t& outTracerPid) {
    int fd = open("/proc/self/status", O_RDONLY);
    if (fd < 0) {
        return false;
    }

    char buffer[2048];
    ssize_t bytesRead = read(fd, buffer, sizeof(buffer) - 1);
    close(fd);

    if (bytesRead <= 0) {
        return false;
    }
    buffer[bytesRead] = '\0';

    const char* tracerLine = strstr(buffer, "TracerPid:");
    if (!tracerLine) {
        return false;
    }

    int pid = 0;
    if (sscanf(tracerLine, "TracerPid:\t%d", &pid) == 1) {
        outTracerPid = static_cast<uint32_t>(pid);
        return (pid != 0);
    }

    return false;
}

bool FridaSentinel::probeFridaPorts() {
    const int ports[] = {27042, 27043};
    for (int port : ports) {
        int sock = socket(AF_INET, SOCK_STREAM, 0);
        if (sock < 0) continue;

        // Set non-blocking with timeout
        timeval timeout{};
        timeout.tv_sec = 0;
        timeout.tv_usec = 20000; // 20ms
        setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

        sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_port = htons(port);
        addr.sin_addr.s_addr = inet_addr("127.0.0.1");

        int ret = connect(sock, reinterpret_cast<sockaddr*>(&addr), sizeof(addr));
        close(sock);

        if (ret == 0) {
            return true; // Active Frida listening socket
        }
    }
    return false;
}

bool FridaSentinel::checkWchanState() {
    int fd = open("/proc/self/wchan", O_RDONLY);
    if (fd < 0) return false;

    char buffer[128];
    ssize_t n = read(fd, buffer, sizeof(buffer) - 1);
    close(fd);

    if (n > 0) {
        buffer[n] = '\0';
        if (strstr(buffer, "ptrace_stop") != nullptr || strstr(buffer, "do_signal_stop") != nullptr) {
            return true;
        }
    }
    return false;
}

} // namespace wci::security
