# Add project specific ProGuard rules here.

# ── Use cases — R8 suspend/Result optimization fix ───────────────────────────
# R8 incorrectly optimizes suspend functions returning Result<T> (inline class, -BWLJW6A suffix).
# The state machine in $invoke$1 calls invoke-BWLJW6A which gets misoptimized → ClassCastException.
-keep class com.shade.app.domain.usecase.** { *; }
-keep class com.shade.app.crypto.** { *; }

# ── Kotlin Coroutines — R8 ClassCastException fix ───────────────────────────
-optimizations !class/merging/*
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory
-keep class kotlin.coroutines.jvm.internal.BaseContinuationImpl { *; }
-keep class * extends kotlin.coroutines.jvm.internal.BaseContinuationImpl { *; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclassmembers,allowobfuscation class * {
    *** invokeSuspend(java.lang.Object);
}

# ── Protobuf — R8 field name obfuscation fix ─────────────────────────────────
# Protobuf uses reflection to find fields by name (e.g. messageId_).
# Without this, minified builds crash with "Field X not found".
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep,allowobfuscation,allowshrinking class com.google.protobuf.** { *; }
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile