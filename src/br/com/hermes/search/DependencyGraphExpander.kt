package br.com.hermes.search

import kotlinx.serialization.Serializable

@Serializable
data class ExpandedCodeNode(
    val rootEvidence: DeepSearchEvidence,
    val connectedSymbols: List<String>,
    val neighboringHints: List<String>
)

/**
 * DependencyGraphExpander: AST-Lite Code Walk
 * Analisa estaticamente arquivos de código (.rs, .java, .kt, .ts) para extrair
 * o grafo de dependências locais (imports, uses, mods) e enriquecer o contexto de busca.
 */
object DependencyGraphExpander {

    fun extractReferences(sourceCode: String, ext: String): List<String> {
        val cleanExt = ext.trimStart('.').lowercase()
        val references = mutableSetOf<String>()

        when (cleanExt) {
            "rs" -> {
                // Rust: pub mod xyz; mod xyz;
                Regex("\\b(?:pub\\s+)?mod\\s+([a-zA-Z0-9_]+)\\s*;").findAll(sourceCode).forEach {
                    references.add(it.groupValues[1])
                }
                // Rust: use crate::a::b; use super::x::y;
                Regex("use\\s+(?:crate|super)::([a-zA-Z0-9_:]+)").findAll(sourceCode).forEach {
                    val fullPath = it.groupValues[1]
                    fullPath.split("::").filter { seg -> seg.length >= 3 }.forEach {
                        references.add(it)
                    }
                }
            }
            "java", "kt", "kotlin" -> {
                // Java/Kotlin: import a.b.c.ClassName;
                Regex("import\\s+([a-zA-Z0-9_.]+)\\s*;").findAll(sourceCode).forEach {
                    val full = it.groupValues[1]
                    val last = full.substringAfterLast(".")
                    if (last.length >= 3) references.add(last)
                }
            }
            "ts", "js", "tsx", "jsx" -> {
                // TS/JS: import ... from './module'
                Regex("from\\s+['\"]([^'\"]+)['\"]").findAll(sourceCode).forEach {
                    val target = it.groupValues[1].substringAfterLast("/")
                    if (target.isNotEmpty()) references.add(target)
                }
            }
            else -> {
                // Genérico: palavras com cara de identificador ou módulo
                Regex("\\b[A-Z][a-zA-Z0-9]{3,}\\b").findAll(sourceCode).take(5).forEach {
                    references.add(it.value)
                }
            }
        }

        return references.toList()
    }

    fun expandEvidence(evidence: DeepSearchEvidence): ExpandedCodeNode {
        val ext = evidence.path.substringAfterLast(".", "rs")
        val symbols = extractReferences(evidence.content, ext)

        val hints = symbols.map { sym ->
            "Módulo/Símbolo relacionado: '$sym' (referenciado em ${evidence.title})"
        }

        return ExpandedCodeNode(
            rootEvidence = evidence,
            connectedSymbols = symbols,
            neighboringHints = hints
        )
    }
}
