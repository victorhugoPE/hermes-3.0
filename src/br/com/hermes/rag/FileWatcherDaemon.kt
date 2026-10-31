package br.com.hermes.rag

import java.io.File
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FileWatcherDaemon: Monitor contínuo de arquivos do ecossistema.
 * Utiliza WatchService nativo do macOS para sincronizar incrementalmente qualquer alteração
 * de código direto no Turbovec e SQLite em tempo real sem reindexação manual.
 */
object FileWatcherDaemon {
    private val running = AtomicBoolean(false)
    private val validExtensions = setOf("rs", "kt", "java", "ts", "js", "wgsl", "md", "toml", "json", "html", "css")
    private val ignoreDirs = setOf(".git", "target", "build", ".gradle", "node_modules", "dist", "assets", "__pycache__")
    private val debounceMap = ConcurrentHashMap<String, Long>()

    val watchedPaths = listOf(
        File("/Users/victorhugo/Documents/Client L2"),
        File("/Users/victorhugo/Documents/elbera/Lineage2Elbera")
    )

    fun isIndexable(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in validExtensions
    }

    fun resolveScope(file: File): String {
        val path = file.absolutePath
        return when {
            path.contains("Client L2/docs") -> "client-l2-kb"
            path.contains("Client L2") -> "client-l2-code"
            path.contains("Lineage2Elbera/docs") -> "elbera-docs"
            path.contains("Lineage2Elbera") -> "elbera-code"
            path.contains("hermes") -> "hermes-core"
            else -> "general"
        }
    }

    fun startWatching(once: Boolean = false) {
        running.set(true)
        println("👀 FileWatcherDaemon iniciado!")
        println("   • Monitorando diretórios:")
        watchedPaths.filter { it.exists() }.forEach { println("     - ${it.absolutePath}") }
        println("   • Extensões: ${validExtensions.joinToString(", ")}")
        println("   • Sincronização automática via IncrementalIndexer ativada.\n")

        val watchService = FileSystems.getDefault().newWatchService()
        val keyMap = mutableMapOf<WatchKey, Path>()

        // Registrar recursivamente pastas principais
        for (root in watchedPaths.filter { it.exists() }) {
            root.walkTopDown().maxDepth(5).filter { it.isDirectory && it.name !in ignoreDirs }.forEach { dir ->
                runCatching {
                    val p = dir.toPath()
                    val key = p.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY
                    )
                    keyMap[key] = p
                }
            }
        }

        println("✅ ${keyMap.size} diretórios registrados no WatchService. Aguardando eventos de código...")

        if (once) {
            watchService.close()
            return
        }

        while (running.get()) {
            val key = try {
                watchService.take()
            } catch (e: Exception) {
                break
            }

            val dirPath = keyMap[key] ?: continue
            for (event in key.pollEvents()) {
                val kind = event.kind()
                if (kind == StandardWatchEventKinds.OVERFLOW) continue

                val filename = event.context() as? Path ?: continue
                val fullFile = dirPath.resolve(filename).toFile()

                if (fullFile.isFile && isIndexable(fullFile)) {
                    val now = System.currentTimeMillis()
                    val lastSync = debounceMap[fullFile.absolutePath] ?: 0L
                    if (now - lastSync > 1500L) { // Debounce de 1.5s
                        debounceMap[fullFile.absolutePath] = now
                        val scope = resolveScope(fullFile)
                        println("⚡ Alteração detectada em '${fullFile.name}'. Sincronizando no Turbovec [Escopo: $scope]...")
                        val ok = IncrementalIndexer.upsertFile(fullFile, scope)
                        if (ok) {
                            println("   ✅ '${fullFile.name}' indexado e pronto para busca imediata.")
                        }
                    }
                }
            }
            val valid = key.reset()
            if (!valid) {
                keyMap.remove(key)
            }
        }
    }

    fun stopWatching() {
        running.set(false)
    }
}
