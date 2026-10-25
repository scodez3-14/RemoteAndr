# RemotePhone ProGuard Rules

# Firebase
-keepattributes Signature
-keepattributes *Annotation*

# Firebase Realtime Database models
-keepclassmembers class com.remotephone.data.model.** {
    *;
}

# WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    static ** INSTANCE;
}
-keepclassmembers class <1> {
    static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Coroutines
-dontwarn kotlinx.coroutines.**

# Keep service classes
-keep class com.remotephone.service.** { *; }
