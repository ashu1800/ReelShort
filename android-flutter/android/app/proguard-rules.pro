# R8 rules for the optimised release build.
#
# Flutter's Gradle plugin ships consumer rules for the engine and embedding; these entries cover the
# plugin surface this app actually uses plus the annotation metadata R8 would otherwise strip.

# Plugin classes are discovered through the generated registrant and, for some plugins, JNI.
-keep class io.flutter.plugins.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class com.it_nomads.fluttersecurestorage.** { *; }
-dontwarn com.it_nomads.fluttersecurestorage.**

# Keep generic signatures and annotations used by reflection-based plugin setup.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
