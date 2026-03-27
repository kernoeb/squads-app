# Squads ProGuard rules

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep JSON parsing (org.json is part of Android SDK, but keep model classes)
-keepclassmembers class com.squads.app.data.** { *; }

# Jsoup HTML parser
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Navigation 3 type-safe routes (kotlinx.serialization)
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keep class com.squads.app.ui.navigation.** { *; }
