# Keep Media3 / ExoPlayer internals that are loaded reflectively.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep our model classes used by DataStore serialization if reflection is added later.
-keep class com.harsh.player.data.model.** { *; }

# Coil
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
