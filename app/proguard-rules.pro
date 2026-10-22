# R8 / ProGuard rules for release builds.
# Room, Hilt, Compose, WorkManager and Glance ship their own consumer rules.

# Keep readable stack traces
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Classes the system instantiates by name (receivers, activities, widget, worker)
-keep class com.kARThik.tasks.notifications.** { *; }
-keep class com.kARThik.tasks.widget.** { *; }

# Room entities / converters
-keep class com.kARThik.tasks.data.local.** { *; }

# Glance widget receiver
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

-dontwarn org.jetbrains.annotations.**
