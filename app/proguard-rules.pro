# Reglas de Ofuscacion y Optimizacion (ProGuard/R8)

-repackageclasses ''
-flattenpackagehierarchy ''
-allowaccessmodification

# Conservar el Utils para la validacion de HWID por reflexion (CRITICO para que la app no falle al iniciar)
-keep class com.slipkprojects.sockshttp.util.Utils {
    public static java.lang.String getHWID(android.content.Context);
}

# Conservar librerias nativas JNI / NDK (Ej. tun2socks o VPN binaries)
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class * extends java.lang.Object { native <methods>; }

# Conservar Android Core components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.preference.Preference

# Conservar modelos serializables
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Ignorar advertencias inofensivas de clases faltantes en librerias externas
-dontwarn android.support.**
-dontwarn org.connectbot.**
-dontwarn net.i2p.crypto.**
-dontwarn com.github.kimchangyoun.**
-dontwarn com.slipkprojects.**

# Conservar clases de la app y del servicio VPN para evitar que R8 renombre miembros JNI o tunelizador
-keep class com.slipkprojects.** { *; }

# Conservar clases de librerias criptograficas y SSH
-keep class com.trilead.** { *; }
-keep class net.i2p.crypto.** { *; }
-keep class org.connectbot.** { *; }
-dontwarn com.trilead.**

# Conservar compatibilidad con Vistas de Android para XML
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}
