package br.com.hermes.agent

import br.com.hermes.maestro.resolution.ResolutionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentPoolTest {

    @Test
    fun `blackboard deve armazenar e recuperar descobertas entre agentes`() {
        val blackboard = Blackboard()
        blackboard.post("proto:746", "SessionID RSA blowfish init", "worker-1")
        blackboard.post("render:wgpu", "Pipeline 6 passes FXAA", "worker-2")

        assertEquals("SessionID RSA blowfish init", blackboard.read("proto:746"))
        assertEquals("Pipeline 6 passes FXAA", blackboard.read("render:wgpu"))
        assertEquals(2, blackboard.getAll().size)
    }

    @Test
    fun `agentpool deve executar tarefa unica injetando contexto e gerando proof bundle`() = runTest {
        val pool = AgentPool(workerCount = 3)
        val task = AgentTask("test-01", "revisar regras de FFI e Rust", "skills")

        val result = pool.executeTask(task)

        assertTrue(result.success)
        assertEquals(ResolutionStatus.COMPLETED_VERIFIED, result.proofBundle.status)
        assertNotNull(pool.blackboard.read("context:test-01"))
    }

    @Test
    fun `agentpool deve executar lote de tarefas concorrentes em paralelo`() = runTest {
        val pool = AgentPool(workerCount = 3)
        val tasks = listOf(
            AgentTask("batch-01", "tarefa A"),
            AgentTask("batch-02", "tarefa B"),
            AgentTask("batch-03", "tarefa C")
        )

        val results = pool.executeBatch(tasks)

        assertEquals(3, results.size)
        assertTrue(results.all { it.success })
    }

    @Test
    fun `agentpool deve abortar graciosamente com fallback quando watchdog atingir limite`() = runTest {
        val pool = AgentPool(workerCount = 1)
        // Configurar budget de 2 passos e timeout de 100ms com falha de teste contínua
        val strictBudget = AgentBudget(maxSteps = 2, maxDurationMs = 100)
        val task = AgentTask(
            taskId = "task-timeout",
            description = "deepsearch infinito em 2 repos",
            budget = strictBudget,
            maxRetries = 10,
            simulatedInitialExitCode = 1
        )

        val result = pool.executeTask(task)

        // O watchdog deve impedir as 10 tentativas e forçar encerramento controlado
        assertTrue(result.attempts <= 3)
        assertNotNull(pool.blackboard.read("status:task-timeout"))
    }
}
