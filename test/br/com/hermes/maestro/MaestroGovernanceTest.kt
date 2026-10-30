package br.com.hermes.maestro

import br.com.hermes.maestro.governance.ChangeGovernance
import br.com.hermes.maestro.governance.GovernanceDecision
import br.com.hermes.maestro.resolution.ResolutionEngine
import br.com.hermes.maestro.resolution.ResolutionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaestroGovernanceTest {

    @Test
    fun `deve bloquear edicao em arquivos sensiveis como ponto env ou chaves`() {
        val resultEnv = ChangeGovernance.validateFileWrite("/projeto/.env")
        val resultKey = ChangeGovernance.validateFileWrite("/projeto/secret.key")
        val resultGit = ChangeGovernance.validateFileWrite("/projeto/.git/config")

        assertTrue(resultEnv is GovernanceDecision.Denied)
        assertTrue(resultKey is GovernanceDecision.Denied)
        assertTrue(resultGit is GovernanceDecision.Denied)
    }

    @Test
    fun `deve permitir edicao em arquivos autorizados de codigo`() {
        val resultRs = ChangeGovernance.validateFileWrite("crates/l2-net/src/lib.rs")
        val resultKt = ChangeGovernance.validateFileWrite("src/br/com/hermes/Main.kt")

        assertEquals(GovernanceDecision.Allowed, resultRs)
        assertEquals(GovernanceDecision.Allowed, resultKt)
    }

    @Test
    fun `deve emitir COMPLETED_VERIFIED quando testes e linter passam com mudancas reais`() {
        val bundle = ResolutionEngine.verifyDelivery(
            taskId = "T-100",
            modifiedFiles = listOf("crates/l2-ui/src/lib.rs"),
            testExitCode = 0,
            linterExitCode = 0
        )

        assertEquals(ResolutionStatus.COMPLETED_VERIFIED, bundle.status)
        assertTrue(bundle.testsExecuted)
    }

    @Test
    fun `deve exigir reparo quando os testes falham com codigo diferente de zero`() {
        val bundle = ResolutionEngine.verifyDelivery(
            taskId = "T-101",
            modifiedFiles = listOf("crates/l2-crypto/src/lib.rs"),
            testExitCode = 1,
            linterExitCode = 0
        )

        assertEquals(ResolutionStatus.REPAIR_REQUIRED, bundle.status)
    }
}
