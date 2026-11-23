# Keep JNI native methods and classes for libwci_sentinel.so
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.wci.nativecxx.** { *; }
-keepclassmembers class com.wci.nativecxx.** {
    native <methods>;
    public *;
}
