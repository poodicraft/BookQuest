# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
# Without this the class name in a stack trace is the obfuscated one.
-renamesourcefileattribute SourceFile

# Cloud Firestore maps documents onto model classes by reflection, matching
# field names. Renaming those fields does not fail the build; it silently
# writes documents with one-letter keys and reads nothing back, which is the
# kind of fault that only ever appears in a release build.
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keepclassmembers class com.poodicraft.bookquest.data.** {
    <init>();
    <fields>;
}
-keep class com.poodicraft.bookquest.data.** { *; }

# Firebase and Google Play services keep their own reflective surface.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Credential Manager reaches its providers reflectively.
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# Kotlin coroutines and the metadata reflection depends on.
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Broadcast receivers are named in the manifest, never called from code.
-keep class com.poodicraft.bookquest.data.ReminderReceiver { *; }
-keep class com.poodicraft.bookquest.data.BootReceiver { *; }
