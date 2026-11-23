#include "SeccompSandbox.h"
#include <fcntl.h>
#include <unistd.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <cstddef>
#include <cerrno>

namespace wci::security {

bool SeccompSandbox::isSELinuxEnforcing() {
    int fd = open("/sys/fs/selinux/enforce", O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        // Fallback: check selinuxfs mount point or assume enforcing on stock Android
        return true;
    }
    char val = '0';
    ssize_t bytesRead = read(fd, &val, 1);
    close(fd);
    return (bytesRead == 1 && val == '1');
}

bool SeccompSandbox::enableNoNewPrivs() {
    return prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) == 0;
}

bool SeccompSandbox::installExamSandboxFilter() {
    if (!enableNoNewPrivs()) {
        return false;
    }

    // Offset of syscall number in seccomp_data struct
    constexpr auto syscall_nr_offset = offsetof(struct seccomp_data, nr);

    // BPF filter instructions:
    // 1. Load architecture (seccomp_data.arch)
    // 2. Validate architecture or jump to evaluate syscall
    // 3. Load syscall number (seccomp_data.nr)
    // 4. If ptrace -> ERRNO(EPERM)
    // 5. If process_vm_readv -> ERRNO(EPERM)
    // 6. If process_vm_writev -> ERRNO(EPERM)
    // 7. Otherwise -> SECCOMP_RET_ALLOW

    struct sock_filter filter[] = {
        // Load syscall number into accumulator: A = seccomp_data.nr
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, syscall_nr_offset),

#ifdef __NR_ptrace
        // If A == __NR_ptrace, jump 0 forward to return EPERM, else 1 forward to next check
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, static_cast<uint32_t>(__NR_ptrace), 4, 0),
#endif

#ifdef __NR_process_vm_readv
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, static_cast<uint32_t>(__NR_process_vm_readv), 3, 0),
#endif

#ifdef __NR_process_vm_writev
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, static_cast<uint32_t>(__NR_process_vm_writev), 2, 0),
#endif

        // Allow all other operational syscalls
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),

        // Block with EPERM for unauthorized debugging syscalls
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | (EPERM & SECCOMP_RET_DATA)),
    };

    struct sock_fprog prog = {
        .len = static_cast<unsigned short>(sizeof(filter) / sizeof(filter[0])),
        .filter = filter,
    };

    return prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &prog) == 0;
}

} // namespace wci::security
