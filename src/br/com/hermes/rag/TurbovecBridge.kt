package br.com.hermes.rag

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

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
    private val hermesHome = File("/Users/victorhugo/Documents/hermes-3.0")
    private val pythonEnv = File("/Users/victorhugo/Downloads/BrProject-Native/.sentinel/venv/bin/python3")
    private val queryScript = File(hermesHome, "scripts/ingest/hermes_query.py")
    private val socketFile = File("/tmp/hermes_rag.sock")
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun isReady(): Boolean {
        val indexFile = File(hermesHome, ".hermes/rag/hermes_index.tvim")
        val dbFile = File(hermesHome, ".hermes/rag/hermes_map.db")
        return (indexFile.exists() && dbFile.exists() && pythonEnv.exists()) || socketFile.exists()
    }

    fun query(text: String, topK: Int = 3, scope: String? = null): List<RagResult> {
        // 1. Tentar consulta ultrarrápida via Unix Domain Socket (sem cold start de processo)
        val socketResults = queryViaSocket(text, topK, scope)
        if (socketResults != null) {
            return socketResults
        }

        // 2. Fallback caso o daemon não esteja rodando
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
            parseQueryOutput(output)
        }.getOrDefault(emptyList())
    }

    private fun queryViaSocket(text: String, topK: Int, scope: String?): List<RagResult>? {
        if (!socketFile.exists()) return null

        return runCatching {
            val address = UnixDomainSocketAddress.of(socketFile.toPath())
            SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
                channel.connect(address)

                val payload = buildString {
                    append("{\"query\": ")
                    append(Json.encodeToString(text))
                    append(", \"top_k\": ").append(topK)
                    if (scope != null) {
                        append(", \"scope\": ").append(Json.encodeToString(scope))
                    }
                    append("}")
                }

                val sendBuffer = ByteBuffer.wrap(payload.toByteArray(Charsets.UTF_8))
                while (sendBuffer.hasRemaining()) {
                    channel.write(sendBuffer)
                }

                val receiveBuffer = ByteBuffer.allocate(65536)
                val outputBytes = mutableListOf<Byte>()
                val startRead = System.currentTimeMillis()

                while (System.currentTimeMillis() - startRead < 3000) {
                    val bytesRead = channel.read(receiveBuffer)
                    if (bytesRead > 0) {
                        receiveBuffer.flip()
                        for (i in 0 until bytesRead) {
                            outputBytes.add(receiveBuffer.get())
                        }
                        receiveBuffer.clear()
                        // Se o JSON foi completamente lido e termina com '}', encerra leitura
                        if (outputBytes.isNotEmpty() && outputBytes.last() == '}'.code.toByte()) {
                            break
                        }
                    } else if (bytesRead == -1) {
                        break
                    }
                }

                val jsonString = String(outputBytes.toByteArray(), Charsets.UTF_8)
                if (!jsonString.trim().startsWith("{")) return null
                val rootJson = jsonParser.parseToJsonElement(jsonString).jsonObject
                val resultsArray = rootJson["results"]?.jsonArray ?: return null

                resultsArray.map { item ->
                    val obj = item.jsonObject
                    RagResult(
                        score = obj["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                        scope = obj["scope"]?.jsonPrimitive?.content ?: "general",
                        title = obj["title"]?.jsonPrimitive?.content ?: "",
                        path = obj["path"]?.jsonPrimitive?.content ?: "",
                        content = obj["content"]?.jsonPrimitive?.content ?: "",
                        ext = obj["ext"]?.jsonPrimitive?.content
                    )
                }
            }
        }.getOrNull()
    }

    private fun parseQueryOutput(rawOutput: String): List<RagResult> {
        val results = mutableListOf<RagResult>()
        val blocks = rawOutput.split("----------------------------------------------------------------------")

        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.isEmpty() || !lines[0].startsWith("[")) continue

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
