package br.com.hermes.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentWatchdogTest {

    @Test
    fun `watchdog deve retornar status normal quando dentro do budget`() {
        val budget = AgentBudget(maxSteps = 10, maxDurationMs = 5000)
        val watchdog = AgentWatchdog(budget)

        val status = watchdog.check(step = 3)
        assertIs<WatchdogStatus.Normal>(status)
        assertEquals(3, status.currentStep)
        assertEquals(10, status.maxSteps)
    }

    @Test
    fun `watchdog deve emitir warning de sintese ao atingir limiar de 75 por cento dos passos`() {
        val budget = AgentBudget(maxSteps = 10, warningThresholdPct = 0.75)
        val watchdog = AgentWatchdog(budget)

        val status = watchdog.check(step = 8)
        assertIs<WatchdogStatus.NearingBudget>(status)
        assertTrue(status.warningMessage.contains("Atenção"))
        assertTrue(status.suggestImmediateSynthesis)
    }

    @Test
    fun `watchdog deve disparar tripped quando exceder maximo de passos`() {
        val budget = AgentBudget(maxSteps = 5)
        val watchdog = AgentWatchdog(budget)

        val status = watchdog.check(step = 6)
        assertIs<WatchdogStatus.Tripped>(status)
        assertEquals(WatchdogTripReason.STEP_BUDGET_EXCEEDED, status.reason)
    }

    @Test
    fun `watchdog deve detectar timeout de duracao total`() {
        val budget = AgentBudget(maxSteps = 100, maxDurationMs = 50)
        val watchdog = AgentWatchdog(budget)

        Thread.sleep(60)
        val status = watchdog.check(step = 1)
        assertIs<WatchdogStatus.Tripped>(status)
        assertEquals(WatchdogTripReason.TIMEOUT_EXCEEDED, status.reason)
    }

    @Test
    fun `watchdog deve detectar zombie task quando heartbeat nao for atualizado`() {
        val budget = AgentBudget(heartbeatTimeoutMs = 50)
        val watchdog = AgentWatchdog(budget)

        watchdog.heartbeat()
        Thread.sleep(70)

        val status = watchdog.check(step = 1)
        assertIs<WatchdogStatus.StalledZombie>(status)
        assertTrue(status.reason.contains("Heartbeat expirado"))
    }
}
