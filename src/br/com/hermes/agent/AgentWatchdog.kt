package br.com.hermes.agent

import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicLong

enum class WatchdogTripReason {
    STEP_BUDGET_EXCEEDED,
    TIMEOUT_EXCEEDED,
    HEARTBEAT_EXPIRED
}

sealed interface WatchdogStatus {
    data class Normal(
        val currentStep: Int,
        val maxSteps: Int,
        val elapsedMs: Long
    ) : WatchdogStatus

    data class NearingBudget(
        val currentStep: Int,
        val maxSteps: Int,
        val warningMessage: String,
        val suggestImmediateSynthesis: Boolean = true
    ) : WatchdogStatus

    data class Tripped(
        val reason: WatchdogTripReason,
        val details: String
    ) : WatchdogStatus

    data class StalledZombie(
        val reason: String,
        val lastHeartbeatAgoMs: Long
    ) : WatchdogStatus
}

@Serializable
data class AgentBudget(
    val maxSteps: Int = 15,
    val maxDurationMs: Long = 180_000L, // 3 minutos por tarefa individual
    val warningThresholdPct: Double = 0.75,
    val heartbeatTimeoutMs: Long = 60_000L // 1 minuto sem sinal = zombie
)

/**
 * AgentWatchdog: Fiscaliza consumo de tempo, passos e vivacidade dos agentes.
 * Garante que uma tarefa nunca ultrapasse limites orçamentários nem fique suspensa.
 */
class AgentWatchdog(
    val budget: AgentBudget = AgentBudget()
) {
    private val startTimeMs: Long = System.currentTimeMillis()
    private val lastHeartbeatTimeMs = AtomicLong(startTimeMs)

    fun heartbeat() {
        lastHeartbeatTimeMs.set(System.currentTimeMillis())
    }

    fun check(step: Int): WatchdogStatus {
        val now = System.currentTimeMillis()
        val elapsed = now - startTimeMs
        val sinceHeartbeat = now - lastHeartbeatTimeMs.get()

        // 1. Verificar Zombie / Heartbeat expirado
        if (sinceHeartbeat > budget.heartbeatTimeoutMs) {
            return WatchdogStatus.StalledZombie(
                reason = "Heartbeat expirado após ${sinceHeartbeat}ms sem resposta do agente.",
                lastHeartbeatAgoMs = sinceHeartbeat
            )
        }

        // 2. Verificar Timeout de Duração Total
        if (elapsed > budget.maxDurationMs) {
            return WatchdogStatus.Tripped(
                reason = WatchdogTripReason.TIMEOUT_EXCEEDED,
                details = "Tempo limite atingido: ${elapsed}ms > ${budget.maxDurationMs}ms."
            )
        }

        // 3. Verificar Limite de Passos
        if (step > budget.maxSteps) {
            return WatchdogStatus.Tripped(
                reason = WatchdogTripReason.STEP_BUDGET_EXCEEDED,
                details = "Limite de passos excedido: $step > ${budget.maxSteps}."
            )
        }

        // 4. Verificar Proximidade do Limite (Warning de Emergência para Sintetizar)
        val warningStep = (budget.maxSteps * budget.warningThresholdPct).toInt()
        if (step >= warningStep) {
            return WatchdogStatus.NearingBudget(
                currentStep = step,
                maxSteps = budget.maxSteps,
                warningMessage = "Atenção: 75% do orçamento de passos atingido ($step/${budget.maxSteps}). Conclua as buscas e sintetize os resultados agora.",
                suggestImmediateSynthesis = true
            )
        }

        return WatchdogStatus.Normal(step, budget.maxSteps, elapsed)
    }
}
