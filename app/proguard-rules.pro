# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.spaceclipboard.app.**$$serializer { *; }
-keepclassmembers class com.spaceclipboard.app.** { *** Companion; }
-keepclasseswithmembers class com.spaceclipboard.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor / CIO
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Room
-keep class * extends androidx.room.RoomDatabase
