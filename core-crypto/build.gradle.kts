plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.wci.core.crypto"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 24
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  // Tink AEAD & Streaming Vault (L-01)
  implementation(libs.google.tink.android)
  // SQLCipher for Android (L-02)
  implementation(libs.sqlcipher.android)
  implementation(libs.androidx.sqlite.ktx)
  // Argon2kt memory-hard hasher (L-04)
  implementation(libs.argon2kt)
  // BouncyCastle X.509 and Ed25519 (L-05)
  implementation(libs.bouncycastle.prov)
  // AndroidX Biometric StrongBox gate (L-07)
  implementation(libs.androidx.biometric)
  // Nimbus JOSE + JWT for RFC 9449 DPoP (L-09)
  implementation(libs.nimbus.jose.jwt)
  // Conscrypt TLS 1.3 (L-10)
  implementation(libs.conscrypt.android)
  // AndroidX Security Crypto
  implementation(libs.androidx.security.crypto)

  // Firebase integration for App Check & Lockdown listener
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.appcheck.playintegrity)

  // RASP and Root detection
  implementation(libs.scottyab.rootbeer)
  implementation(libs.talsec.freerasp)
  implementation(libs.appmattus.ct)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
}
