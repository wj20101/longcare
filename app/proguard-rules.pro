# Add project specific ProGuard rules here.
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

#===============================================================================
# Android Gradle Plugin 默认优化规则 (通常已通过 getDefaultProguardFile)
#===============================================================================
# -optimizationpasses 5 # R8 默认会进行多次优化，通常不需要手动设置
# -dontusemixedcaseclassnames # 允许混淆时使用大小写混合的类名，默认行为
# -dontskipnonpubliclibraryclasses # 不跳过非公共库类的处理，默认行为
# -dontpreverify # 预校验已不再需要，R8 会处理
# -verbose # 构建时输出详细日志，调试时有用，发布时可移除或注释
# -optimizations !code/simplification/arithmetic,!field/*,!class/merging/* # proguard-android-optimize.txt 会包含优化

#===============================================================================
# 混淆字典 (来自您提供的 proguard_keyword.txt)
#===============================================================================
# 使用您提供的字典进行名称混淆，可能让逆向更难一些
-obfuscationdictionary proguard_keyword.txt
-classobfuscationdictionary proguard_keyword.txt
-packageobfuscationdictionary proguard_keyword.txt # 混淆包名，更彻底但调试难度增加，谨慎使用

#================================셔츠===============================================
# 通用 Android 规则 (部分可能已在 android-optimize.txt 中)
#===============================================================================
-keepattributes Signature # 保留泛型签名，某些库（如 Gson, Jackson, Kotlinx Serialization）可能需要
-keepattributes InnerClasses # 保留内部类信息
-keepattributes EnclosingMethod # 保留匿名内部类指向外部方法的信息
-keepattributes *Annotation* # 保留注解信息，很多现代库依赖注解

# 保留 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留枚举类的 values() 和 valueOf() 方法
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

#===============================================================================
# Kotlin 相关规则 (部分可能由 kotlin-reflect 或 kotlinx.coroutines 自动处理)
#===============================================================================
# 保留所有被 @Keep 注解的类、方法和字段
-keep @androidx.annotation.Keep class * {*;}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}

#===============================================================================
# 第三方库规则
#===============================================================================

# --- Kotlinx Serialization ---
# (您在 app/build.gradle.kts 中使用了 libs.plugins.kotlinSerialization 和 libs.kotlinx.serialization.json)
-keepattributes Signature
-keepclassmembers class kotlinx.serialization.internal.* {
    *;
}
-keepclassmembers class **$$serializer { # 注意这里的 $$
    *;
}
-keep class **$$serializer { # 注意这里的 $$
    *;
}
-keepclassmembers class * { # 保留被 @Serializable 注解的类的成员
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.Transient <fields>;
}
-keepnames class * { # 保留被 @Serializable 注解的类名
    @kotlinx.serialization.Serializable <methods>;
}

# --- Tencent Bugly ---
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}

# --- AMap Location ---
# AMap location SDK calls back into Java from JNI via fixed class/member names.
# Renaming support/logging classes in release builds can trigger NoSuchMethodError
# during native library loading (for example com.amap.location.support.log.ALLog.d).
-keep class com.amap.api.location.** { *; }
-keep class com.amap.api.fence.** { *; }
-keep class com.loc.** { *; }
-keep class com.autonavi.aps.amapapi.model.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.amap.location.** { *; }
# Optional classes referenced by bundled third-party SDK code. App code does not
# call these classes directly; keep the suppression narrow so new missing classes
# still fail loudly in release builds.
-dontwarn com.amap.ams.gnss.GnssSoftLocator
-dontwarn net.jafama.FastMath

# --- ML Kit Face Detection ---
# Face detection relies on provider/component bootstrap plus bundled model/native glue.
-keep class com.google.mlkit.common.internal.MlKitInitProvider { *; }
-keep class com.google.mlkit.common.internal.MlKitComponentDiscoveryService { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar
-keep class com.google.mlkit.common.internal.CommonComponentRegistrar { *; }
-keep class com.google.mlkit.vision.common.internal.VisionCommonRegistrar { *; }
-keep class com.google.mlkit.vision.face.internal.FaceRegistrar { *; }
-keep class com.google.mlkit.vision.face.bundled.internal.** { *; }
-keepclassmembers class * extends com.google.android.gms.internal.mlkit_vision_face_bundled.zzuw {
    <fields>;
}
-dontwarn dalvik.system.VMStack

# --- QLZ device assessment SDK 1.3.0.2 ---
# Vendor-required rules. The SDK discovers several model and Activity classes
# reflectively, and its protobuf payload types must retain their generated APIs.
-keep class com.comm.* { *; }
-keep class com.comm.** { *; }
-keep class com.qiaolz.eco.app.protobuf.** { *; }
-keep class com.evenmed.mode.** { *; }
-keep class com.evenmed.util.** { *; }
-keep class com.falth.data.* { *; }
-keep class com.falth.data.** { *; }
-keep class com.evenmed.sdk.call.** { *; }
-keep class com.evenmed.sdk.chekpage.TreatmentBaseAct { *; }

#===============================================================================
# 应用特定规则 (请根据您的代码添加)
#===============================================================================
