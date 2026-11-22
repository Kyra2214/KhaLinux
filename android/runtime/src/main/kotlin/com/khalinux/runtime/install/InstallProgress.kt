package com.khalinux.runtime.install

/** Fases visíveis da instalação automática do ambiente Linux. */
enum class InstallPhase(val label: String) {
    CHECKING("Verificando dispositivo"),
    DOWNLOADING_PROOT("Baixando PRoot"),
    DOWNLOADING_ROOTFS("Baixando Debian ARM64"),
    VERIFYING("Verificando integridade"),
    EXTRACTING("Instalando Debian"),
    FINALIZING("Finalizando ambiente"),
    READY("Ambiente pronto"),
    FAILED("Falha na instalação")
}

/** Estado publicado ao longo da instalação; [percent] é global (0..100). */
data class InstallProgress(
    val phase: InstallPhase,
    val percent: Int,
    val doneBytes: Long = 0L,
    val totalBytes: Long? = null
)

/**
 * Converte (fase, fração da fase) em um percentual global monotônico.
 * Bandas: PRoot 0–3, download RootFS 3–58, verificação 58–60, extração 60–97, finalização 97–100.
 */
object InstallProgressCalculator {
    private data class Band(val start: Int, val end: Int)

    private fun band(phase: InstallPhase): Band = when (phase) {
        InstallPhase.CHECKING -> Band(0, 0)
        InstallPhase.DOWNLOADING_PROOT -> Band(0, 3)
        InstallPhase.DOWNLOADING_ROOTFS -> Band(3, 58)
        InstallPhase.VERIFYING -> Band(58, 60)
        InstallPhase.EXTRACTING -> Band(60, 97)
        InstallPhase.FINALIZING -> Band(97, 100)
        InstallPhase.READY -> Band(100, 100)
        InstallPhase.FAILED -> Band(0, 0)
    }

    fun percent(phase: InstallPhase, fraction: Double): Int {
        val band = band(phase)
        val clamped = if (fraction.isNaN()) 0.0 else fraction.coerceIn(0.0, 1.0)
        return (band.start + (band.end - band.start) * clamped).toInt().coerceIn(0, 100)
    }

    fun fraction(done: Long, total: Long?): Double =
        if (total != null && total > 0L) (done.toDouble() / total.toDouble()).coerceIn(0.0, 1.0) else 0.0
}

/** Cancelamento solicitado pelo usuário; não deve ser repetido automaticamente. */
class InstallCancelledException : RuntimeException("Instalação cancelada")
