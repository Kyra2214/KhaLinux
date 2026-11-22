package com.khalinux.android.labs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khalinux.android.R
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.runtime.labs.ExerciseState
import com.khalinux.runtime.labs.LabDescriptor
import com.khalinux.runtime.labs.LabExercise
import com.khalinux.runtime.labs.LabHint
import com.khalinux.runtime.labs.LabLockedException
import com.khalinux.runtime.labs.LabProgressService
import com.khalinux.runtime.labs.LabSession
import com.khalinux.runtime.labs.SubmitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LabRow(val lab: LabDescriptor, val completed: Boolean, val locked: Boolean, val points: Int, val missing: List<String>)

data class ExerciseView(val exercise: LabExercise, val state: ExerciseState, val hints: List<LabHint>)

data class LabsUiState(
    val visible: Boolean = false,
    val available: Boolean = false,
    val totalPoints: Int = 0,
    val rows: List<LabRow> = emptyList(),
    val openLab: LabDescriptor? = null,
    val exercises: List<ExerciseView> = emptyList(),
    val answers: Map<String, String> = emptyMap(),
    val feedback: Map<String, String> = emptyMap(),
    val message: String? = null
)

/** Estado da tela de Laboratórios: trilhas e pré-requisitos, sessão do laboratório aberto, dicas, respostas e pontos. */
class LabsViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(LabsUiState())
    val uiState: StateFlow<LabsUiState> = mutableState.asStateFlow()

    @Volatile private var gateway: TerminalRuntimeGateway? = null
    @Volatile private var session: LabSession? = null

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    fun attach(gateway: TerminalRuntimeGateway) {
        this.gateway = gateway
        if (mutableState.value.visible) refresh()
    }

    fun open() {
        mutableState.update { it.copy(visible = true) }
        refresh()
    }

    fun close() {
        session = null
        mutableState.update { LabsUiState(visible = false, available = it.available, totalPoints = it.totalPoints, rows = it.rows) }
    }

    fun dismissMessage() = mutableState.update { it.copy(message = null) }

    fun setAnswer(exerciseId: String, answer: String) =
        mutableState.update { it.copy(answers = it.answers + (exerciseId to answer.take(200))) }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { reload() }
    }

    private fun reload() {
        val service = gateway?.labs()
        if (service == null) {
            mutableState.update { it.copy(available = false, rows = emptyList(), openLab = null, exercises = emptyList()) }
            return
        }
        val progress = service.progress()
        val rows = service.engine.catalog.labs.map { lab ->
            val missing = lab.prerequisites.filter { it !in progress.completedLabs }
            LabRow(lab, lab.id in progress.completedLabs, missing.isNotEmpty(), progress.pointsOf(lab.id), missing)
        }
        val current = session
        mutableState.update {
            it.copy(
                available = true, totalPoints = progress.totalPoints, rows = rows,
                exercises = if (current != null) views(current) else emptyList()
            )
        }
    }

    private fun views(session: LabSession): List<ExerciseView> =
        session.lab.exercises.map { ExerciseView(it, session.state(it.id), session.revealedHints(it.id)) }

    fun openLab(labId: String) {
        val service = gateway?.labs() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val opened = service.open(labId)
                session = opened
                mutableState.update {
                    it.copy(openLab = opened.lab, exercises = views(opened), answers = emptyMap(), feedback = emptyMap(), message = null)
                }
            } catch (e: LabLockedException) {
                mutableState.update { it.copy(message = e.message) }
            }
        }
    }

    fun closeLab() {
        session = null
        mutableState.update { it.copy(openLab = null, exercises = emptyList(), answers = emptyMap(), feedback = emptyMap(), message = null) }
        refresh()
    }

    fun submit(exerciseId: String) {
        val service = gateway?.labs() ?: return
        val current = session ?: return
        val answer = mutableState.value.answers[exerciseId].orEmpty()
        viewModelScope.launch(Dispatchers.IO) {
            val feedback = when (val result = service.submit(current, exerciseId, answer)) {
                is SubmitResult.Correct ->
                    text(R.string.lab_correct, result.pointsAwarded) + if (result.labCompleted) " " + text(R.string.lab_completed) else ""
                is SubmitResult.Incorrect -> text(R.string.lab_incorrect, result.wrongAttempts)
                SubmitResult.AlreadySolved -> text(R.string.lab_already)
                SubmitResult.EmptyAnswer -> text(R.string.lab_empty)
            }
            mutableState.update { it.copy(feedback = it.feedback + (exerciseId to feedback)) }
            reload()
        }
    }

    fun revealHint(exerciseId: String) {
        val service = gateway?.labs() ?: return
        val current = session ?: return
        viewModelScope.launch(Dispatchers.IO) {
            service.revealNextHint(current, exerciseId)
            reload()
        }
    }

    fun resetLab(labId: String) {
        val service = gateway?.labs() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            service.resetLab(labId)
            session = null
            mutableState.update { it.copy(openLab = null, exercises = emptyList(), answers = emptyMap(), feedback = emptyMap()) }
            reload()
        }
    }

    fun resetAll() {
        val service = gateway?.labs() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            service.resetAll()
            session = null
            mutableState.update { it.copy(openLab = null, exercises = emptyList(), answers = emptyMap(), feedback = emptyMap()) }
            reload()
        }
    }
}
