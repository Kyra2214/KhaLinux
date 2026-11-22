package com.khalinux.android.tutor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.runtime.tutor.TutorContext
import com.khalinux.runtime.tutor.TutorEngine
import com.khalinux.runtime.tutor.TutorKnowledgeParser
import com.khalinux.runtime.tutor.TutorReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Uma troca da conversa; a conversa vive só em memória (nada é gravado nem enviado para fora). */
data class TutorMessage(val question: String, val reply: TutorReply)

data class TutorUiState(
    val visible: Boolean = false,
    val input: String = "",
    val context: TutorContext = TutorContext(),
    val messages: List<TutorMessage> = emptyList(),
    val unavailable: Boolean = false
)

/** Tutor didático offline (7.8): base `knowledge.tsv` (asset) + descritores do toolkit; sem rede e sem modelo. */
class TutorViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(TutorUiState())
    val uiState: StateFlow<TutorUiState> = mutableState.asStateFlow()

    @Volatile private var gateway: TerminalRuntimeGateway? = null
    @Volatile private var engine: TutorEngine? = null

    fun attach(gateway: TerminalRuntimeGateway) {
        this.gateway = gateway
        engine = null // reconstruído com os descritores do toolkit na próxima pergunta
    }

    fun open() = mutableState.update { it.copy(visible = true, context = TutorContext()) }

    /** Abre o tutor já ligado ao exercício em andamento (só enunciado público; nunca a resposta). */
    fun openWith(context: TutorContext) = mutableState.update { it.copy(visible = true, context = context, messages = emptyList()) }

    fun close() = mutableState.update { it.copy(visible = false) }

    fun clear() = mutableState.update { it.copy(messages = emptyList()) }

    fun setInput(text: String) = mutableState.update { it.copy(input = text.take(TutorEngine.MAX_QUESTION)) }

    fun send() {
        val question = mutableState.value.input.trim()
        if (question.isEmpty()) return
        val context = mutableState.value.context
        viewModelScope.launch(Dispatchers.IO) {
            val active = engine ?: buildEngine()
            if (active == null) {
                mutableState.update { it.copy(unavailable = true) }
                return@launch
            }
            val reply = active.ask(question, context)
            mutableState.update { it.copy(input = "", unavailable = false, messages = (it.messages + TutorMessage(question, reply)).takeLast(MAX_MESSAGES)) }
        }
    }

    private fun buildEngine(): TutorEngine? = try {
        val text = getApplication<Application>().assets.open("knowledge.tsv").use { String(it.readBytes(), Charsets.UTF_8) }
        val tools = runCatching { gateway?.toolkit()?.registry?.tools }.getOrNull().orEmpty()
        TutorEngine(TutorKnowledgeParser.parse(text), tools).also { engine = it }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MAX_MESSAGES = 30
    }
}
