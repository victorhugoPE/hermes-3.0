package br.com.hermes.rag

import java.io.File
import java.security.MessageDigest

object IncrementalIndexer {
    private val pythonEnv = File("/Users/victorhugo/Downloads/BrProject-Native/.sentinel/venv/bin/python3")
    private val incrementalScript = File("scripts/ingest/hermes_incremental.py")

    fun computeFileHash(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead = stream.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = stream.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun detectModifiedFiles(
        directory: File,
        existingHashes: Map<String, String>,
        allowedExtensions: Set<String> = setOf("rs", "kt", "java", "md", "ts", "wgsl")
    ): List<File> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()

        val modified = mutableListOf<File>()
        directory.walkTopDown().maxDepth(8).forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in allowedExtensions) {
                    val currentHash = computeFileHash(file)
                    val previousHash = existingHashes[file.absolutePath]
                    if (previousHash == null || previousHash != currentHash) {
                        modified.add(file)
                    }
                }
            }
        }
        return modified
    }

    fun upsertFile(file: File, scope: String): Boolean {
        if (!file.exists() || !pythonEnv.exists() || !incrementalScript.exists()) return false

        return runCatching {
            val process = ProcessBuilder(
                pythonEnv.absolutePath,
                incrementalScript.absolutePath,
                "--file", file.absolutePath,
                "--scope", scope
            ).directory(File("."))
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            exitCode == 0
        }.getOrDefault(false)
    }
}
