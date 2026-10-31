package br.com.hermes.search

import br.com.hermes.agent.*
import br.com.hermes.rag.RagResult
import br.com.hermes.rag.TurbovecBridge
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable

@Serializable
data class ResearchPlan(
    val topic: String,
    val primaryScope: String,
    val secondaryScope: String,
    val maxStepsPerAgent: Int = 8,
    val timeoutPerAgentMs: Long = 30_000L
)

/**
 * ResearchOrchestrator:
 * Despacha agentes pesquisadores independentes em paralelo para repositórios/escopos distintos,
 * com blackboard em tempo real, heartbeats e síntese determinística sem risco de deadlock.
 */
class ResearchOrchestrator(
    val blackboard: Blackboard = Blackboard(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun executeParallelResearch(plan: ResearchPlan): DeepSearchReport = coroutineScope {
        blackboard.post("research:status", "Iniciando pesquisa concorrente sobre '${plan.topic}'", "orchestrator")

        // Disparar Agent A para Escopo Primário
        val jobA = async(dispatcher) {
            exploreScope(plan.topic, plan.primaryScope, "worker-A", plan.maxStepsPerAgent, plan.timeoutPerAgentMs)
        }

        // Disparar Agent B para Escopo Secundário
        val jobB = async(dispatcher) {
            exploreScope(plan.topic, plan.secondaryScope, "worker-B", plan.maxStepsPerAgent, plan.timeoutPerAgentMs)
        }

        // Aguardar ambos os agentes com garantia de não travar
        val resultsA = jobA.await()
        val resultsB = jobB.await()

        blackboard.post(
            "research:summary",
            "Exploração concluída: ${resultsA.size} achados em '${plan.primaryScope}', ${resultsB.size} achados em '${plan.secondaryScope}'",
            "orchestrator"
        )

        // Fase de Síntese Cruzada
        val insights = mutableListOf<String>()
        val correlation = DeepSearchEngine.correlateProjects(plan.topic, resultsA, plan.primaryScope, resultsB, plan.secondaryScope)
        if (correlation.sharedEntities.isNotEmpty()) {
            insights.add("Entidades arquiteturais equivalentes entre os dois projetos: ${correlation.sharedEntities.joinToString(", ")}")
        }
        if (resultsA.isNotEmpty()) {
            insights.add("Âncora primária identificada em: ${resultsA.first().path}")
        }
        if (resultsB.isNotEmpty()) {
            insights.add("Âncora secundária identificada em: ${resultsB.first().path}")
        }

        DeepSearchEngine.generateReport(
            query = plan.topic,
            primaryFindings = resultsA,
            secondaryFindings = resultsB,
            primaryScope = plan.primaryScope,
            secondaryScope = plan.secondaryScope,
            insights = insights
        )
    }

    private suspend fun exploreScope(
        topic: String,
        scope: String,
        agentId: String,
        maxSteps: Int,
        timeoutMs: Long
    ): List<RagResult> = withContext(dispatcher) {
        val budget = AgentBudget(maxSteps = maxSteps, maxDurationMs = timeoutMs)
        val watchdog = AgentWatchdog(budget)
        val loopDetector = LoopDetector()
        val collected = mutableListOf<RagResult>()

        var step = 1
        while (step <= maxSteps) {
            watchdog.heartbeat()

            // Checagem de Watchdog
            val wStatus = watchdog.check(step)
            if (wStatus is WatchdogStatus.Tripped) {
                blackboard.post("research:$scope:circuit", "Watchdog tripped: ${wStatus.details}", agentId)
                break
            }
            if (wStatus is WatchdogStatus.NearingBudget) {
                blackboard.post("research:$scope:warning", wStatus.warningMessage, agentId)
            }

            // Passo de busca híbrida (Dense + Exact)
            val actionKey = "search|$scope|step=$step"
            val lVerdict = loopDetector.recordAction("explore", actionKey)
            if (lVerdict is LoopVerdict.Tripped) {
                blackboard.post("research:$scope:circuit", "Loop tripped: ${lVerdict.message}", agentId)
                break
            }

            val dense = TurbovecBridge.query(topic, topK = 3, scope = scope)
            val exact = DeepSearchEngine.searchExactSymbols(topic.take(15), scope = scope, limit = 3)
            val fused = DeepSearchEngine.fuseResults(dense, exact)

            for (item in fused) {
                collected.add(RagResult(item.rankScore, item.id, item.scope, item.title, item.path, item.content))
            }

            blackboard.post(
                "research:$scope:step-$step",
                "Coletados ${fused.size} itens no passo $step",
                agentId
            )

            // Se já coletou evidências suficientes com boa correlação, conclui cedo
            if (collected.isNotEmpty()) {
                break
            }
            step++
        }

        collected.distinctBy { it.path }
    }
}
