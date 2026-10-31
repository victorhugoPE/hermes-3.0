package br.com.hermes.agent

import br.com.hermes.maestro.governance.ChangeGovernance
import br.com.hermes.maestro.governance.GovernanceDecision
import br.com.hermes.maestro.resolution.ProofBundle
import br.com.hermes.maestro.resolution.ResolutionEngine
import br.com.hermes.maestro.resolution.ResolutionStatus
import br.com.hermes.rag.TurbovecBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable

@Serializable
data class AgentTask(
    val taskId: String,
    val description: String,
    val scopeFilter: String? = null,
    val maxRetries: Int = 3,
    val budget: AgentBudget = AgentBudget(),
    val simulatedInitialExitCode: Int = 0
)

@Serializable
data class AgentTaskResult(
    val taskId: String,
    val agentId: String,
    val proofBundle: ProofBundle,
    val contextInjected: List<String>,
    val attempts: Int,
    val success: Boolean,
    val tripReason: String? = null
)

/**
 * AgentPool: Concorrência real via Kotlin Coroutines.
 * Gerencia um pool de workers concorrentes processando tarefas assíncronas,
 * com injeção automática de RAG Turbovec e validação pelo Maestro Resolution Engine.
 */
class AgentPool(
    val workerCount: Int = 3,
    val blackboard: Blackboard = Blackboard(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val taskChannel = Channel<AgentTask>(Channel.BUFFERED)

    suspend fun executeTask(task: AgentTask): AgentTaskResult = withContext(dispatcher) {
        // 1. Injeção de Contexto do RAG Turbovec (Skills + Regras)
        val ragMatches = TurbovecBridge.query(task.description, topK = 2, scope = task.scopeFilter)
        val injectedContext = ragMatches.map { "[${it.scope}] ${it.title}: ${it.content.take(150)}" }

        // Compartilhar no Blackboard a skill e contexto selecionados
        if (ragMatches.isNotEmpty()) {
            blackboard.post(
                key = "context:${task.taskId}",
                value = "RAG matched: " + ragMatches.first().title,
                author = "orchestrator"
            )
        }

        // 2. Loop de Auto-Reparo e Execução da Tarefa com Watchdog & Loop Detection
        val watchdog = AgentWatchdog(task.budget)
        val loopDetector = LoopDetector()
        var attempt = 1
        var currentExitCode = task.simulatedInitialExitCode
        val modifiedFiles = mutableListOf<String>()
        var tripReason: String? = null

        while (attempt <= task.maxRetries) {
            watchdog.heartbeat()

            // Checagem de Watchdog por passos e tempo
            val watchdogStatus = watchdog.check(step = attempt)
            when (watchdogStatus) {
                is WatchdogStatus.Tripped -> {
                    tripReason = "Watchdog tripped: ${watchdogStatus.details}"
                    blackboard.post("status:${task.taskId}", tripReason, "watchdog")
                    break
                }
                is WatchdogStatus.StalledZombie -> {
                    tripReason = "Zombie detected: ${watchdogStatus.reason}"
                    blackboard.post("status:${task.taskId}", tripReason, "watchdog")
                    break
                }
                is WatchdogStatus.NearingBudget -> {
                    blackboard.post("warning:${task.taskId}", watchdogStatus.warningMessage, "watchdog")
                }
                is WatchdogStatus.Normal -> {
                    // Normal execution
                }
            }

            // Checagem de Loop Detector para a ação/tentativa atual
            val loopVerdict = loopDetector.recordAction("execute_attempt", "attempt=$attempt|task=${task.taskId}")
            if (loopVerdict is LoopVerdict.Tripped) {
                tripReason = "Loop tripped: ${loopVerdict.message}"
                blackboard.post("status:${task.taskId}", tripReason, "loop-detector")
                break
            }

            // Validar governança Maestro se arquivos forem tocados
            val simulatedPath = "crates/l2-net/src/lib.rs"
            val decision = ChangeGovernance.validateFileWrite(simulatedPath)
            if (decision is GovernanceDecision.Allowed) {
                modifiedFiles.add(simulatedPath)
            }

            val proof = ResolutionEngine.verifyDelivery(
                taskId = task.taskId,
                modifiedFiles = modifiedFiles,
                testExitCode = currentExitCode,
                linterExitCode = 0
            )

            if (proof.status == ResolutionStatus.COMPLETED_VERIFIED) {
                return@withContext AgentTaskResult(
                    taskId = task.taskId,
                    agentId = "worker-${(1..workerCount).random()}",
                    proofBundle = proof,
                    contextInjected = injectedContext,
                    attempts = attempt,
                    success = true
                )
            }

            // Se não passou, auto-reparo na próxima tentativa (a menos que a task force erro constante)
            attempt++
            if (task.simulatedInitialExitCode == 0) {
                currentExitCode = 0 // Auto-reparo corrige os testes normais
            }
        }

        // Falha ou encerramento pelo Watchdog/LoopDetector
        val finalProof = ResolutionEngine.verifyDelivery(
            taskId = task.taskId,
            modifiedFiles = modifiedFiles,
            testExitCode = 1,
            linterExitCode = 0
        )

        AgentTaskResult(
            taskId = task.taskId,
            agentId = if (tripReason != null) "worker-circuit-broken" else "worker-failed",
            proofBundle = finalProof,
            contextInjected = injectedContext,
            attempts = attempt,
            success = false,
            tripReason = tripReason
        )
    }

    suspend fun executeBatch(tasks: List<AgentTask>): List<AgentTaskResult> = coroutineScope {
        tasks.map { task ->
            async(dispatcher) {
                executeTask(task)
            }
        }.awaitAll()
    }
}
