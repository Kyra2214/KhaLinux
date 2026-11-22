package com.khalinux.android

import androidx.lifecycle.ViewModel
import com.khalinux.android.domain.RuntimeEventObserver
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.android.domain.TerminalUseCases
import com.khalinux.runtime.RuntimeEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val DEFAULT_COMMAND = "printf 'KhaLinux\\n'"

data class TerminalUiState(
    val serviceConnected: Boolean = false,
    val runtimeInstalled: Boolean = false,
    val connectionMessage: String? = null,
    val command: String = DEFAULT_COMMAND,
    val output: String = "",
    val sessionId: String? = null
) {
    val isRunning: Boolean get() = sessionId != null
}

class TerminalViewModel : ViewModel() {
    private val lock = Any()
    private val transcript = TerminalOutputBuffer()
    private val mutableUiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = mutableUiState.asStateFlow()

    private var runtimeGateway: TerminalRuntimeGateway? = null
    private var runtimeUseCases: TerminalUseCases? = null

    fun attachRuntime(gateway: TerminalRuntimeGateway) = synchronized(lock) {
        if (runtimeGateway !== gateway && mutableUiState.value.sessionId != null) {
            appendOutputLocked("\n[sessão interrompida após reconexão do Service]\n")
            mutableUiState.update { it.copy(sessionId = null) }
        }
        runtimeGateway = gateway
        runtimeUseCases = TerminalUseCases(gateway)
        mutableUiState.update {
            it.copy(
                serviceConnected = true,
                runtimeInstalled = runtimeUseCases?.isRuntimeInstalled?.invoke() == true,
                connectionMessage = null
            )
        }
    }

    fun serviceDisconnected() = synchronized(lock) {
        if (mutableUiState.value.sessionId != null) {
            appendOutputLocked("\n[Service desconectado; sessão não está mais vinculada]\n")
        }
        runtimeGateway = null
        runtimeUseCases = null
        mutableUiState.update {
            it.copy(
                serviceConnected = false,
                runtimeInstalled = false,
                connectionMessage = "Service local desconectado",
                sessionId = null
            )
        }
    }

    fun bindingUnavailable() = synchronized(lock) {
        runtimeGateway = null
        runtimeUseCases = null
        mutableUiState.update {
            it.copy(
                serviceConnected = false,
                runtimeInstalled = false,
                connectionMessage = "Não foi possível conectar ao Service local",
                sessionId = null
            )
        }
    }

    fun updateCommand(command: String) {
        mutableUiState.update { it.copy(command = command) }
    }

    fun runCommand() = synchronized(lock) {
        val activeUseCases = runtimeUseCases ?: return
        val current = mutableUiState.value
        if (!current.runtimeInstalled || current.isRunning) return
        val command = current.command.trim()
        if (command.isEmpty()) return

        appendOutputLocked("\n$ $command\n")
        try {
            val sessionId = activeUseCases.startCommand(
                command,
                RuntimeEventObserver { event ->
                    synchronized(lock) {
                        if (runtimeUseCases === activeUseCases) handleEventLocked(event)
                    }
                }
            )
            mutableUiState.update { it.copy(sessionId = sessionId) }
        } catch (error: Exception) {
            appendOutputLocked("erro: ${error.message ?: error.javaClass.simpleName}\n")
        }
    }

    fun cancelCommand() = synchronized(lock) {
        val activeUseCases = runtimeUseCases ?: return
        val sessionId = mutableUiState.value.sessionId ?: return
        activeUseCases.cancelSession(sessionId)
    }

    private fun handleEventLocked(event: RuntimeEvent) {
        val activeSessionId = mutableUiState.value.sessionId
        when (event) {
            is RuntimeEvent.Started -> {
                if (activeSessionId == null || activeSessionId == event.sessionId) {
                    mutableUiState.update { it.copy(sessionId = event.sessionId) }
                }
            }
            is RuntimeEvent.Stdout -> {
                if (event.sessionId == activeSessionId) appendOutputLocked(String(event.bytes, Charsets.UTF_8))
            }
            is RuntimeEvent.Stderr -> {
                if (event.sessionId == activeSessionId) appendOutputLocked(String(event.bytes, Charsets.UTF_8))
            }
            is RuntimeEvent.Exited -> {
                if (event.sessionId == activeSessionId) {
                    val result = if (event.timedOut) "timeout" else "exit=${event.exitCode}"
                    appendOutputLocked("\n[$result]\n")
                    mutableUiState.update { it.copy(sessionId = null) }
                }
            }
            is RuntimeEvent.Failed -> {
                if (activeSessionId == null || event.sessionId == activeSessionId) {
                    appendOutputLocked("\n[falha=${event.message}]\n")
                    mutableUiState.update { it.copy(sessionId = null) }
                }
            }
        }
    }

    private fun appendOutputLocked(text: String) {
        transcript.append(text)
        val output = transcript.toString()
        mutableUiState.update { it.copy(output = output) }
    }

    override fun onCleared() {
        synchronized(lock) {
            val activeUseCases = runtimeUseCases
            val sessionId = mutableUiState.value.sessionId
            if (activeUseCases != null && sessionId != null) {
                try {
                    activeUseCases.cancelSession(sessionId)
                } catch (_: Exception) {
                    // The service may already have been destroyed with the Activity.
                }
            }
            runtimeGateway = null
            runtimeUseCases = null
        }
        super.onCleared()
    }
}
