package com.khalinux.android.data

import com.khalinux.android.KhaLinuxRuntimeService
import com.khalinux.android.domain.RuntimeEventObserver
import com.khalinux.android.domain.RootfsInstallObserver
import com.khalinux.android.domain.ProotInstallObserver
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.runtime.RuntimeEvent

class ServiceTerminalRuntimeGateway(
    private val service: KhaLinuxRuntimeService
) : TerminalRuntimeGateway {
    override fun isInstalled(): Boolean = service.isRuntimeInstalled()

    override fun isRootfsInstalled(): Boolean = service.isRootfsInstalled()

    override fun isProotInstalled(): Boolean = service.isProotInstalled()

    override fun installProot(observer: ProotInstallObserver) {
        service.installLatestProot(object : KhaLinuxRuntimeService.ProotInstallListener {
            override fun onProgress(downloadedBytes: Long, totalBytes: Long?) =
                observer.onProgress(downloadedBytes, totalBytes)

            override fun onInstalled(prootExecutable: java.io.File, runtimeReady: Boolean) =
                observer.onInstalled(runtimeReady)

            override fun onError(message: String) = observer.onError(message)
        })
    }

    override fun installRootfs(observer: RootfsInstallObserver) {
        service.installLatestRootfs(object : KhaLinuxRuntimeService.RootfsInstallListener {
            override fun onProgress(downloadedBytes: Long, totalBytes: Long?) =
                observer.onProgress(downloadedBytes, totalBytes)

            override fun onInstalled(rootfsDirectory: java.io.File, runtimeReady: Boolean) =
                observer.onInstalled(runtimeReady)

            override fun onError(message: String) = observer.onError(message)
        })
    }

    override fun start(command: String, observer: RuntimeEventObserver): String =
        service.startShell(
            shellCommand = command,
            listener = observerListener(observer)
        )

    override fun startInteractive(observer: RuntimeEventObserver): String =
        service.startInteractiveShell(listener = observerListener(observer))

    override fun writeInput(sessionId: String, text: String): Boolean = service.writeInput(sessionId, text)

    override fun resize(sessionId: String, rows: Int, columns: Int): Boolean =
        service.resize(sessionId, rows, columns)

    override fun cancel(sessionId: String): Boolean = service.cancel(sessionId)

    private fun observerListener(observer: RuntimeEventObserver) =
        object : KhaLinuxRuntimeService.EventListener {
            override fun onEvent(event: RuntimeEvent) = observer.onEvent(event)
        }
}
