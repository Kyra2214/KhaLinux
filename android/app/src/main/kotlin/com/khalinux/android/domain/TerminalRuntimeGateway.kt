package com.khalinux.android.domain

import com.khalinux.runtime.RuntimeEvent

fun interface RuntimeEventObserver {
    fun onEvent(event: RuntimeEvent)
}

/** Port used by terminal use cases; Android Service details stay in the data layer. */
interface TerminalRuntimeGateway {
    fun isInstalled(): Boolean
    fun start(command: String, observer: RuntimeEventObserver): String
    fun cancel(sessionId: String): Boolean
}
