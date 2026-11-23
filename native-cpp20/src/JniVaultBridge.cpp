#include <jni.h>
#include <vector>
#include <cstring>
#include "FridaSentinel.h"
#include "KernelIntegrityDaemon.h"
#include "MemoryWiper.h"
#include "SeccompSandbox.h"
#include "SandboxMemoryCap.h"
#include "VmBytecodeInterpreter.h"
#include "PqcKeyExchange.h"

using namespace wci::security;
using namespace wci::vm;
using namespace wci::crypto;

static jboolean JNICALL native_isTampered(JNIEnv* /*env*/, jclass /*clazz*/) {
    // 1. Check Frida / TracerPid / ptrace debugger
    if (FridaSentinel::isDebuggerOrFridaAttached()) {
        return JNI_TRUE;
    }

    // 2. Check ELF memory segment integrity
    if (!KernelIntegrityDaemon::verifySegmentCrc32()) {
        return JNI_TRUE;
    }

    // 3. Verify SELinux mode
    if (!SeccompSandbox::isSELinuxEnforcing()) {
        // Warning: SELinux disabled / permissive
        return JNI_TRUE;
    }

    return JNI_FALSE;
}

static void JNICALL native_wipeMemory(JNIEnv* env, jclass /*clazz*/, jbyteArray buffer) {
    if (!buffer) return;
    jsize len = env->GetArrayLength(buffer);
    if (len <= 0) return;

    jbyte* bytes = env->GetByteArrayElements(buffer, nullptr);
    if (bytes) {
        MemoryWiper::wipe(bytes, static_cast<size_t>(len));
        env->ReleaseByteArrayElements(buffer, bytes, 0);
    }
}

static jboolean JNICALL native_enforceSandboxLimits(
    JNIEnv* /*env*/, jclass /*clazz*/,
    jlong maxVirtualBytes, jlong maxStackBytes
) {
    bool okVm = SandboxMemoryCap::enforceVirtualMemoryLimit(static_cast<uint64_t>(maxVirtualBytes));
    bool okStack = SandboxMemoryCap::enforceStackLimit(static_cast<uint64_t>(maxStackBytes));
    return (okVm && okStack) ? JNI_TRUE : JNI_FALSE;
}

static jboolean JNICALL native_isSELinuxEnforcing(JNIEnv* /*env*/, jclass /*clazz*/) {
    return SeccompSandbox::isSELinuxEnforcing() ? JNI_TRUE : JNI_FALSE;
}

static jboolean JNICALL native_installSeccompFilter(JNIEnv* /*env*/, jclass /*clazz*/) {
    return SeccompSandbox::installExamSandboxFilter() ? JNI_TRUE : JNI_FALSE;
}

static jbyteArray JNICALL native_deriveExamSeal(
    JNIEnv* env, jclass /*clazz*/,
    jlong studentUidHash, jlong examIdHash, jlong timestampSec
) {
    VmExecutionResult res = VmBytecodeInterpreter::executeExamTokenDerivation(
        static_cast<uint64_t>(studentUidHash),
        static_cast<uint64_t>(examIdHash),
        static_cast<uint64_t>(timestampSec)
    );

    jbyteArray outArray = env->NewByteArray(static_cast<jsize>(res.outputPayload.size()));
    if (outArray && !res.outputPayload.empty()) {
        env->SetByteArrayRegion(
            outArray, 0,
            static_cast<jsize>(res.outputPayload.size()),
            reinterpret_cast<const jbyte*>(res.outputPayload.data())
        );
    }
    return outArray;
}

static jbyteArray JNICALL native_unmaskConfigKey(
    JNIEnv* env, jclass /*clazz*/,
    jbyteArray maskedKey, jbyteArray mask
) {
    if (!maskedKey || !mask) return nullptr;
    jsize len = env->GetArrayLength(maskedKey);
    jsize maskLen = env->GetArrayLength(mask);
    if (len <= 0 || maskLen <= 0) return nullptr;

    jbyte* keyBytes = env->GetByteArrayElements(maskedKey, nullptr);
    jbyte* maskBytes = env->GetByteArrayElements(mask, nullptr);

    std::vector<uint8_t> unmasked(len);
    for (jsize i = 0; i < len; ++i) {
        unmasked[i] = static_cast<uint8_t>(keyBytes[i] ^ maskBytes[i % maskLen]);
    }

    env->ReleaseByteArrayElements(maskedKey, keyBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(mask, maskBytes, JNI_ABORT);

    jbyteArray outArray = env->NewByteArray(len);
    if (outArray) {
        env->SetByteArrayRegion(outArray, 0, len, reinterpret_cast<const jbyte*>(unmasked.data()));
    }
    MemoryWiper::wipe(unmasked.data(), unmasked.size());
    return outArray;
}

// Table of native methods to dynamically register
static const JNINativeMethod gMethods[] = {
    {"isTampered", "()Z", (void*)native_isTampered},
    {"wipeMemory", "([B)V", (void*)native_wipeMemory},
    {"enforceSandboxLimits", "(JJ)Z", (void*)native_enforceSandboxLimits},
    {"isSELinuxEnforcing", "()Z", (void*)native_isSELinuxEnforcing},
    {"installSeccompFilter", "()Z", (void*)native_installSeccompFilter},
    {"deriveExamSeal", "(JJJ)[B", (void*)native_deriveExamSeal},
    {"unmaskConfigKey", "([B[B)[B", (void*)native_unmaskConfigKey}
};

static const char* const kClassName = "com/wci/nativecxx/WciNativeSentinel";

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass(kClassName);
    if (!clazz) {
        return JNI_ERR;
    }

    if (env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(gMethods[0])) < 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
