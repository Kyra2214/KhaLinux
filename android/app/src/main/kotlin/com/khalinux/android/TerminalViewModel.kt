package com.khalinux.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khalinux.android.domain.RuntimeEventObserver
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.android.domain.TerminalUseCases
import com.khalinux.runtime.RuntimeEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val DEFAULT_COMMAND = "printf 'KhaLinux\\n'"

data class TerminalUiState(
    val serviceConnected: Boolean = false,
    val runtimeInstalled: Boolean = false,
    val connectionMessage: String? = null,
    val command: String = DEFAULT_COMMAND,
    val output: String = "",
    val sessionId: String? = null,
    val isInteractive: Boolean = false,
    val isStarting: Boolean = false,
    val isSendingInput: Boolean = false
) {
    val isRunning: Boolean get() = isStarting || sessionId != null
}

class TerminalViewModel : ViewModel() {
    private val lock = Any()
    private val transcript = TerminalOutputBuffer()
    private val mutableUiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = mutableUiState.asStateFlow()

    private var runtimeGateway: TerminalRuntimeGateway? = null
    private var runtimeUseCases: TerminalUseCases? = null
    private var startJob: Job? = null
    private var inputJob: Job? = null
    private val completedSessionsBeforeStartReturn = mutableSetOf<String>()

