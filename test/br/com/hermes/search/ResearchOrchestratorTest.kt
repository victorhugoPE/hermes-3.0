package br.com.hermes.search

import br.com.hermes.agent.Blackboard
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ResearchOrchestratorTest {

    @Test
    fun `orchestrator deve despachar 2 agentes concorrentes para escopos distintos e sintetizar sem travar`() = runTest {
        val blackboard = Blackboard()
        val orchestrator = ResearchOrchestrator(blackboard = blackboard)

        val plan = ResearchPlan(
            topic = "autenticacao e sessao",
            primaryScope = "client-l2-code",
            secondaryScope = "servers-java-kt",
            maxStepsPerAgent = 5,
            timeoutPerAgentMs = 5000
        )

        val report = orchestrator.executeParallelResearch(plan)

        assertNotNull(report)
        assertEquals("client-l2-code", report.primaryScope)
        assertEquals("servers-java-kt", report.secondaryScope)
        assertTrue(report.markdownSummary.contains("Dossiê DeepSearch"))

        // Validar que ambos os agentes postaram seus achados no Blackboard compartilhado
        val allEntries = blackboard.getAll()
        assertTrue(allEntries.keys.any { it.startsWith("research:client-l2-code") })
        assertTrue(allEntries.keys.any { it.startsWith("research:servers-java-kt") })
    }
}
