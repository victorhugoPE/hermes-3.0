package br.com.hermes.rag

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileWatcherDaemonTest {

    @Test
    fun `deve identificar o escopo correto pelo caminho do arquivo`() {
        val clientL2File = File("/Users/victorhugo/Documents/Client L2/crates/l2-net/src/lib.rs")
        val elberaFile = File("/Users/victorhugo/Documents/elbera/Lineage2Elbera/editor/world/js/app.js")
        val unknownFile = File("/tmp/test.txt")

        assertEquals("client-l2-code", FileWatcherDaemon.resolveScope(clientL2File))
        assertEquals("elbera-code", FileWatcherDaemon.resolveScope(elberaFile))
        assertEquals("general", FileWatcherDaemon.resolveScope(unknownFile))
    }

    @Test
    fun `deve filtrar apenas extensoes relevantes de codigo`() {
        assertTrue(FileWatcherDaemon.isIndexable(File("main.rs")))
        assertTrue(FileWatcherDaemon.isIndexable(File("Server.kt")))
        assertTrue(FileWatcherDaemon.isIndexable(File("shader.wgsl")))
        assertTrue(FileWatcherDaemon.isIndexable(File("script.js")))
        assertTrue(!FileWatcherDaemon.isIndexable(File("image.png")))
        assertTrue(!FileWatcherDaemon.isIndexable(File("build.class")))
    }
}
