# TV
-keep class androidx.leanback.widget.** { *; }

-keep class com.fongmi.android.tv.bean.** { *; }

-keep class com.fongmi.android.tv.ui.activity.WebActivity {
  public static boolean openWeb(java.lang.String);
  public static java.lang.String getUserAgent();
}

-keep class androidx.core.content.FileProvider {
  public static android.net.Uri getUriForFile(android.content.Context, java.lang.String, java.io.File);
}

# Gson
-keep class com.google.gson.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okio.** { *; }
-keep class okhttp3.** { *; }

# SLF4J
-keep class org.slf4j.** { *; }

# Kotlin
-keep class kotlin.** { *; }

# JUPnP
-dontwarn org.jupnp.**
-keep class org.jupnp.** { *; }

# Nano
-keep class fi.iki.elonen.** { *; }

# NewPipeExtractor
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.schabi.newpipe.extractor.services.youtube.protos.** { *; }
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# Sardine
-keep class com.thegrizzlylabs.sardineandroid.** { *; }

# TVBus
-keep class com.tvbus.engine.** { *; }

# Zxing QRCode ABI
-keep class com.google.zxing.* { *; }
-keep class com.google.zxing.common.BitMatrix { *; }
-keep class com.google.zxing.common.HybridBinarizer { *; }
-keep class com.google.zxing.qrcode.QRCodeReader { *; }
-keep class com.google.zxing.qrcode.QRCodeWriter { *; }
-keep class com.google.zxing.qrcode.decoder.ErrorCorrectionLevel { *; }

# Media3 视频后处理工厂（BugFix-261030-0001）
# exoplayer 模块不直接依赖 effect 模块，运行期通过反射创建
# androidx.media3.effect.DefaultVideoFrameProcessor$Factory$Builder，
# 无 keep 规则时 R8 会移除其构造函数，release 包播放报
# NoSuchMethodException -> ERROR_CODE_FAILED_RUNTIME_CHECK（提示 Failed Runtime Check）。
-keep class androidx.media3.effect.DefaultVideoFrameProcessor$Factory$Builder {
    public <init>();
}

