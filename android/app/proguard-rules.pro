# Regras R8/ProGuard do KhaLinux (release). Não validadas em build real (sem SDK/rede na sessão de autoria).
# Se o release falhar ou se comportar diferente do debug, reproduza com -PkhalinuxMinify=false e ajuste aqui.

# Nomes de classe/método fazem parte dos símbolos JNI de native_pty.c (também em consumer-rules.pro do runtime).
-keep class com.khalinux.runtime.NativePty { *; }

# commons-compress usa ServiceLoader/reflexão e referencia dependências opcionais ausentes (xz, zstd, brotli, asm, osgi).
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
-dontwarn javax.annotation.**

# Stack traces úteis nos relatórios de problema.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
