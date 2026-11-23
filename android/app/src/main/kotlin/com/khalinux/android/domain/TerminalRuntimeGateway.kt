package com.khalinux.android.domain

import com.khalinux.runtime.RuntimeEvent
import com.khalinux.runtime.diagnostics.DiagnosticReport
import com.khalinux.runtime.install.InstallProgress

/** Observador da instalação automática (PRoot + RootFS) com progresso global 0..100. */
interface EnvironmentInstallObserver {
    fun onProgress(progress: InstallProgress)
    fun onFinished(runtimeReady: Boolean)
    fun onError(message: String)
}

fun interface RuntimeEventObserver {
    fun onEvent(event: RuntimeEvent)
}

/** Port used by terminal use cases; Android Service details stay in the data layer. */
interface TerminalRuntimeGateway {
    fun isInstalled(): Boolean
    fun isRootfsInstalled(): Boolean
    fun isProotInstalled(): Boolean
    fun installEnvironment(observer: EnvironmentInstallObserver)
    fun cancelInstall()
    /** Bloqueante; chamar fora da thread principal. */
    fun runDiagnostics(onUpdate: (DiagnosticReport) -> Unit): DiagnosticReport
    fun start(command: String, observer: RuntimeEventObserver): String
    fun startInteractive(rows: Int, columns: Int, observer: RuntimeEventObserver): String
    fun writeInput(sessionId: String, text: String): Boolean
    fun resize(sessionId: String, rows: Int, columns: Int): Boolean
    fun cancel(sessionId: String): Boolean
    /** Gerenciador de pacotes; nulo se o runtime ainda não está instalado. */
    fun packageManager(): com.khalinux.runtime.packages.PackageManager? = null
    /** Cyber Toolkit; nulo se o runtime ainda não está instalado. */
    fun toolkit(): com.khalinux.runtime.toolkit.ToolkitService? = null

    /** Motor operacional compartilhado entre terminal e interfaces do laboratório. */
    fun operationEngine(): com.khalinux.runtime.operations.LabOperationEngine? = null

    /** Inventário local e ativos já registrados; nulos/vazios quando o Service não está disponível. */
    fun refreshLocalDeviceInventory(): com.khalinux.runtime.operations.LabAsset? = null
    fun deviceAssets(): List<com.khalinux.runtime.operations.LabAsset> = emptyList()
    fun refreshAdbDeviceInventory(): List<com.khalinux.runtime.operations.LabAsset> = emptyList()
    fun adbClient(): com.khalinux.runtime.operations.AdbClient? = null

    /** Laboratórios (Fase 6); nulo se o runtime ainda não está instalado. */
    fun labs(): com.khalinux.runtime.labs.LabProgressService? = null

    /** Higiene de armazenamento (8.1). */
    fun storage(): com.khalinux.runtime.storage.StorageHygiene? = null

    fun isInstallRunning(): Boolean = false
}
