// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.lsparanoid) apply false
}

// Global build task for asserting strict zero web files, zero payment gateways, zero EMI schemas, zero dummy data
tasks.register<Exec>("verifyStaticGuardrails") {
  group = "verification"
  description = "Asserts zero web files, zero payment gateways, zero EMI schemas, zero dummy data, and single campus lock."
  commandLine("python3", "${rootDir}/scripts/verify_static_guardrails.py")
}
