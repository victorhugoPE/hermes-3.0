package br.com.hermes.agent

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class BlackboardEntry(
    val key: String,
    val value: String,
    val authorAgentId: String,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

/**
 * Shared Blackboard concorrente onde múltiplos agentes compartilham descobertas,
 * structs, notas de protocolo e contratos em tempo real durante a execução paralela.
 */
class Blackboard {
    private val entries = ConcurrentHashMap<String, BlackboardEntry>()

    fun post(key: String, value: String, author: String): BlackboardEntry {
        val entry = BlackboardEntry(key, value, author)
        entries[key] = entry
        return entry
    }

    fun read(key: String): String? = entries[key]?.value

    fun getAll(): Map<String, BlackboardEntry> = entries.toMap()

    fun getFormattedSummary(): String {
        if (entries.isEmpty()) return "[Blackboard Vazio]"
        return entries.values.joinToString("\n") {
            "• [${it.authorAgentId}] ${it.key}: ${it.value}"
        }
    }

    fun clear() {
        entries.clear()
    }
}
