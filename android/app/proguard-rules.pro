# R8 rules for the release build.

# Rows exchanged with Supabase are (de)serialized by kotlinx.serialization through
# reflection-free generated serializers; keep them and their names intact.
-keep class br.vendas.app.data.supabase.** { *; }
-keepclassmembers class br.vendas.app.** {
    *** Companion;
}
-keepclasseswithmembers class br.vendas.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Type-safe navigation routes are serialized too.
-keep @kotlinx.serialization.Serializable class br.vendas.app.ui.navigation.** { *; }

# Ktor and OkHttp reference optional classes that are not on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
