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

# 保留所有 Application, Activity, Service, BroadcastReceiver, ContentProvider 的子类
# R8 通常能自动处理，但显式声明更保险
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class com.android.vending.licensing.ILicensingService

# 保留所有 View 的子类中带有特定参数类型的构造函数 (用于XML布局实例化)
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# 保留 Parcelable 实现类
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# 保留所有 R$* 内部类及其所有字段 (资源ID)
-keep class **.R$* {
    *;
}

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
# 通常由 AGP 和 Kotlin 插件自动处理，但如果遇到问题可以尝试添加
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keep class kotlin.coroutines.Continuation

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

# --- Wire (Protocol Buffers) ---
# (您在 app/build.gradle.kts 中使用了 libs.plugins.wire)
# Wire 生成的代码通常需要保留，特别是如果模型类被用于序列化或网络。
# Wire 插件本身可能提供 Proguard 规则，或者您需要参考其文档。
# 一般来说，生成的 Message 和 Enum 类需要保留。
-keep class com.squareup.wire.** { *; }
-keep interface com.squareup.wire.** { *; }
# 保留所有生成的 Wire Model 类及其字段和方法
# 您可能需要根据您的 protobuf 包名调整
# 例如，如果您的 .proto 文件包名是 com.example.protos
# -keep class com.example.protos.** { *; }
# 更通用的做法是保留所有 com.squareup.wire.Message 的子类
-keep public class * extends com.squareup.wire.Message {
    <fields>;
    <methods>;
}
-keep public class * extends com.squareup.wire.ProtoAdapter {
    <fields>;
    <methods>;
}
-keep public enum * extends com.squareup.wire.WireEnum {
    <fields>;
    <methods>;
}

# --- Tencent Bugly ---
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}
#===============================================================================
# 应用特定规则 (请根据您的代码添加)
#===============================================================================
