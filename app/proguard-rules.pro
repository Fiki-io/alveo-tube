# Proguard rules for Alveo Tube
-keep class com.alveo.tube.core.model.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
