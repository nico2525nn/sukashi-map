# Sukashi Map ProGuard / R8 rules
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class jp.nico2525.sukashimap.model.** { *; }
-keep class jp.nico2525.sukashimap.data.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepnames class <2>$$serializer {
    static <2>$$serializer INSTANCE;
}
