package br.com.hermes.search

import br.com.hermes.agent.AgentBudget
import br.com.hermes.agent.AgentWatchdog
import br.com.hermes.agent.LoopDetector
import br.com.hermes.agent.LoopVerdict
import br.com.hermes.rag.RagResult
import br.com.hermes.rag.TurbovecBridge
import kotlinx.serialization.Serializable
import java.io.File
import java.sql.DriverManager

@Serializable
data class DeepSearchEvidence(
    val rankScore: Double,
    val originalScore: Double,
    val id: Long,
    val scope: String,
    val title: String,
    val path: String,
    val content: String,
    val sourceMethod: String // "dense", "exact", ou "fused"
)

@Serializable
data class CrossRepoComparison(
    val query: String,
    val scopeA: String,
    val scopeB: String,
    val projectAEvidences: List<DeepSearchEvidence>,
    val projectBEvidences: List<DeepSearchEvidence>,
    val sharedEntities: List<String>
)

@Serializable
data class DeepSearchReport(
    val query: String,
    val primaryScope: String,
    val secondaryScope: String? = null,
    val primaryEvidences: List<DeepSearchEvidence>,
    val secondaryEvidences: List<DeepSearchEvidence>,
    val keyEntities: List<String>,
    val insights: List<String>,
    val generatedAtEpochMs: Long = System.currentTimeMillis(),
    val markdownSummary: String
)

object DeepSearchEngine {
    private val dbFile = File(".hermes/rag/hermes_map.db")

    /**
     * Reciprocal Rank Fusion (RRF):
     * Combina os rankings da busca densa (Turbovec 4-bit) com a busca exata (Symbols/SQL)
     * garantindo que acertos em ambas as buscas ganhem pontuação exponencialmente superior.
     */
    fun fuseResults(
        dense: List<RagResult>,
        exact: List<RagResult>,
        k: Int = 60
    ): List<DeepSearchEvidence> {
        val rrfScores = mutableMapOf<String, Double>()
        val evidenceMap = mutableMapOf<String, RagResult>()
        val sourceMap = mutableMapOf<String, MutableSet<String>>()

        // Pontuação do ranking denso
        dense.forEachIndexed { rank, item ->
            val key = "${item.scope}|${item.path}|${item.title}"
            val score = 1.0 / (k + rank + 1)
            rrfScores[key] = (rrfScores[key] ?: 0.0) + score
            evidenceMap[key] = item
            sourceMap.computeIfAbsent(key) { mutableSetOf() }.add("dense")
        }

        // Pontuação do ranking exato
        exact.forEachIndexed { rank, item ->
            val key = "${item.scope}|${item.path}|${item.title}"
            val score = 1.0 / (k + rank + 1)
            rrfScores[key] = (rrfScores[key] ?: 0.0) + score
            evidenceMap[key] = item
            sourceMap.computeIfAbsent(key) { mutableSetOf() }.add("exact")
        }

        return rrfScores.entries
            .sortedByDescending { it.value }
            .map { (key, score) ->
                val raw = evidenceMap.getValue(key)
                val methods = sourceMap[key] ?: emptySet()
                val sourceDesc = if (methods.size > 1) "fused(dense+exact)" else methods.firstOrNull() ?: "dense"

                DeepSearchEvidence(
                    rankScore = score,
                    originalScore = raw.score,
                    id = raw.id,
                    scope = raw.scope,
                    title = raw.title,
                    path = raw.path,
                    content = raw.content,
                    sourceMethod = sourceDesc
                )
            }
    }

