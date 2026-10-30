package br.com.hermes

import br.com.hermes.maestro.governance.ChangeGovernance
import br.com.hermes.maestro.resolution.ResolutionEngine
import br.com.hermes.rag.TurbovecBridge
import java.io.File

fun main(args: Array<String>) {
    println("=" .repeat(70))
    println("⚡ Hermes 3.0 — Enterprise Agent Runtime (Kotlin/Java 25 + Turbovec)")
    println("=" .repeat(70))

    if (args.isEmpty() || args.contains("--help")) {
        printHelp()
        return
    }

    when (args[0]) {
        "--rag-status" -> {
            val ready = TurbovecBridge.isReady()
            println("📦 Status do Turbovec RAG:")
            println("   • RAG Ready: $ready")
            println("   • Índice: .hermes/rag/hermes_index.tvim (${File(".hermes/rag/hermes_index.tvim").length() / 1024} KB)")
            println("   • Metadados: .hermes/rag/hermes_map.db (${File(".hermes/rag/hermes_map.db").length() / 1024} KB)")
        }

        "--query" -> {
            val queryText = args.getOrNull(1) ?: run {
                println("❌ Erro: informe o texto da busca. Ex: --query \"como usar ffi\"")
                return
            }
            val scope = if (args.size >= 4 && args[2] == "--scope") args[3] else null

            println("🔎 Consultando RAG Turbovec para: '$queryText' ${if (scope != null) "[Escopo: $scope]" else ""}...")
            val t0 = System.currentTimeMillis()
            val results = TurbovecBridge.query(queryText, topK = 3, scope = scope)
            val elapsed = System.currentTimeMillis() - t0

            println("⚡ Concluído em ${elapsed}ms (${results.size} resultados encontrados):\n")
            for ((idx, r) in results.withIndex()) {
                println("[${idx + 1}] Score: ${r.score} | Escopo: ${r.scope} | ${r.title}")
                println("    Caminho: ${r.path}")
                println("    Trecho:  ${r.content}")
                println("-".repeat(70))
            }
        }

        "--verify-governance" -> {
            val path = args.getOrNull(1) ?: ".env"
            val decision = ChangeGovernance.validateFileWrite(path)
            println("🛡️ Verificação de Governança para '$path': $decision")
        }

        "--simulate-delivery" -> {
            val proof = ResolutionEngine.verifyDelivery(
                taskId = "task-poc-001",
                modifiedFiles = listOf("crates/l2-net/src/lib.rs"),
                testExitCode = 0,
                linterExitCode = 0
            )
            println("📋 Proof Bundle Gerado:")
            println("   • Status: ${proof.status}")
            println("   • Notas:  ${proof.notes}")
            println("   • Arquivos: ${proof.modifiedFiles}")
        }

        else -> {
            println("Comando não reconhecido: ${args[0]}")
            printHelp()
        }
    }
}

private fun printHelp() {
    println("""
Uso:
    hermes --rag-status                     # Verifica a saúde e tamanho dos índices RAG
    hermes --query "<termo>" [--scope <s>]   # Busca semântica ultrarrápida via Turbovec
    hermes --verify-governance <arquivo>    # Testa validação de ChangeGovernance
    hermes --simulate-delivery              # Simula validação de Proof Bundle do Maestro
    """.trimIndent())
}
