# WallCarousel release rules.
#
# kotlinx.serialization generates a serializer object for every @Serializable
# class. R8 must keep the generated members and the annotation that points a
# class to its serializer, otherwise state.json cannot be decoded.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.wallcarousel.app.**$$serializer { *; }
-keepclassmembers class com.wallcarousel.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.wallcarousel.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager instantiates workers by name.
-keep class com.wallcarousel.app.schedule.ApplyWallpaperWorker { <init>(...); }

# Room generates WorkDatabase_Impl and creates it through reflection; without
# its no-argument constructor WorkManager cannot start.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
