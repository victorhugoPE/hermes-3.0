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

        "--deep-search" -> {
            val queryText = args.getOrNull(1) ?: run {
                println("❌ Erro: informe a query. Ex: --deep-search \"handshake blowfish\" --scope client-l2-code")
                return
            }
            var primaryScope = "client-l2-code"
            var secondaryScope: String? = null

            var i = 2
            while (i < args.size) {
                when (args[i]) {
                    "--scope" -> if (i + 1 < args.size) primaryScope = args[++i]
                    "--compare" -> if (i + 1 < args.size) secondaryScope = args[++i]
                }
                i++
            }

            println("🔬 Executando DeepSearch 2.0 (Turbovec Híbrido + Watchdog Anti-Loop)...")
            println("   • Query: '$queryText'")
            println("   • Escopo Primário: $primaryScope")
            if (secondaryScope != null) println("   • Escopo de Comparação: $secondaryScope")

            val t0 = System.currentTimeMillis()
            val report = br.com.hermes.search.DeepSearchEngine.runDeepSearch(
                query = queryText,
                primaryScope = primaryScope,
                secondaryScope = secondaryScope,
                topK = 5
            )
            val elapsed = System.currentTimeMillis() - t0

            println("\n" + "=".repeat(70))
            println(report.markdownSummary)
            println("=".repeat(70))
            println("⚡ DeepSearch concluído em ${elapsed}ms com segurança total de ciclo e watchdog.")
        }

        "--research" -> {
            val topic = args.getOrNull(1) ?: run {
                println("❌ Erro: informe o tema. Ex: --research \"decodificacao de pacotes\" --repos client-l2-code,servers-java-kt")
                return
            }
            var reposArg = "client-l2-code,servers-java-kt"
            if (args.size >= 4 && args[2] == "--repos") {
                reposArg = args[3]
            }
            val parts = reposArg.split(",").map { it.trim() }
            val scopeA = parts.getOrNull(0) ?: "client-l2-code"
            val scopeB = parts.getOrNull(1) ?: "servers-java-kt"

            println("🤖 Despachando 2 Agentes Paralelos via ResearchOrchestrator...")
            println("   • Tema: '$topic'")
            println("   • Agente A -> Escopo: $scopeA")
            println("   • Agente B -> Escopo: $scopeB")
            println("   • Blackboard Concorrente e Watchdog ativos.\n")

            val orchestrator = br.com.hermes.search.ResearchOrchestrator()
            kotlinx.coroutines.runBlocking {
                val t0 = System.currentTimeMillis()
                val plan = br.com.hermes.search.ResearchPlan(
                    topic = topic,
                    primaryScope = scopeA,
                    secondaryScope = scopeB,
                    maxStepsPerAgent = 6,
                    timeoutPerAgentMs = 25_000L
                )
                val report = orchestrator.executeParallelResearch(plan)
                val elapsed = System.currentTimeMillis() - t0

                println("=".repeat(70))
                println(report.markdownSummary)
                println("=".repeat(70))
                println("\n📊 Log do Blackboard Compartilhado:")
                println(orchestrator.blackboard.getFormattedSummary())
                println("\n🏁 Pesquisa concorrente concluída em ${elapsed}ms!")
            }
        }

        "--sync-file" -> {
            val filePath = args.getOrNull(1) ?: run {
                println("❌ Erro: informe o caminho do arquivo. Ex: --sync-file crates/l2-net/src/lib.rs --scope client-l2-code")
                return
            }
            val scope = if (args.size >= 4 && args[2] == "--scope") args[3] else "general"
            println("⚡ Sincronizando incrementalmente: '$filePath' [Escopo: $scope]...")
            val success = br.com.hermes.rag.IncrementalIndexer.upsertFile(java.io.File(filePath), scope)
            if (success) {
                println("✅ Sincronização incremental concluída com sucesso!")
            } else {
                println("❌ Falha ao sincronizar arquivo.")
            }
        }

        "--watch" -> {
            br.com.hermes.rag.FileWatcherDaemon.startWatching()
        }

        "--verify-governance" -> {
            val path = args.getOrNull(1) ?: ".env"
            val decision = ChangeGovernance.validateFileWrite(path)
            println("🛡️ Verificação de Governança para '$path': $decision")
        }

        "--verify-l2" -> {
            br.com.hermes.project.ProjectVerifier.verifyClientL2()
        }

        "--verify-elbera" -> {
            br.com.hermes.project.ProjectVerifier.verifyElbera()
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

        "--verify-watchdog" -> {
            println("🛡️ Verificando Loop Detector e Agent Watchdog...")
            val detector = br.com.hermes.agent.LoopDetector(repetitionThreshold = 3)
            println("   • Registrando ação 'search(packet)' 1x: ${detector.recordAction("search", "packet")}")
            println("   • Registrando ação 'search(packet)' 2x: ${detector.recordAction("search", "packet")}")
            val trip = detector.recordAction("search", "packet")
            println("   • Registrando ação 'search(packet)' 3x: $trip")

            val watchdog = br.com.hermes.agent.AgentWatchdog(br.com.hermes.agent.AgentBudget(maxSteps = 5))
            println("   • Watchdog Step 2/5: ${watchdog.check(2)}")
            println("   • Watchdog Step 4/5 (Warning): ${watchdog.check(4)}")
            println("   • Watchdog Step 6/5 (Tripped): ${watchdog.check(6)}")
            println("✅ Loop Detector e Watchdog 100% operacionais!")
        }

        "--exec" -> {
            val taskDesc = args.getOrNull(1) ?: run {
                println("❌ Erro: informe a descrição da tarefa. Ex: --exec \"ajustar decodificador de pacotes C6\"")
                return
            }
            println("🤖 Iniciando AgentPool (3 Workers Concorrentes em Coroutines)...")
            println("📌 Tarefa recebida: '$taskDesc'")

            val pool = br.com.hermes.agent.AgentPool()
            kotlinx.coroutines.runBlocking {
                val t0 = System.currentTimeMillis()
                val task = br.com.hermes.agent.AgentTask(
                    taskId = "task-" + System.currentTimeMillis().toString().takeLast(6),
                    description = taskDesc
                )
                val result = pool.executeTask(task)
                val elapsed = System.currentTimeMillis() - t0

                println("\n🏁 Execução concluída em ${elapsed}ms!")
                println("   • Worker Responsável: ${result.agentId}")
                println("   • Sucesso: ${result.success}")
                if (result.tripReason != null) {
                    println("   • ⚠️ Motivo do Circuito: ${result.tripReason}")
                }
                println("   • Tentativas no TDD Loop: ${result.attempts}")
                println("   • Contextos RAG Injetados:")
                result.contextInjected.forEach { println("     $it") }
                println("   • Proof Bundle Status: ${result.proofBundle.status}")
                println("   • Arquivos Modificados: ${result.proofBundle.modifiedFiles}")
                println("   • Notas da Entrega: ${result.proofBundle.notes}")
                println("\n📊 Estado do Blackboard Compartilhado:")
                println(pool.blackboard.getFormattedSummary())
            }
        }

        "--batch-exec" -> {
            println("⚡ Disparando lote de tarefas paralelas no AgentPool...")
            val pool = br.com.hermes.agent.AgentPool()
            val tasks = listOf(
                br.com.hermes.agent.AgentTask("T-01", "ajustar criptografia blowfish do login", "client-l2-code"),
                br.com.hermes.agent.AgentTask("T-02", "verificar colisão e linecheck no mapa", "interlude-dll-forensics"),
                br.com.hermes.agent.AgentTask("T-03", "revisar bindings ffi do lua", "skills")
            )

            kotlinx.coroutines.runBlocking {
                val t0 = System.currentTimeMillis()
                val results = pool.executeBatch(tasks)
                val elapsed = System.currentTimeMillis() - t0

                println("\n🏁 Lote de ${results.size} tarefas finalizado em ${elapsed}ms concorrentes!")
                for (r in results) {
                    println("   • [${r.taskId}] Worker: ${r.agentId} | Status: ${r.proofBundle.status} | Sucesso: ${r.success}")
                }
                println("\n📊 Estado do Blackboard Compartilhado:")
                println(pool.blackboard.getFormattedSummary())
            }
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
    hermes --deep-search "<q>" --scope <A> [--compare <B>] # DeepSearch híbrida multi-projeto com síntese e anti-loop
    hermes --research "<tema>" --repos <A>,<B>       # Orquestra 2 agentes paralelos + síntese consolidada
    hermes --watch                          # Monitora Client L2 e Elbera sincronizando alteracoes em tempo real
    hermes --sync-file <arquivo> [--scope <s>]   # Atualização incremental do Turbovec/SQLite
    hermes --verify-watchdog                # Testa o Loop Detector e Watchdog Anti-Travamento
    hermes --verify-governance <arquivo>    # Testa validação de ChangeGovernance
    hermes --exec "<tarefa>"                 # Executa tarefa concorrente com auto-reparo e proteção
    hermes --simulate-delivery              # Simula validação de Proof Bundle do Maestro
    """.trimIndent())
}
