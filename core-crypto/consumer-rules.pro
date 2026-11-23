# Proguard rules for core-crypto
-keep class com.wci.core.crypto.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class com.nimbusds.** { *; }
-dontwarn com.nimbusds.**
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class com.google.crypto.tink.** { *; }
-keep class com.lambdapioneer.argon2kt.** { *; }
