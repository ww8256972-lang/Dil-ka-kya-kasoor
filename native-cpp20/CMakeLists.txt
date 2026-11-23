cmake_minimum_required(VERSION 3.22.1)
project("wci_sentinel" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Hardened Security & Anti-Disassembly Compiler Flags (S-14, S-48)
add_compile_options(
    -Wall
    -Wextra
    -Werror
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -ffunction-sections
    -fdata-sections
    -fstack-protector-strong
    -fno-rtti
    -fno-exceptions
    -D_FORTIFY_SOURCE=2
    -O2
)

# Linker flags for symbol stripping and RELRO hardening
add_link_options(
    -Wl,--gc-sections
    -Wl,-z,relro
    -Wl,-z,now
    -Wl,-z,noexecstack
)

add_library(wci_sentinel SHARED
    src/JniVaultBridge.cpp
    src/VmBytecodeInterpreter.cpp
    src/FridaSentinel.cpp
    src/KernelIntegrityDaemon.cpp
    src/SeccompSandbox.cpp
    src/SandboxMemoryCap.cpp
    src/PqcKeyExchange.cpp
    src/MemoryWiper.cpp
)

target_include_directories(wci_sentinel PRIVATE
    src
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(wci_sentinel
    ${log-lib}
    ${android-lib}
)
