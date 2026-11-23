package com.khalinux.android.blue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khalinux.android.red.RedSession
import com.khalinux.android.red.RedSessionStore
import com.khalinux.android.red.RedLiveEvidenceSnapshot
import com.khalinux.android.red.RedLiveEvidenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.runtime.operations.LabOperationEngine
import com.khalinux.runtime.operations.LabEventRecord

data class BlueUiState(
    val visible: Boolean = false,
    val sessions: List<RedSession> = emptyList(),
    val selectedSessionId: String? = null,
    val message: String? = null,
    val liveEvidence: RedLiveEvidenceSnapshot = RedLiveEvidenceSnapshot(),
    val operationEvents: List<LabEventRecord> = emptyList()
) {
    val selectedSession: RedSession?
        get() = sessions.firstOrNull { it.id == selectedSessionId }
}

class BlueViewModel(application: Application) : AndroidViewModel(application) {
    private val sessions = RedSessionStore(application)
    @Volatile private var runtimeGateway: TerminalRuntimeGateway? = null
    private val engineLock = Any()
    private var operationEngine: LabOperationEngine? = null
    private var operationEventSubscription: Closeable? = null
    private val mutableState = MutableStateFlow(BlueUiState())
    val uiState: StateFlow<BlueUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            RedLiveEvidenceStore.state.collect { evidence ->
                mutableState.update { it.copy(liveEvidence = evidence) }
            }
        }
    }

    fun attachRuntime(gateway: TerminalRuntimeGateway) {
        runtimeGateway = gateway
        viewModelScope.launch(Dispatchers.IO) { connectOperationEngine() }
    }

    fun detachRuntime() {
        runtimeGateway = null
        synchronized(engineLock) {
            operationEventSubscription?.close()
            operationEventSubscription = null
            operationEngine = null
        }
        mutableState.update { it.copy(operationEvents = emptyList()) }
    }

    private fun connectOperationEngine(): LabOperationEngine? {
        val next = runtimeGateway?.operationEngine() ?: return null
        synchronized(engineLock) {
            if (operationEngine !== next) {
                operationEventSubscription?.close()
                operationEngine = next
                operationEventSubscription = next.addEventListener { event ->
                    viewModelScope.launch(Dispatchers.IO) {
                        mutableState.update { current ->
                            current.copy(operationEvents =
                                (current.operationEvents.filterNot { it.id == event.id } + event).takeLast(300))
                        }
                    }
                }
            }
        }
        return next
    }

    override fun onCleared() {
        operationEventSubscription?.close()
        operationEventSubscription = null
        super.onCleared()
    }

    fun open() {
        mutableState.update { it.copy(visible = true, message = null) }
        refresh()
    }

    fun close() {
        mutableState.update { it.copy(visible = false, selectedSessionId = null) }
    }

    fun selectSession(id: String?) {
        mutableState.update { it.copy(selectedSessionId = id) }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = sessions.all()
            val engine = connectOperationEngine()
            val operationEvents = engine?.sessions()?.flatMap { engine.events(it.id) }
                ?.sortedBy { it.occurredAtEpochMillis }?.takeLast(300).orEmpty()
            mutableState.update { current ->
                current.copy(
                    sessions = all,
                    operationEvents = operationEvents,
                    selectedSessionId = current.selectedSessionId?.takeIf { id -> all.any { it.id == id } }
                        ?: all.firstOrNull()?.id,
                    message = if (all.isEmpty() && operationEvents.isEmpty()) "Ainda não há sessões RED nem eventos operacionais para analisar." else null
                )
            }
        }
    }
}
