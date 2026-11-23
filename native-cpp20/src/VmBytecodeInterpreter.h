#ifndef WCI_VM_BYTECODE_INTERPRETER_H
#define WCI_VM_BYTECODE_INTERPRETER_H

#include <cstdint>
#include <cstddef>
#include <vector>
#include <array>

namespace wci::vm {

enum Opcode : uint8_t {
    OP_NOP               = 0x12,
    OP_PUSH_U64          = 0x2A,
    OP_POP               = 0x33,
    OP_ADD               = 0x48,
    OP_SUB               = 0x5F,
    OP_XOR               = 0x6E,
    OP_ROL               = 0x71,
    OP_LOAD_REG          = 0x85,
    OP_STORE_REG         = 0x9C,
    OP_JMP               = 0xA4,
    OP_JZ                = 0xB7,
    OP_CMP_EQ            = 0xCE,
    OP_VERIFY_SIG        = 0xD0,
    OP_DERIVE_EXAM_KEY   = 0xE6,
    OP_WIPE_REGISTERS    = 0xF3,
    OP_HALT              = 0xFA
};

constexpr size_t VM_STACK_CAPACITY = 256;
constexpr size_t VM_REGISTER_COUNT = 16;

struct VmExecutionResult {
    bool success;
    uint64_t exitCode;
    uint64_t topOfStack;
    std::vector<uint8_t> outputPayload;
};

class VmBytecodeInterpreter {
public:
    VmBytecodeInterpreter();
    ~VmBytecodeInterpreter();

    /**
     * Executes bytecode program through the stack VM.
     * @param bytecode Program instruction stream.
     * @param inputSeeds Variable registers initialization seeds.
     * @return Execution result with stack state and derived payload.
     */
    VmExecutionResult execute(
        const std::vector<uint8_t>& bytecode,
        const std::vector<uint64_t>& inputSeeds = {}
    );

    /**
     * Synthesizes and executes the proprietary WCI Campus Exam Token derivation program.
     */
    static VmExecutionResult executeExamTokenDerivation(
        uint64_t studentUidHash,
        uint64_t examIdHash,
        uint64_t timestampSec
    );

private:
    std::array<uint64_t, VM_STACK_CAPACITY> stack_{};
    size_t sp_{0};
    std::array<uint64_t, VM_REGISTER_COUNT> registers_{};

    void push(uint64_t val);
    uint64_t pop();
    void reset();
};

} // namespace wci::vm

#endif // WCI_VM_BYTECODE_INTERPRETER_H
