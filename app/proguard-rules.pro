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
# 番茄钟阶段以 enum.name() 持久化在 SharedPreferences 里。
# R8 默认会把枚举常量改名（FOCUS -> d），那样旧版本存下的 "FOCUS"/"BREAK"
# 在新包里就解析不出来，阶段会回退成专注、跨版本状态全乱。
-keep class com.yuichi39soft.waterreminder.PomodoroPhase { *; }