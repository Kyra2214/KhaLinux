package com.khalinux.runtime.memory

/**
 * Política de pressão de memória (8.4). Os níveis são os inteiros de `ComponentCallbacks2.onTrimMemory` (o runtime
 * não depende de Android). Só os níveis realmente críticos reduzem o histórico das abas em segundo plano:
 * `TRIM_MEMORY_RUNNING_CRITICAL` (15) com o app visível e `TRIM_MEMORY_MODERATE`/`COMPLETE` (60/80) em segundo plano.
 * Níveis informativos (`UI_HIDDEN` = 20, `BACKGROUND` = 40, `RUNNING_LOW` = 10) não descartam dados do usuário.
 */
object MemoryPressure {
    const val RUNNING_CRITICAL = 15
    const val MODERATE = 60

    fun shouldTrimBackgroundTabs(level: Int): Boolean = level == RUNNING_CRITICAL || level >= MODERATE
}
