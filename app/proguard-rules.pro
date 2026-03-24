# Squads ProGuard rules

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep JSON parsing (org.json is part of Android SDK, but keep model classes)
-keepclassmembers class com.squads.app.data.** { *; }

# Jsoup HTML parser
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
