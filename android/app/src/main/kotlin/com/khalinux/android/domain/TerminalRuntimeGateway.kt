package com.khalinux.android.domain

import com.khalinux.runtime.RuntimeEvent

interface RootfsInstallObserver {
    fun onProgress(downloadedBytes: Long, totalBytes: Long?)
    fun onInstalled(runtimeReady: Boolean)
    fun onError(message: String)
}

fun interface RuntimeEventObserver {
    fun onEvent(event: RuntimeEvent)
}

/** Port used by terminal use cases; Android Service details stay in the data layer. */
interface TerminalRuntimeGateway {
    fun isInstalled(): Boolean
    fun installRootfs(observer: RootfsInstallObserver)
    fun start(command: String, observer: RuntimeEventObserver): String
    fun startInteractive(observer: RuntimeEventObserver): String
    fun writeInput(sessionId: String, text: String): Boolean
    fun resize(sessionId: String, rows: Int, columns: Int): Boolean
    fun cancel(sessionId: String): Boolean
}
