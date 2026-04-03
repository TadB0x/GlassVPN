-keep class com.glassvpn.app.** { *; }
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-dontwarn libv2ray.**
-dontwarn go.**

# Xray / LibXray
-keep class com.v2ray.** { *; }
-keep class libXray.** { *; }
-dontwarn com.v2ray.**
-dontwarn libXray.**

# tun2socks
-keep class tun2socks.** { *; }
-dontwarn tun2socks.**

# Vico chart library
-keep class com.patrykandpatrick.vico.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
