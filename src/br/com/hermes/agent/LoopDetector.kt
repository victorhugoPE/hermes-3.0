package br.com.hermes.agent

import java.util.concurrent.ConcurrentLinkedDeque

enum class LoopCause {
    CONSECUTIVE_IDENTICAL,
    OSCILLATING_CYCLE,
    STAGNATION
}

sealed interface LoopVerdict {
    data object Clear : LoopVerdict
    data class Warning(val message: String) : LoopVerdict
    data class Tripped(
        val cause: LoopCause,
        val message: String,
        val suggestedRemedy: String
    ) : LoopVerdict
}

data class ActionRecord(
    val actionName: String,
    val parameters: String,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val fingerprint: String = "$actionName|$parameters"
}

/**
 * LoopDetector: Monitor de ciclo heurístico para agentes autônomos.
 * Previne que subagentes travem em loops infinitos de busca ou chamadas idênticas.
 */
class LoopDetector(
    val windowSize: Int = 10,
    val repetitionThreshold: Int = 3,
    val cycleLength: Int = 2,
    val cycleRepeatThreshold: Int = 3
) {
    private val history = ConcurrentLinkedDeque<ActionRecord>()

    @Synchronized
    fun recordAction(actionName: String, parameters: String): LoopVerdict {
        val record = ActionRecord(actionName, parameters)
        history.addLast(record)

        while (history.size > windowSize) {
            history.removeFirst()
        }

        val currentList = history.toList()

        // 1. Verificação de Repetição Consecutiva Idêntica (Ex: busca pela mesma coisa 3x)
        if (currentList.size >= repetitionThreshold) {
            val tail = currentList.takeLast(repetitionThreshold)
            val firstFingerprint = tail.first().fingerprint
            val allMatch = tail.all { it.fingerprint == firstFingerprint }
            if (allMatch) {
                return LoopVerdict.Tripped(
                    cause = LoopCause.CONSECUTIVE_IDENTICAL,
                    message = "Repetição consecutiva de $repetitionThreshold ações idênticas detectada: $firstFingerprint",
                    suggestedRemedy = "Interromper busca redundante e avançar para síntese ou mudar estratégia de exploração."
                )
            }
        }

        // 2. Verificação de Ciclo Oscilatório (Ex: A -> B -> A -> B -> A -> B)
        val requiredHistoryForCycle = cycleLength * cycleRepeatThreshold
        if (currentList.size >= requiredHistoryForCycle) {
            val tail = currentList.takeLast(requiredHistoryForCycle)
            val baseCycle = tail.take(cycleLength).map { it.fingerprint }

            var isOscillating = true
            for (i in 1 until cycleRepeatThreshold) {
                val segment = tail.drop(i * cycleLength).take(cycleLength).map { it.fingerprint }
                if (segment != baseCycle) {
                    isOscillating = false
                    break
                }
            }

            if (isOscillating) {
                return LoopVerdict.Tripped(
                    cause = LoopCause.OSCILLATING_CYCLE,
                    message = "Ciclo oscilatório repetido $cycleRepeatThreshold vezes detectado: ${baseCycle.joinToString(" -> ")}",
                    suggestedRemedy = "Quebrar alternância de ferramentas e emitir relatório de descoberta consolidado."
                )
            }
        }

        return LoopVerdict.Clear
    }

    @Synchronized
    fun reset() {
        history.clear()
    }
}
