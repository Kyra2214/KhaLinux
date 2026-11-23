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

data class BlueUiState(
    val visible: Boolean = false,
    val sessions: List<RedSession> = emptyList(),
    val selectedSessionId: String? = null,
    val message: String? = null,
    val liveEvidence: RedLiveEvidenceSnapshot = RedLiveEvidenceSnapshot()
) {
    val selectedSession: RedSession?
        get() = sessions.firstOrNull { it.id == selectedSessionId }
}

class BlueViewModel(application: Application) : AndroidViewModel(application) {
    private val sessions = RedSessionStore(application)
    private val mutableState = MutableStateFlow(BlueUiState())
    val uiState: StateFlow<BlueUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            RedLiveEvidenceStore.state.collect { evidence ->
                mutableState.update { it.copy(liveEvidence = evidence) }
            }
        }
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
            mutableState.update { current ->
                current.copy(
                    sessions = all,
                    selectedSessionId = current.selectedSessionId?.takeIf { id -> all.any { it.id == id } }
                        ?: all.firstOrNull()?.id,
                    message = if (all.isEmpty()) "Ainda não há sessões RED para analisar." else null
                )
            }
        }
    }
}
