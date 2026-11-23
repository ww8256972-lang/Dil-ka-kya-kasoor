package com.wci.sandbox.runtime

import java.util.regex.Pattern

/**
 * AstBlacklistGuard (S-38, S-42)
 * Pre-Execution Regex & Token Blacklist ensuring student code cannot escape the sandbox:
 * Blocks reflection exploits, filesystem escapes, subprocess spawning, and raw sockets.
 */
object AstBlacklistGuard {

    private val BANNED_PATTERNS = listOf(
        Pattern.compile("\\b(__import__|__subclasses__|__builtins__|__class__)\\b"),
        Pattern.compile("\\b(eval|exec|compile)\\b"),
        Pattern.compile("\\b(os\\.system|os\\.popen|subprocess\\.|shutil\\.)\\b"),
        Pattern.compile("\\b(open\\s*\\(['\"][^'\"]*etc|open\\s*\\(['\"][^'\"]*sys)\\b"),
        Pattern.compile("\\b(socket\\.|urllib\\.|requests\\.|http\\.client)\\b"),
        Pattern.compile("\\b(ctypes\\.|sys\\.modules)\\b")
    )

    data class ScanResult(
        val isSafe: Boolean,
        val violationMessage: String? = null
    )

    fun scanCode(code: String): ScanResult {
        for (pattern in BANNED_PATTERNS) {
            val matcher = pattern.matcher(code)
            if (matcher.find()) {
                val matched = matcher.group()
                return ScanResult(
                    isSafe = false,
                    violationMessage = "SECURITY_VIOLATION: Disallowed keyword or module detected: '$matched'. Sandbox execution prohibited."
                )
            }
        }
        return ScanResult(isSafe = true)
    }
}
