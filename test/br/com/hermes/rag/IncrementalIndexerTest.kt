package br.com.hermes.rag

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncrementalIndexerTest {

    @Test
    fun `deve calcular hash sha256 de arquivo para detectar alteracoes reais`() {
        val tempFile = File.createTempFile("test_incremental", ".rs")
        tempFile.writeText("pub fn calculate_hash() { 42 }")
        tempFile.deleteOnExit()

        val hash1 = IncrementalIndexer.computeFileHash(tempFile)
        assertTrue(hash1.isNotEmpty())

        // Mesmo conteudo deve gerar o mesmo hash
        val hash2 = IncrementalIndexer.computeFileHash(tempFile)
        assertEquals(hash1, hash2)

        // Conteudo diferente deve gerar hash distinto
        tempFile.writeText("pub fn calculate_hash() { 99 }")
        val hash3 = IncrementalIndexer.computeFileHash(tempFile)
        assertTrue(hash1 != hash3)
    }

    @Test
    fun `deve identificar arquivos modificados em um diretorio baseado em hash`() {
        val tempDir = File.createTempFile("temp_scan_dir", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val fileA = File(tempDir, "file_a.rs").apply { writeText("fn a() {}") }
        val fileB = File(tempDir, "file_b.kt").apply { writeText("fun b() {}") }

        val trackedHashes = mapOf(fileA.absolutePath to IncrementalIndexer.computeFileHash(fileA))

        val modified = IncrementalIndexer.detectModifiedFiles(tempDir, trackedHashes, setOf("rs", "kt"))

        // fileB é novo (não estava no map), deve ser detectado
        assertTrue(modified.any { it.name == "file_b.kt" })
    }
}
