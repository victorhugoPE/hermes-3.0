package br.com.hermes.rag

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class RagResult(
    val score: Double,
    val id: Long,
    val scope: String,
    val title: String,
    val path: String,
    val content: String,
    val ext: String? = null
)

object TurbovecBridge {
    private val pythonEnv = File("/Users/victorhugo/Downloads/BrProject-Native/.sentinel/venv/bin/python3")
    private val queryScript = File("scripts/ingest/hermes_query.py")
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun isReady(): Boolean {
        val indexFile = File(".hermes/rag/hermes_index.tvim")
        val dbFile = File(".hermes/rag/hermes_map.db")
        return indexFile.exists() && dbFile.exists() && pythonEnv.exists()
    }

    fun query(text: String, topK: Int = 3, scope: String? = null): List<RagResult> {
        if (!isReady()) return emptyList()

        val cmd = mutableListOf(
            pythonEnv.absolutePath,
            queryScript.absolutePath,
            text,
            "--top", topK.toString()
        )
        if (scope != null) {
            cmd.add("--scope")
            cmd.add(scope)
        }

        return runCatching {
            val process = ProcessBuilder(cmd)
                .directory(File("."))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            // Parse output simplificado ou leitura do SQLite
            // Aqui fazemos parse das linhas de retorno
            parseQueryOutput(output)
        }.getOrDefault(emptyList())
    }

    private fun parseQueryOutput(rawOutput: String): List<RagResult> {
        val results = mutableListOf<RagResult>()
        val blocks = rawOutput.split("----------------------------------------------------------------------")

        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.isEmpty() || !lines[0].startsWith("[")) continue

            // [1] Score: 0.6119 | Escopo: skills | brproject-rust-lua-ffi
            val header = lines[0]
            val scoreMatch = Regex("Score:\\s+([0-9.]+)").find(header)
            val scopeMatch = Regex("Escopo:\\s+([a-zA-Z0-9_-]+)").find(header)
            val score = scoreMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val scope = scopeMatch?.groupValues?.get(1) ?: "general"

            val pathLine = lines.getOrNull(1)?.replace("Caminho:", "")?.trim() ?: ""
            val snippetLine = lines.getOrNull(2)?.replace("Trecho:", "")?.trim() ?: ""

            results.add(
                RagResult(
                    score = score,
                    id = 0L,
                    scope = scope,
                    title = header.substringAfterLast("|").trim(),
                    path = pathLine,
                    content = snippetLine
                )
            )
        }
        return results
    }
}
