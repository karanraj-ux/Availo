-keep class com.google.errorprone.annotations.** { *; }
-dontwarn com.google.errorprone.annotations.**

# Keep all our custom classes
-keep class com.example.** { *; }

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# Keep ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }

# Keep WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Keep Moshi
-keep class com.squareup.moshi.** { *; }
-keep class * {
    @com.squareup.moshi.JsonClass <fields>;
}

# Keep Retrofit
-keep class retrofit2.** { *; }

# Keep SQLCipher and Security
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keep class androidx.sqlite.db.** { *; }
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }

# Prevent missing class warnings
-dontwarn kotlinx.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn com.squareup.moshi.**

# Prevent missing class warnings from libraries (Tink, Joda, Ktor)
-dontwarn com.google.api.client.**
-dontwarn java.lang.management.**
-dontwarn org.joda.time.**
-dontwarn io.ktor.**
-dontwarn com.google.crypto.tink.**