    /**
     * Busca de símbolos exatos no SQLite (tokens, nomes de classe, structs, métodos)
     */
    fun searchExactSymbols(term: String, scope: String? = null, limit: Int = 10): List<RagResult> {
        if (!dbFile.exists()) return emptyList()

        val results = mutableListOf<RagResult>()
        runCatching {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                val sql = StringBuilder("SELECT id, scope, title, path, content, ext FROM chunks WHERE ")
                val params = mutableListOf<String>()

                if (scope != null) {
                    sql.append("scope = ? AND ")
                    params.add(scope)
                }
                sql.append("(title LIKE ? OR content LIKE ? OR path LIKE ?) LIMIT ?")
                params.add("%$term%")
                params.add("%$term%")
                params.add("%$term%")
                params.add(limit.toString())

                conn.prepareStatement(sql.toString()).use { stmt ->
                    for (i in params.indices) {
                        stmt.setString(i + 1, params[i])
                    }
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        results.add(
                            RagResult(
                                score = 1.0,
                                id = rs.getLong("id"),
                                scope = rs.getString("scope"),
                                title = rs.getString("title"),
                                path = rs.getString("path"),
                                content = rs.getString("content").take(300),
                                ext = rs.getString("ext")
                            )
                        )
                    }
                }
            }
        }
        return results
    }

    /**
     * Cruzamento e correlação semântica entre dois escopos/projetos
     */
    fun correlateProjects(
        query: String,
        resultsA: List<RagResult>,
        scopeA: String,
        resultsB: List<RagResult>,
        scopeB: String
    ): CrossRepoComparison {
        val evidencesA = resultsA.map {
            DeepSearchEvidence(it.score, it.score, it.id, it.scope, it.title, it.path, it.content, "dense")
        }
        val evidencesB = resultsB.map {
            DeepSearchEvidence(it.score, it.score, it.id, it.scope, it.title, it.path, it.content, "dense")
        }

        // Identificar entidades compartilhadas procurando palavras-chave comuns de 4+ letras
        val wordsA = resultsA.flatMap { extractKeywords(it.title + " " + it.content) }.toSet()
        val wordsB = resultsB.flatMap { extractKeywords(it.title + " " + it.content) }.toSet()
        val shared = wordsA.intersect(wordsB).filter { it.length >= 4 }.take(10)

        return CrossRepoComparison(
            query = query,
            scopeA = scopeA,
            scopeB = scopeB,
            projectAEvidences = evidencesA,
            projectBEvidences = evidencesB,
            sharedEntities = shared
        )
    }

    /**
     * Gera o relatório consolidado final em Markdown, estruturado e pronto para visualização
     */
    fun generateReport(
        query: String,
        primaryFindings: List<RagResult>,
        secondaryFindings: List<RagResult> = emptyList(),
        primaryScope: String = primaryFindings.firstOrNull()?.scope ?: "general",
        secondaryScope: String? = secondaryFindings.firstOrNull()?.scope,
        insights: List<String> = emptyList()
    ): DeepSearchReport {
        val primaryEvidences = primaryFindings.map {
            DeepSearchEvidence(it.score, it.score, it.id, it.scope, it.title, it.path, it.content, "primary")
        }
        val secondaryEvidences = secondaryFindings.map {
            DeepSearchEvidence(it.score, it.score, it.id, it.scope, it.title, it.path, it.content, "secondary")
        }

        val allText = (primaryFindings + secondaryFindings).joinToString(" ") { it.title + " " + it.content }
        val keyEntities = extractKeywords(allText).take(8)

        val sb = StringBuilder()
        sb.append("# 🔬 Dossiê DeepSearch Hermes 3.0\n\n")
        sb.append("**Consulta:** `$query`  \n")
        sb.append("**Projeto/Escopo Primário:** `$primaryScope` (${primaryEvidences.size} evidências)  \n")
        if (secondaryScope != null) {
            sb.append("**Projeto/Escopo Secundário:** `$secondaryScope` (${secondaryEvidences.size} evidências)  \n")
        }
        sb.append("\n---\n\n")

        sb.append("## 1. Entidades-Chave Detectadas\n")
        keyEntities.forEach { sb.append("- `$it`\n") }
        sb.append("\n")

        sb.append("## 2. Evidências no Projeto Primário (`$primaryScope`)\n")
        primaryEvidences.take(5).forEachIndexed { i, ev ->
            val node = DependencyGraphExpander.expandEvidence(ev)
            sb.append("### [${i + 1}] ${ev.title} (Score: ${String.format("%.3f", ev.rankScore)})\n")
            sb.append("- **Arquivo:** `${ev.path}`\n")
            sb.append("- **Trecho:** ${ev.content.replace("\n", " ").take(200)}...\n")
            if (node.connectedSymbols.isNotEmpty()) {
                sb.append("- **Grafo de Dependências Conectadas:** `${node.connectedSymbols.take(5).joinToString("`, `")}`\n")
            }
            sb.append("\n")
        }

        if (secondaryEvidences.isNotEmpty() && secondaryScope != null) {
            sb.append("## 3. Evidências no Projeto Secundário (`$secondaryScope`)\n")
            secondaryEvidences.take(5).forEachIndexed { i, ev ->
                val node = DependencyGraphExpander.expandEvidence(ev)
                sb.append("### [${i + 1}] ${ev.title} (Score: ${String.format("%.3f", ev.rankScore)})\n")
                sb.append("- **Arquivo:** `${ev.path}`\n")
                sb.append("- **Trecho:** ${ev.content.replace("\n", " ").take(200)}...\n")
                if (node.connectedSymbols.isNotEmpty()) {
                    sb.append("- **Grafo de Dependências Conectadas:** `${node.connectedSymbols.take(5).joinToString("`, `")}`\n")
                }
                sb.append("\n")
            }
        }

        if (insights.isNotEmpty()) {
            sb.append("## 4. Síntese e Insights Comparativos\n")
            insights.forEach { sb.append("- 💡 $it\n") }
            sb.append("\n")
        }

        return DeepSearchReport(
            query = query,
            primaryScope = primaryScope,
            secondaryScope = secondaryScope,
            primaryEvidences = primaryEvidences,
            secondaryEvidences = secondaryEvidences,
            keyEntities = keyEntities,
            insights = insights,
            markdownSummary = sb.toString()
        )
    }

    /**
     * Execução ponta a ponta com LoopDetector e Watchdog ativo
     */
    fun runDeepSearch(
        query: String,
        primaryScope: String,
        secondaryScope: String? = null,
        topK: Int = 5
    ): DeepSearchReport {
        val watchdog = AgentWatchdog(AgentBudget(maxSteps = 6, maxDurationMs = 15_000))
        val loopDetector = LoopDetector()

        // Passo 1: Busca Semântica Densa no Escopo Primário
        loopDetector.recordAction("dense_search", "scope=$primaryScope|query=$query")
        watchdog.heartbeat()
        val densePrimary = TurbovecBridge.query(query, topK = topK, scope = primaryScope)

        // Passo 2: Busca Exata de Símbolos no Escopo Primário
        loopDetector.recordAction("exact_search", "scope=$primaryScope|query=$query")
        watchdog.heartbeat()
        val exactPrimary = searchExactSymbols(query.take(20), scope = primaryScope, limit = topK)
        val primaryFused = fuseResults(densePrimary, exactPrimary)

        // Passo 3: Busca no Escopo Secundário (se houver)
        val secondaryFused = if (secondaryScope != null) {
            loopDetector.recordAction("dense_search", "scope=$secondaryScope|query=$query")
            watchdog.heartbeat()
            val denseSecondary = TurbovecBridge.query(query, topK = topK, scope = secondaryScope)
            val exactSecondary = searchExactSymbols(query.take(20), scope = secondaryScope, limit = topK)
            fuseResults(denseSecondary, exactSecondary)
        } else {
            emptyList()
        }

        // Passo 4: Cruzamento e Síntese
        val primaryRagList = primaryFused.map { RagResult(it.rankScore, it.id, it.scope, it.title, it.path, it.content) }
        val secondaryRagList = secondaryFused.map { RagResult(it.rankScore, it.id, it.scope, it.title, it.path, it.content) }

        val insights = mutableListOf<String>()
        if (secondaryScope != null && primaryRagList.isNotEmpty() && secondaryRagList.isNotEmpty()) {
            val corr = correlateProjects(query, primaryRagList, primaryScope, secondaryRagList, secondaryScope)
            if (corr.sharedEntities.isNotEmpty()) {
                insights.add("Entidades correspondentes entre $primaryScope e $secondaryScope: ${corr.sharedEntities.joinToString(", ")}")
            }
        }
        if (primaryRagList.isNotEmpty()) {
            insights.add("Maior correlação em $primaryScope localizada no arquivo '${primaryRagList.first().path}'.")
        }

        return generateReport(
            query = query,
            primaryFindings = primaryRagList,
            secondaryFindings = secondaryRagList,
            primaryScope = primaryScope,
            secondaryScope = secondaryScope,
            insights = insights
        )
    }

    private fun extractKeywords(text: String): List<String> {
        val ignored = setOf(
            "class", "public", "private", "val", "var", "fun", "struct", "impl", "fn",
            "the", "and", "with", "from", "this", "that", "para", "como", "usar", "classe",
            "java", "rust", "extends", "target"
        )

        // Quebra camelCase e snake_case para correlacionar código multi-linguagem (Rust <-> Java/TS)
        // Ex: MoveToPawn -> [MoveToPawn, move, pawn], move_to_pawn -> [move_to_pawn, move, pawn]
        val tokens = mutableListOf<String>()
        val rawWords = Regex("[a-zA-Z0-9_]{3,}").findAll(text).map { it.value }.toList()

        for (raw in rawWords) {
            tokens.add(raw)
            tokens.add(raw.lowercase())
            // Quebrar por underline
            raw.split("_").filter { it.length >= 3 }.forEach {
                tokens.add(it)
                tokens.add(it.lowercase())
            }
            // Quebrar por CamelCase (ex: MoveToPawn -> Move, To, Pawn)
            Regex("[A-Z][a-z0-9]+").findAll(raw).map { it.value }.filter { it.length >= 3 }.forEach {
                tokens.add(it)
                tokens.add(it.lowercase())
            }
        }

        return tokens
            .filter { it !in ignored }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    }
}
