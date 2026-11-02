# ProGuard / R8 rules for FinTrack App

# Keep model classes & Gson SerializedName fields
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.example.fintrack.app.**.remote.*Request { *; }
-keep class com.example.fintrack.app.**.remote.*Response { *; }
-keep class com.example.fintrack.app.**.remote.*Dto { *; }

# OkHttp & Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
