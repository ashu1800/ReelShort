# ReelShort / ShortLink ProGuard rules
# L7: release 构建开启混淆和资源压缩

# --- Kotlin ---
-dontwarn kotlin.**
-keepclassmembers class kotlin.Metadata { *; }

# --- Compose ---
# Compose runtime 和 compiler 生成的类不应被混淆
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# --- Coil (图片加载) ---
-dontwarn coil.**

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- Media3 / ExoPlayer ---
-dontwarn androidx.media3.**

# --- Kotlinx Serialization (app-core DTO) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.reelshort.**$$serializer { *; }
-keepclassmembers class com.reelshort.** {
    *** Companion;
}
-keepclasseswithmembers class com.reelshort.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- app-core 模型的序列化类保持不变（OkHttp JSON 解析依赖字段名） ---
-keep class com.reelshort.app.** { *; }

# --- AndroidX Security Crypto ---
-keep class androidx.security.crypto.** { *; }