    fun attachRuntime(gateway: TerminalRuntimeGateway) = synchronized(lock) {
        if (runtimeGateway !== gateway && mutableUiState.value.isRunning) {
            appendOutputLocked("\n[sessão interrompida após reconexão do Service]\n")
            startJob?.cancel()
            inputJob?.cancel()
            completedSessionsBeforeStartReturn.clear()
            mutableUiState.update { it.copy(sessionId = null, isInteractive = false, isStarting = false, isSendingInput = false) }
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
        if (mutableUiState.value.sessionId != null || mutableUiState.value.isStarting) {
            appendOutputLocked("\n[Service desconectado; sessão não está mais vinculada]\n")
        }
        startJob?.cancel()
        inputJob?.cancel()
        completedSessionsBeforeStartReturn.clear()
        runtimeGateway = null
        runtimeUseCases = null
        mutableUiState.update {
            it.copy(
                serviceConnected = false,
                runtimeInstalled = false,
                connectionMessage = "Service local desconectado",
                sessionId = null,
                isInteractive = false,
                isStarting = false,
                isSendingInput = false
            )
        }
    }

    fun bindingUnavailable() = synchronized(lock) {
        startJob?.cancel()
        inputJob?.cancel()
        completedSessionsBeforeStartReturn.clear()
        runtimeGateway = null
        runtimeUseCases = null
        mutableUiState.update {
            it.copy(
                serviceConnected = false,
                runtimeInstalled = false,
                connectionMessage = "Não foi possível conectar ao Service local",
                sessionId = null,
                isInteractive = false,
                isStarting = false,
                isSendingInput = false
            )
        }
    }

    fun updateCommand(command: String) {
        mutableUiState.update { it.copy(command = command) }
    }

    fun runCommand() {
        val start = synchronized(lock) {
            val activeUseCases = runtimeUseCases ?: return
            val current = mutableUiState.value
            if (!current.runtimeInstalled || current.isRunning || current.isInteractive) return
            val command = current.command.trim()
            if (command.isEmpty()) return
            appendOutputLocked("\n$ $command\n")
            mutableUiState.update { it.copy(isStarting = true) }
            activeUseCases to command
        }
        launchSession(interactive = false, useCases = start.first, command = start.second)
    }

    fun openInteractiveShell() {
        val activeUseCases = synchronized(lock) {
            val current = mutableUiState.value
            val useCases = runtimeUseCases ?: return
            if (!current.runtimeInstalled || current.isRunning) return
            appendOutputLocked("\n[shell interativo PTY iniciando; TERM=dumb]\n")
            mutableUiState.update { it.copy(isStarting = true) }
            useCases
        }
        launchSession(interactive = true, useCases = activeUseCases)
    }

    private fun launchSession(
        interactive: Boolean,
        useCases: TerminalUseCases,
        command: String? = null
    ) {
        startJob = viewModelScope.launch {
            val createdSessionId = AtomicReference<String?>(null)
            try {
                val sessionId = withContext(Dispatchers.IO) {
                    val id = if (interactive) {
                        useCases.startInteractive(observerFor(useCases))
                    } else {
                        useCases.startCommand(requireNotNull(command), observerFor(useCases))
                    }
                    createdSessionId.set(id)
                    id
                }
                synchronized(lock) {
                    val alreadyFinished = completedSessionsBeforeStartReturn.remove(sessionId)
                    mutableUiState.update { state ->
                        state.copy(
                            sessionId = if (alreadyFinished) null else sessionId,
                            isStarting = false,
                            isInteractive = interactive && !alreadyFinished,
                            command = if (interactive) "" else state.command
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                createdSessionId.get()?.let { sessionId ->
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { useCases.cancelSession(sessionId) }
                    }
                }
                throw cancelled
            } catch (error: Exception) {
                synchronized(lock) {
                    mutableUiState.update { it.copy(isStarting = false, sessionId = null, isInteractive = false) }
                    appendOutputLocked("erro ao iniciar sessão: ${error.message ?: error.javaClass.simpleName}\n")
                }
            }
        }
    }

    fun sendInput() {
        val payload = synchronized(lock) {
            val activeUseCases = runtimeUseCases ?: return
            val current = mutableUiState.value
            val sessionId = current.sessionId ?: return
            if (!current.isInteractive || current.isStarting || current.isSendingInput) return
            val input = current.command
            if (input.isEmpty()) return
            mutableUiState.update { it.copy(isSendingInput = true) }
            Triple(activeUseCases, sessionId, input)
        }
        inputJob = viewModelScope.launch {
            try {
                val sent = withContext(Dispatchers.IO) {
                    payload.first.writeInput(payload.second, "${payload.third}\n")
                }
                synchronized(lock) {
                    mutableUiState.update { state ->
                        state.copy(
                            command = if (sent && state.command == payload.third) "" else state.command,
                            isSendingInput = false
                        )
                    }
                    if (!sent) appendOutputLocked("\n[entrada não enviada: sessão PTY indisponível]\n")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                synchronized(lock) {
                    mutableUiState.update { it.copy(isSendingInput = false) }
                    appendOutputLocked("\n[erro de entrada: ${error.message ?: error.javaClass.simpleName}]\n")
                }
            }
        }
    }

    fun resizeTerminal(rows: Int, columns: Int) = synchronized(lock) {
        val activeUseCases = runtimeUseCases ?: return
        val sessionId = mutableUiState.value.sessionId ?: return
        if (mutableUiState.value.isInteractive) activeUseCases.resize(sessionId, rows, columns)
    }

    fun cancelCommand() {
        val (activeUseCases, sessionId) = synchronized(lock) {
            val useCases = runtimeUseCases ?: return
            val id = mutableUiState.value.sessionId ?: return
            useCases to id
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { activeUseCases.cancelSession(sessionId) }
        }
    }

    private fun observerFor(activeUseCases: TerminalUseCases) = RuntimeEventObserver { event ->
        synchronized(lock) {
            if (runtimeUseCases === activeUseCases) handleEventLocked(event)
        }
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
                    inputJob?.cancel()
                    val result = if (event.timedOut) "timeout" else "exit=${event.exitCode}"
                    appendOutputLocked("\n[$result]\n")
                    if (mutableUiState.value.isStarting) {
                        completedSessionsBeforeStartReturn.add(event.sessionId)
                    }
                    mutableUiState.update { it.copy(sessionId = null, isInteractive = false, isSendingInput = false) }
                }
            }
            is RuntimeEvent.Failed -> {
                if (event.sessionId == activeSessionId) {
                    inputJob?.cancel()
                    appendOutputLocked("\n[falha=${event.message}]\n")
                    if (mutableUiState.value.isStarting) {
                        completedSessionsBeforeStartReturn.add(event.sessionId)
                    }
                    mutableUiState.update { it.copy(sessionId = null, isInteractive = false, isSendingInput = false) }
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
            startJob?.cancel()
            inputJob?.cancel()
            completedSessionsBeforeStartReturn.clear()
            runtimeGateway = null
            runtimeUseCases = null
        }
        super.onCleared()
    }
}
