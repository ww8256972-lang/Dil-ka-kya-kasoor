import java.io.File

plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.wci.nativecxx"
  compileSdk { version = release(36) { minorApiLevel = 1 } }
  ndkVersion = "27.0.12077973"

  defaultConfig {
    minSdk = 26
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")

    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
    }

    externalNativeBuild {
      cmake {
        cppFlags("-std=c++20 -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections -fstack-protector-strong -O2")
        arguments(
          "-DANDROID_STL=c++_shared"
        )
      }
    }
  }

  val sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME") ?: ""
  val ndkHome = System.getenv("ANDROID_NDK_HOME") ?: System.getenv("ANDROID_NDK_ROOT") ?: ""
  val hasNdkInstalled = (sdkRoot.isNotEmpty() && File(sdkRoot, "ndk").exists()) ||
    (sdkRoot.isNotEmpty() && File(sdkRoot, "ndk-bundle").exists()) ||
    (ndkHome.isNotEmpty() && File(ndkHome).exists())
  val enableNativeCxx = (project.findProperty("enableNativeCxxBuild")?.toString() ?: "true") == "true"
  if (enableNativeCxx && hasNdkInstalled) {
    externalNativeBuild {
      cmake {
        path = file("CMakeLists.txt")
        version = "3.22.1"
      }
    }
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
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
}
