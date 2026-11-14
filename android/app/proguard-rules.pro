# ProGuard / R8 rules for FinTrack App

# Keep Gson SerializedName fields and model signatures
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep all DTOs and Data Models across feature packages
-keep class com.example.fintrack.app.**.remote.** { *; }
-keep class com.example.fintrack.app.**.model.** { *; }
-keep class com.example.fintrack.app.finance.domain.** { *; }
-keep class com.example.fintrack.app.finance.data.FinanceFileDto { *; }
-keep class com.example.fintrack.app.finance.data.FinanceFileDto$* { *; }
-keep class com.example.fintrack.app.core.network.** { *; }
-keep class com.example.fintrack.app.core.session.** { *; }

# Keep Retrofit Service Interfaces
-keep interface com.example.fintrack.app.**.remote.*ApiService { *; }

# OkHttp & Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ML Kit & CameraX
-dontwarn com.google.mlkit.**
-dontwarn androidx.camera.**
