package com.khalinux.android.data

import com.khalinux.android.KhaLinuxRuntimeService
import com.khalinux.android.domain.RuntimeEventObserver
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.runtime.RuntimeEvent

class ServiceTerminalRuntimeGateway(
    private val service: KhaLinuxRuntimeService
) : TerminalRuntimeGateway {
    override fun isInstalled(): Boolean = service.isRuntimeInstalled()

    override fun start(command: String, observer: RuntimeEventObserver): String =
        service.startShell(
            shellCommand = command,
            listener = object : KhaLinuxRuntimeService.EventListener {
                override fun onEvent(event: RuntimeEvent) = observer.onEvent(event)
            }
        )

    override fun cancel(sessionId: String): Boolean = service.cancel(sessionId)
}
