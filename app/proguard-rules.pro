# Keep kotlinx.serialization classes (they're accessed via reflection/generated code)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.matejgroombridge.readinglist.**$$serializer { *; }
-keepclassmembers class dev.matejgroombridge.readinglist.** {
    *** Companion;
}
-keepclasseswithmembers class dev.matejgroombridge.readinglist.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor + OkHttp reference optional JVM-only classes (SLF4J, JMX, Conscrypt
# etc.) that don't exist on Android. They're never loaded at runtime, so
# R8 only needs telling not to fail on the missing references.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn io.ktor.utils.io.jvm.nio.**
