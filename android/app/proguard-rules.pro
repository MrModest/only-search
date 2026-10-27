# JNI: native methods are kept by proguard-android-optimize.txt. The Swift bridge never looks up Kotlin
# classes, methods or fields by name (no FindClass/GetMethodID/GetFieldID), so nothing else is needed for it.

# Used only by androidTest (SoulverTest), which runs against this minified release build.
-keepclassmembers class app.onlysearch.search.Soulver {
    long getInitMillis();
    boolean initBlocking(android.content.Context);
    java.lang.String evalBlocking(java.lang.String);
    void setRatesBlocking(java.lang.String);
    java.lang.String rateLogBlocking();
}

# The androidTest APK reuses these classes from the app APK (it only ships what the app lacks).
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
