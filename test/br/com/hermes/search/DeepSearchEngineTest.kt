package br.com.hermes.search

import br.com.hermes.rag.RagResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeepSearchEngineTest {

    @Test
    fun `reciprocal rank fusion deve priorizar documentos com boa classificacao em ambas as buscas`() {
        val denseResults = listOf(
            RagResult(score = 0.9, id = 1, scope = "client-l2-code", title = "Login.kt", path = "src/Login.kt", content = "class Login"),
            RagResult(score = 0.8, id = 2, scope = "client-l2-code", title = "Auth.kt", path = "src/Auth.kt", content = "class Auth"),
            RagResult(score = 0.7, id = 3, scope = "client-l2-code", title = "Crypto.kt", path = "src/Crypto.kt", content = "class Crypto")
        )

        val exactResults = listOf(
            RagResult(score = 1.0, id = 3, scope = "client-l2-code", title = "Crypto.kt", path = "src/Crypto.kt", content = "class Crypto"),
            RagResult(score = 0.9, id = 1, scope = "client-l2-code", title = "Login.kt", path = "src/Login.kt", content = "class Login"),
            RagResult(score = 0.5, id = 4, scope = "client-l2-code", title = "Net.kt", path = "src/Net.kt", content = "class Net")
        )

        val fused = DeepSearchEngine.fuseResults(denseResults, exactResults, k = 60)

        // Doc 1 e 3 apareceram no topo em ambos; doc 1 (pos 0 dense, pos 1 exact) ou doc 3 devem liderar
        assertTrue(fused.isNotEmpty())
        assertEquals("Login.kt", fused.first().title)
        assertEquals(4, fused.size) // 1, 2, 3, 4 sem duplicatas
    }

    @Test
    fun `cross repo search deve agrupar e correlacionar achados de dois projetos distintos`() {
        val projectAItems = listOf(
            RagResult(score = 0.85, id = 10, scope = "client-l2-code", title = "PawnMove.rs", path = "src/pawn/move.rs", content = "fn move_to_pawn(target: u32)")
        )
        val projectBItems = listOf(
            RagResult(score = 0.82, id = 20, scope = "servers-java-kt", title = "MoveToPawn.java", path = "network/serverpackets/MoveToPawn.java", content = "public class MoveToPawn extends L2GameServerPacket")
        )

        val comparison = DeepSearchEngine.correlateProjects(
            query = "MoveToPawn opcode e protocolo",
            resultsA = projectAItems,
            scopeA = "client-l2-code",
            resultsB = projectBItems,
            scopeB = "servers-java-kt"
        )

        assertEquals(1, comparison.projectAEvidences.size)
        assertEquals(1, comparison.projectBEvidences.size)
        assertTrue(comparison.sharedEntities.any { it.contains("pawn", ignoreCase = true) || it.contains("move", ignoreCase = true) })
    }

    @Test
    fun `synthesis report deve gerar relatorio estruturado com secoes de evidencias e conclusao`() {
        val report = DeepSearchEngine.generateReport(
            query = "criptografia blowfish e autenticacao",
            primaryFindings = listOf(
                RagResult(score = 0.9, id = 1, scope = "client-l2-code", title = "Crypt.rs", path = "src/crypt.rs", content = "Blowfish engine with RSA handshake")
            ),
            secondaryFindings = listOf(
                RagResult(score = 0.88, id = 2, scope = "servers-java-kt", title = "NewCrypt.java", path = "net/NewCrypt.java", content = "Blowfish key expansion matrix")
            ),
            insights = listOf("Ambos os projetos usam a mesma matriz S-box de Blowfish.")
        )

        assertNotNull(report)
        assertTrue(report.markdownSummary.contains("Dossiê DeepSearch"))
        assertTrue(report.markdownSummary.contains("client-l2-code"))
        assertTrue(report.markdownSummary.contains("servers-java-kt"))
        assertTrue(report.markdownSummary.contains("S-box"))
    }
}
