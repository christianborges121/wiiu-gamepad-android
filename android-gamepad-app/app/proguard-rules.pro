# ProGuard / R8 Optimization & Keep Rules for CemuPad

# Jetpack Compose Rules
-keepattributes *Annotation*
-dontwarn androidx.compose.**

# Keep DSU Protocol Data Classes & Enums (serialized over network via reflection / binary mapping)
-keep class com.cemupad.dsu.** { *; }
-keepclassmembers class com.cemupad.dsu.** { *; }
-keep class com.cemupad.config.** { *; }
-keepclassmembers class com.cemupad.config.** { *; }

# Keep Network, Audio, and Video transport models & clients
-keep class com.cemupad.network.** { *; }
-keep class com.cemupad.video.** { *; }
-keep class com.cemupad.audio.** { *; }
-keep class com.cemupad.input.** { *; }

# Keep MediaCodec & AudioTrack hardware buffer structures
-keep class android.media.** { *; }

# Keep FileProvider for debug bundle sharing
-keep class androidx.core.content.FileProvider { *; }
