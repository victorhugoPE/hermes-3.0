package br.com.hermes.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyGraphExpanderTest {

    @Test
    fun `deve extrair dependencias e modulos de codigo Rust`() {
        val rustCode = """
            //! Protocol module
            pub mod crypt;
            pub mod packets;
            use crate::crypt::BlowfishKey;
            use super::session::SessionState;
        """.trimIndent()

        val deps = DependencyGraphExpander.extractReferences(rustCode, ext = "rs")

        assertTrue(deps.contains("crypt"))
        assertTrue(deps.contains("packets"))
        assertTrue(deps.contains("session"))
    }

    @Test
    fun `deve extrair pacotes e imports de codigo Java e Kotlin`() {
        val javaCode = """
            package l2.login.crypt;
            import java.io.IOException;
            import l2.commons.util.Rnd;
            import l2.login.network.L2LoginClient;
        """.trimIndent()

        val deps = DependencyGraphExpander.extractReferences(javaCode, ext = "java")

        assertTrue(deps.contains("Rnd"))
        assertTrue(deps.contains("L2LoginClient"))
    }

    @Test
    fun `deve ranquear e enriquecer evidencias com os nos conectados do grafo`() {
        val primary = DeepSearchEvidence(
            rankScore = 0.9,
            originalScore = 0.9,
            id = 1,
            scope = "client-l2-code",
            title = "lib.rs",
            path = "crates/l2-net/src/lib.rs",
            content = "pub mod crypt; use crate::crypt::Blowfish;",
            sourceMethod = "dense"
        )

        val graph = DependencyGraphExpander.expandEvidence(primary)
        assertEquals(1, graph.rootEvidence.id)
        assertTrue(graph.connectedSymbols.contains("crypt"))
    }
}
