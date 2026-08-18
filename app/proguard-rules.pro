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

# Keep Db and Data Models used by the application
-keep class com.sohan.diutransportschedule.db.** { *; }
-keep class com.sohan.diutransportschedule.data.** { *; }

# Keep Notification Models used in schedule handling
-keep class com.sohan.diutransportschedule.notifications.** { *; }

# Keep ui Home package models that might be used
-keep class com.sohan.diutransportschedule.ui.home.** { *; }