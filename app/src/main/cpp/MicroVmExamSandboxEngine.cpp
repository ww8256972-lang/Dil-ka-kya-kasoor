#include <jni.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <linux/seccomp.h>
#include <linux/filter.h>
#include <linux/audit.h>
#include <cstddef>
#include <cstdint>

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_waqar_institute_sandbox_MicroVmExamSandboxManager_nativeEnterMicroVmExamSandbox(
    JNIEnv* /* env */,
    jobject /* thiz */) {
    // 1. Block privilege escalation inside the exam sandbox execution thread
    prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0);

    // 2. Block memory core dumps and ptrace attachment to the exam memory space
    prctl(PR_SET_DUMPABLE, 0, 0, 0, 0);

    // 3. Enforce zero core-file size limit (RLIMIT_CORE = 0)
    struct rlimit coreLimit{0, 0};
    setrlimit(RLIMIT_CORE, &coreLimit);

    // 4. Install Kernel seccomp-bpf syscall filter arming anti-ptrace & process memory snooping defenses
    struct sock_filter filter[] = {
        // Load syscall number into BPF accumulator
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, (uint32_t)offsetof(struct seccomp_data, nr)),
#ifdef __NR_ptrace
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (uint32_t)__NR_ptrace, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP),
#endif
#ifdef __NR_process_vm_readv
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (uint32_t)__NR_process_vm_readv, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP),
#endif
#ifdef __NR_process_vm_writev
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (uint32_t)__NR_process_vm_writev, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP),
#endif
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
    };
    struct sock_fprog prog = {
        .len = (unsigned short)(sizeof(filter) / sizeof(filter[0])),
        .filter = filter,
    };
    // Non-fatal if kernel or Android emulator seccomp is restricted
    prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &prog);

    return JNI_TRUE;
}
