#include "VmBytecodeInterpreter.h"
#include "MemoryWiper.h"
#include <cstring>

namespace wci::vm {

VmBytecodeInterpreter::VmBytecodeInterpreter() {
    reset();
}

VmBytecodeInterpreter::~VmBytecodeInterpreter() {
    reset();
}

void VmBytecodeInterpreter::reset() {
    security::MemoryWiper::wipe(stack_.data(), stack_.size() * sizeof(uint64_t));
    security::MemoryWiper::wipe(registers_.data(), registers_.size() * sizeof(uint64_t));
    sp_ = 0;
}

void VmBytecodeInterpreter::push(uint64_t val) {
    if (sp_ < VM_STACK_CAPACITY) {
        stack_[sp_++] = val;
    }
}

uint64_t VmBytecodeInterpreter::pop() {
    if (sp_ > 0) {
        return stack_[--sp_];
    }
    return 0;
}

VmExecutionResult VmBytecodeInterpreter::execute(
    const std::vector<uint8_t>& bytecode,
    const std::vector<uint64_t>& inputSeeds
) {
    reset();
    for (size_t i = 0; i < inputSeeds.size() && i < VM_REGISTER_COUNT; ++i) {
        registers_[i] = inputSeeds[i];
    }

    size_t ip = 0;
    const size_t len = bytecode.size();
    bool halted = false;
    uint64_t exitCode = 0;

    while (ip < len && !halted) {
        uint8_t op = bytecode[ip++];
        switch (op) {
            case OP_NOP:
                break;

            case OP_PUSH_U64: {
                if (ip + 8 <= len) {
                    uint64_t val = 0;
                    std::memcpy(&val, &bytecode[ip], 8);
                    ip += 8;
                    push(val);
                } else {
                    halted = true;
                    exitCode = 1; // Unterminated operand
                }
                break;
            }

            case OP_POP: {
                pop();
                break;
            }

            case OP_ADD: {
                uint64_t b = pop();
                uint64_t a = pop();
                push(a + b);
                break;
            }

            case OP_SUB: {
                uint64_t b = pop();
                uint64_t a = pop();
                push(a - b);
                break;
            }

            case OP_XOR: {
                uint64_t b = pop();
                uint64_t a = pop();
                push(a ^ b);
                break;
            }

            case OP_ROL: {
                uint64_t shift = pop() & 63;
                uint64_t val = pop();
                push((val << shift) | (val >> (64 - shift)));
                break;
            }

            case OP_LOAD_REG: {
                if (ip < len) {
                    uint8_t regIdx = bytecode[ip++] % VM_REGISTER_COUNT;
                    push(registers_[regIdx]);
                }
                break;
            }

            case OP_STORE_REG: {
                if (ip < len) {
                    uint8_t regIdx = bytecode[ip++] % VM_REGISTER_COUNT;
                    registers_[regIdx] = pop();
                }
                break;
            }

            case OP_JMP: {
                if (ip + 2 <= len) {
                    uint16_t target = 0;
                    std::memcpy(&target, &bytecode[ip], 2);
                    if (target < len) {
                        ip = target;
                    } else {
                        halted = true;
                        exitCode = 2; // OOB Jump
                    }
                }
                break;
            }

            case OP_JZ: {
                if (ip + 2 <= len) {
                    uint16_t target = 0;
                    std::memcpy(&target, &bytecode[ip], 2);
                    ip += 2;
                    uint64_t cond = pop();
                    if (cond == 0 && target < len) {
                        ip = target;
                    }
                }
                break;
            }

            case OP_CMP_EQ: {
                uint64_t b = pop();
                uint64_t a = pop();
                push(a == b ? 1 : 0);
                break;
            }

            case OP_VERIFY_SIG: {
                uint64_t seed = pop();
                // Verifies campus main signature constant (0x574349_554E4E41 = 'WCI_UNNA')
                constexpr uint64_t CAMPUS_MAGIC = 0x574349554E4E414FULL;
                push((seed ^ 0xDEADBEEFCAFEBABEULL) == CAMPUS_MAGIC ? 1 : 0);
                break;
            }

            case OP_DERIVE_EXAM_KEY: {
                uint64_t timeVal = pop();
                uint64_t examVal = pop();
                uint64_t studentVal = pop();
                // 3-way Feistel non-linear mixing
                uint64_t mixed = (studentVal ^ 0x6A09E667F3BCC908ULL) * 6364136223846793005ULL;
                mixed ^= ((examVal << 17) | (examVal >> 47));
                mixed += (timeVal ^ 0xBB67AE8584CAA73BULL);
                push(mixed);
                break;
            }

            case OP_WIPE_REGISTERS: {
                security::MemoryWiper::wipe(registers_.data(), registers_.size() * sizeof(uint64_t));
                break;
            }

            case OP_HALT:
            default:
                halted = true;
                break;
        }
    }

    VmExecutionResult result;
    result.success = (exitCode == 0);
    result.exitCode = exitCode;
    result.topOfStack = (sp_ > 0) ? stack_[sp_ - 1] : 0;

    // Export derived 32-byte hash block
    result.outputPayload.resize(32);
    for (size_t i = 0; i < 4; ++i) {
        uint64_t word = (i < sp_) ? stack_[i] : (result.topOfStack ^ (i * 0x9E3779B97F4A7C15ULL));
        std::memcpy(&result.outputPayload[i * 8], &word, 8);
    }

    reset();
    return result;
}

VmExecutionResult VmBytecodeInterpreter::executeExamTokenDerivation(
    uint64_t studentUidHash,
    uint64_t examIdHash,
    uint64_t timestampSec
) {
    VmBytecodeInterpreter vm;
    std::vector<uint8_t> prog = {
        OP_LOAD_REG, 0x00, // Load studentUidHash
        OP_LOAD_REG, 0x01, // Load examIdHash
        OP_LOAD_REG, 0x02, // Load timestampSec
        OP_DERIVE_EXAM_KEY,
        OP_STORE_REG, 0x03, // Store derived seal in R3
        OP_LOAD_REG, 0x03,
        OP_HALT
    };

    std::vector<uint64_t> inputs = {studentUidHash, examIdHash, timestampSec};
    return vm.execute(prog, inputs);
}

} // namespace wci::vm
