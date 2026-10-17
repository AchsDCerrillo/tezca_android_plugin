# Project-specific R8 rules. Libraries used by the templates (Hilt, Room, kotlinx-serialization,
# Retrofit/OkHttp, Ktor, Navigation 3) ship their own consumer rules.

# Navigation 3 keys are restored from saved state by their serializer; keep @Serializable keys.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
