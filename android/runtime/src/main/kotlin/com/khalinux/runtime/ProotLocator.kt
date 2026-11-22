package com.khalinux.runtime

import java.io.File

/**
 * Onde o executável PRoot foi encontrado e com que ambiente ele precisa rodar.
 * [source] é apenas informativo (aparece no diagnóstico).
 */
data class ProotLocation(
    val executable: File,
    val source: String,
    /** Variáveis extras exigidas por esta origem (ex.: PROOT_LOADER no plano B). */
    val environment: Map<String, String> = emptyMap()
)

/**
 * Abstração de "onde está o PRoot". O plano A baixa o binário para `filesDir/runtime/proot`;
 * se o Android bloquear a execução ali, o plano B empacota `libproot.so` em `jniLibs`
 * ([NativeLibProotLocator]) sem alterar o restante do runtime.
 */
interface ProotLocator {
    /** Retorna a primeira origem utilizável ou nulo se nenhuma existir. */
    fun locate(): ProotLocation?

    /** Descrição das origens tentadas, para mensagens de erro. */
    fun describe(): String
}

/** Plano A: binário baixado e verificado em armazenamento privado do app. */
class FilesDirProotLocator(private val executable: File) : ProotLocator {
    override fun locate(): ProotLocation? =
        if (executable.isFile && executable.canExecute()) ProotLocation(executable, SOURCE) else null

    override fun describe(): String = "$SOURCE (${executable.path})"

    companion object { const val SOURCE = "filesDir" }
}

/**
 * Plano B: `libproot.so` em `jniLibs/arm64-v8a`, extraído em `nativeLibraryDir`
 * (exige `android:extractNativeLibs="true"`/`useLegacyPackaging = true`). Se existir
 * `libproot-loader.so`, ele é informado ao PRoot por `PROOT_LOADER`.
 * Nenhuma biblioteca é empacotada nesta versão; o localizador só passa a funcionar quando o plano B for adotado.
 */
class NativeLibProotLocator(private val nativeLibraryDirectory: File) : ProotLocator {
    override fun locate(): ProotLocation? {
        val proot = File(nativeLibraryDirectory, PROOT_LIBRARY)
        if (!proot.isFile || !proot.canExecute()) return null
        val loader = File(nativeLibraryDirectory, LOADER_LIBRARY)
        val environment = if (loader.isFile) mapOf("PROOT_LOADER" to loader.absolutePath) else emptyMap()
        return ProotLocation(proot, SOURCE, environment)
    }

    override fun describe(): String = "$SOURCE (${File(nativeLibraryDirectory, PROOT_LIBRARY).path})"

    companion object {
        const val SOURCE = "nativeLibraryDir"
        const val PROOT_LIBRARY = "libproot.so"
        const val LOADER_LIBRARY = "libproot-loader.so"
    }
}

/** Tenta cada localizador na ordem dada. */
class FallbackProotLocator(private val locators: List<ProotLocator>) : ProotLocator {
    init { require(locators.isNotEmpty()) { "ao menos um localizador é necessário" } }

    override fun locate(): ProotLocation? = locators.firstNotNullOfOrNull { it.locate() }

    override fun describe(): String = locators.joinToString("; ") { it.describe() }
}
