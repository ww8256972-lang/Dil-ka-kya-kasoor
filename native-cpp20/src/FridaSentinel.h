#ifndef WCI_FRIDA_SENTINEL_H
#define WCI_FRIDA_SENTINEL_H

#include <cstdint>
#include <string>

namespace wci::security {

struct ThreatReport {
    bool fridaDetected = false;
    bool tracerDetected = false;
    bool portDetected = false;
    bool wchanSuspended = false;
    uint32_t tracerPid = 0;
    std::string detectedArtifact;

    bool isCompromised() const {
        return fridaDetected || tracerDetected || portDetected || wchanSuspended;
    }
};

class FridaSentinel {
public:
    /**
     * Executes full suite of runtime tamper & instrumentation checks.
     */
    static ThreatReport scanForTampering();

    /**
     * Checks /proc/self/maps for injected frida, xposed, or substrate so artifacts.
     */
    static bool scanProcMaps(std::string& outArtifact);

    /**
     * Reads /proc/self/status to verify TracerPid == 0.
     */
    static bool checkTracerPid(uint32_t& outTracerPid);

    /**
     * Attempts a non-blocking TCP connect to default Frida server ports (27042, 27043).
     */
    static bool probeFridaPorts();

    /**
     * Reads /proc/self/wchan for debugger wait states.
     */
    static bool checkWchanState();
};

} // namespace wci::security

#endif // WCI_FRIDA_SENTINEL_H
