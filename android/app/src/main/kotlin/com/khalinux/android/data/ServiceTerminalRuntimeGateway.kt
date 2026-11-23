package com.khalinux.android.data

import com.khalinux.android.KhaLinuxRuntimeService
import com.khalinux.android.domain.RuntimeEventObserver
import com.khalinux.android.domain.EnvironmentInstallObserver
import com.khalinux.runtime.install.InstallProgress
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.runtime.RuntimeEvent
import com.khalinux.runtime.diagnostics.DiagnosticReport

class ServiceTerminalRuntimeGateway(
    private val service: KhaLinuxRuntimeService
) : TerminalRuntimeGateway {
    // Igualdade por instância do Service: recriar a Activity (rotação) não deve parecer um novo runtime.
    override fun equals(other: Any?): Boolean =
        other is ServiceTerminalRuntimeGateway && other.service === service

    override fun hashCode(): Int = System.identityHashCode(service)

    override fun isInstalled(): Boolean = service.isRuntimeInstalled()

    override fun isRootfsInstalled(): Boolean = service.isRootfsInstalled()

    override fun isProotInstalled(): Boolean = service.isProotInstalled()

    override fun installEnvironment(observer: EnvironmentInstallObserver) {
        service.installEnvironment(object : KhaLinuxRuntimeService.EnvironmentInstallListener {
            override fun onProgress(progress: InstallProgress) = observer.onProgress(progress)

            override fun onFinished(runtimeReady: Boolean) = observer.onFinished(runtimeReady)

            override fun onError(message: String) = observer.onError(message)
        })
    }

    override fun cancelInstall() = service.cancelInstall()

    override fun packageManager(): com.khalinux.runtime.packages.PackageManager? = service.packageManager()

    override fun toolkit(): com.khalinux.runtime.toolkit.ToolkitService? = service.toolkit()

    override fun operationEngine(): com.khalinux.runtime.operations.LabOperationEngine? = service.operationEngine()

    override fun labs(): com.khalinux.runtime.labs.LabProgressService? = service.labs()

    override fun storage(): com.khalinux.runtime.storage.StorageHygiene? = service.storage()

    override fun isInstallRunning(): Boolean = service.isInstallRunning()

    override fun runDiagnostics(onUpdate: (DiagnosticReport) -> Unit): DiagnosticReport =
        service.runDiagnostics(onUpdate)

    override fun start(command: String, observer: RuntimeEventObserver): String =
        service.startShell(
            shellCommand = command,
            listener = observerListener(observer)
        )

    override fun startInteractive(rows: Int, columns: Int, observer: RuntimeEventObserver): String =
        service.startInteractiveShell(rows = rows, columns = columns, listener = observerListener(observer))

    override fun writeInput(sessionId: String, text: String): Boolean = service.writeInput(sessionId, text)

    override fun resize(sessionId: String, rows: Int, columns: Int): Boolean =
        service.resize(sessionId, rows, columns)

    override fun cancel(sessionId: String): Boolean = service.cancel(sessionId)

    private fun observerListener(observer: RuntimeEventObserver) =
        object : KhaLinuxRuntimeService.EventListener {
            override fun onEvent(event: RuntimeEvent) = observer.onEvent(event)
        }
}
