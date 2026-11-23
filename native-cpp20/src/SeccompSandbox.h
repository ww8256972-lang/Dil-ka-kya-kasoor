#ifndef WCI_SECCOMP_SANDBOX_H
#define WCI_SECCOMP_SANDBOX_H

#include <cstdint>

namespace wci::security {

class SeccompSandbox {
public:
    /**
     * Checks if Linux kernel SELinux is actively in Enforcing mode.
     * Returns true if enforcing (1), false if permissive (0) or disabled.
     */
    static bool isSELinuxEnforcing();

    /**
     * Enforces PR_SET_NO_NEW_PRIVS to disallow execve privilege elevation.
     */
    static bool enableNoNewPrivs();

    /**
     * Installs Seccomp-BPF filter to block ptrace and process_vm_readv/writev.
     */
    static bool installExamSandboxFilter();
};

} // namespace wci::security

#endif // WCI_SECCOMP_SANDBOX_H
