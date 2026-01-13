# Repackages optimized classes into %packageName%.repacked package
# in resulting AIX. Repackaging is necessary to avoid clashes with
# the other extensions that might be using same libraries as you.
-repackageclasses %packageName%.repacked

-android
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-useuniqueclassmembernames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

# Supabase
-keep class io.github.jan.supabase.** { *; }
-keepclassmembers class io.github.jan.supabase.** { *; }

# Ktor
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }

# Kotlin Serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Kotlinx Coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# Kotlin Reflection
-keep class kotlin.reflect.** { *; }
-keepclassmembers class kotlin.reflect.** { *; }

# Kotlinx DateTime
-keep class kotlinx.datetime.** { *; }

# Okio
-keep class okio.** { *; }

# Kotlin Standard Library (descriptor classes)
-keep class kotlin.** { *; }
-keep class kotlinx.io.** { *; }

# Kotlinx Collections Immutable
-keep class kotlinx.collections.immutable.** { *; }

# Settings/Preferences
-keep class com.russhwolf.settings.** { *; }

# SLF4J Logging
-keep class org.slf4j.** { *; }

# Typesafe Config
-keep class com.typesafe.config.** { *; }

# Warnings suppression
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn java.beans.**
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.**
-dontwarn com.sun.nio.**
-dontwarn java.lang.management.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn kotlin.ExperimentalContextParameters
-dontwarn twitter4j.**
-dontwarn java.net.UnixDomainSocketAddress
-dontwarn com.sun.nio.file.SensitivityWatchEventModifier
-dontwarn kotlinx.coroutines.debug.ByteBuddyDynamicAttach
-dontwarn java.lang.ProcessBuilder$RedirectPipeImpl
-dontwarn org.fusesource.jansi.**

# Ignore warnings about unresolved references
-dontwarn io.github.jan.supabase.**
-ignorewarnings

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}


