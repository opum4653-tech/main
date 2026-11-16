# ProGuard / R8 Obfuscator and Optimization Rules
-repackageclasses com.andorid.ninmods.internal
-allowaccessmodification

# Optimization passes
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Strip debug attributes
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Preserve Android Entry Points and Components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.lifecycle.ViewModel

# Preserve ViewBinding classes
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
    public static * bind(...);
    public * getRoot();
}

# Preserve Native and Reflection calls if any
-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Kotlin intrinsics and metadata safe
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn com.topjohnwu.**
-dontwarn org.jetbrains.annotations.**
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod
