-dontoptimize
-dontobfuscate

-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn coil3.PlatformContext
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-keep class !androidx.** { *; }
