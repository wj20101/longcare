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

#####方法名等混淆指定配置
-obfuscationdictionary  ./proguard_keyword.txt
#####类名混淆指定配置
-classobfuscationdictionary  ./proguard_keyword.txt
#####包名混淆指定配置
-packageobfuscationdictionary  ./proguard_keyword.txt

#指定压缩级别
-optimizationpasses 5

#不跳过非公共的库的类成员
#-dontskipnonpubliclibraryclassmembers

#混淆时采用算法
-optimizations  !code/simplification/arthhmetric,!field/*,!class/merging/*

#把混淆类中的方法名也混淆了
#-useuniqueclassmembernames

#优化时允许访问并修改有修饰符的类和类成员
-allowaccessmodification

#将文件来源重命名为"SourceFile"字符串
-renamesourcefileattribute SourceFile

#保留行号
-keepattributes SourceFile,LineNumberTable

#保持泛型
-keepattributes Signature

# 保留所有四大组件
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment
-keep class androidx.core.app.CoreComponentFactory { *; }
# 显式保留所有序列化成员。可序列化接口只是一个标记接口，因此不会保存它们。
-keep public class * implements java.io.Serializable {*;}
-keepclassmembers class * implements java.io.Serializable {
   static final long serialVersionUID;
   private static final java.io.ObjectStreamField[]   serialPersistentFields;
   private void writeObject(java.io.ObjectOutputStream);
   private void readObject(java.io.ObjectInputStream);
   java.lang.Object writeReplace();
   java.lang.Object readResolve();
}

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# Keep custom view constructors for XML inflation
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public <init>(android.content.Context, android.util.AttributeSet, int, int);
}

# Keep JavaScript interfaces for WebView
# Ensure any class with methods annotated with @JavascriptInterface is kept
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Keep the JavascriptInterface annotation itself
-keepattributes JavascriptInterface

-keep class kotlin.** { *; }
-dontwarn kotlin.**
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}
-keep class kotlinx.coroutines.** {*;}

# 保留所有 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保持实现了Parcelable的类名不变
-keep class * implements android.os.Parcelable{
    public static final android.os.Parcelable$Creator *;
}

# Keep all @Keep annotated classes and their members
-keep @androidx.annotation.Keep class * {*;}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}

# 保持异常信息
-keepattributes Exceptions

# 反射支持
-keepclassmembers class * {
    public ** getGenericSuperclass();
}