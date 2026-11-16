# ⚡ Hermes 3.0 — Enterprise Agent Runtime

[![Runtime](https://img.shields.io/badge/Runtime-Java%2025%20%2B%20Kotlin%20Coroutines-orange?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Governance](https://img.shields.io/badge/Governance-Maestro%20Resolution%20Engine-purple?style=for-the-badge)]()
[![RAG](https://img.shields.io/badge/RAG-Turbovec%20TurboQuant%204--bit-blue?style=for-the-badge)](https://github.com/ryancodrai/turbovec)
[![Tests](https://img.shields.io/badge/Tests-25%20Passed%20(100%25)-success?style=for-the-badge)]()
[![License](https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge)]()

O **Hermes 3.0** é um sistema operacional e runtime corporativo para agentes de IA autônomos. Ele combina a alta concorrência de **Kotlin Coroutines** e **Java 25**, o motor vetorial in-process **Turbovec (Claude Opus 5.5 TurboQuant 4-bit)** com respostas sub-milissegundo, governança estrita de código pelo **Maestro Resolution Engine**, e proteção nativa contra travamentos com **Loop Detector e Watchdog Circuit Breaker**.

---

## 🌟 Principais Recursos e Arquitetura

```
                                  ┌───────────────────────────┐
                                  │        Hermes CLI         │
                                  └─────────────┬─────────────┘
                                                │
                 ┌──────────────────────────────┼──────────────────────────────┐
                 ▼                              ▼                              ▼
   ┌───────────────────────────┐  ┌───────────────────────────┐  ┌───────────────────────────┐
   │    ResearchOrchestrator   │  │    DeepSearchEngine 2.0   │  │     FileWatcherDaemon     │
   │  (2 Workers Concorrentes) │  │  (Reciprocal Rank Fusion) │  │ (Live Sync FSEvents macOS)│
   └─────────────┬─────────────┘  └─────────────┬─────────────┘  └─────────────┬─────────────┘
                 │                              │                              │
                 └──────────────────────────────┼──────────────────────────────┘
                                                ▼
                 ┌─────────────────────────────────────────────────────────────┐
                 │               LoopDetector & AgentWatchdog                  │
                 │   (Anti-Travamento • Teto de Passos • SLA • Anti-Zombie)    │
                 └──────────────────────────────┬──────────────────────────────┘
                                                │
                 ┌──────────────────────────────┴──────────────────────────────┐
                 ▼                                                             ▼
   ┌───────────────────────────┐                                 ┌───────────────────────────┐
   │    Turbovec RAG Daemon    │ ◄─── [Unix Socket ~20ms] ────►  │ Maestro Resolution Engine │
   │ (3.456 Chunks / 4-bit SIMD│                                 │ (Quality Gates & Proofs)  │
   └───────────────────────────┘                                 └───────────────────────────┘
```

### 🛡️ 1. Loop Detector & Watchdog Anti-Travamento (Circuit Breaker)
- **Detecção de Repetições Consecutivas:** Detecta quando uma tool ou consulta idêntica for chamada $\ge 3$ vezes consecutivas e aciona `Tripped(CONSECUTIVE_IDENTICAL)`.
- **Detecção de Ciclos Oscilatórios:** Detecta alternância repetitiva (ex: Ferramenta A $\to$ B $\to$ A $\to$ B $\to$ A $\to$ B) e interrompe com `Tripped(OSCILLATING_CYCLE)`.
- **Orçamento de Passos (`maxSteps`):** Aos 75% do teto, emite alerta orientando síntese imediata dos achados. Ao bater 100%, força encerramento com `Tripped(STEP_BUDGET_EXCEEDED)`.
- **Teto de Tempo de Execução (`maxDurationMs`):** Impede tarefas de ficarem horas em segundo plano.
- **Anti-Zombie Heartbeat:** Monitora se agentes em background pararam de emitir sinais no Blackboard compartilhado.

### 🔬 2. DeepSearch 2.0 com Fusão Híbrida (RRF)
- **Reciprocal Rank Fusion (RRF):** Combina a busca vetorial densa do Turbovec (4-bit TurboQuant SIMD) com a busca exata de identificadores técnicos no SQLite (`hermes_map.db`).
- **Tokenizador Multi-Linguagem:** Decompõe automaticamente identificadores em `snake_case` e `CamelCase`, permitindo correlacionar código Rust (ex: `move_to_pawn`) com Java/Kotlin (ex: `MoveToPawn`) e TypeScript.
- **Cross-Repo Correlation:** Compara simultaneamente dois repositórios ou escopos distintos e gera um dossiê executivo consolidado com mapa de equivalências de código.

### 🕸️ 3. Dependency Graph Expander (AST-Lite Code Walk)
- Analisa estaticamente arquivos de código `.rs`, `.java`, `.kt`, `.ts` e extrai dependências (`use crate::...`, `mod ...`, `import ...`).
- Enriquece os resultados com os nós do grafo conectados, entregando o contexto da função e de seus chamadores.

### 🤖 4. ResearchOrchestrator Concorrente
- Despacha simultaneamente **2 agentes em Coroutines** para dois repositórios ou escopos distintos (ex: `client-l2-code` e `servers-java-kt`).
- Cada agente explora seu domínio e sincroniza descobertas em tempo real no **Shared Blackboard**.
- O sintetizador agrega as evidências e emite o relatório final sem risco de deadlock.

### 👀 5. Live File Watcher Daemon
- Monitora alterações de arquivos via FSEvents do macOS (`WatchService`) nos repositórios ativos (`Client L2`, `Lineage2Elbera`).
- Dispara o `IncrementalIndexer` para sincronizar arquivos modificados no Turbovec e SQLite em milissegundos sem reindexação total.

---

## ⚡ Como Usar (Guia da CLI)

O binário oficial fica localizado em `bin/hermes`.

### 1. Pesquisa Concorrente Multi-Agente (Anti-Loop)
Dispara 2 agentes paralelos para explorar e correlacionar dois projetos distintos, com síntese consolidada:
```bash
./bin/hermes --research "criptografia blowfish e handshake" --repos client-l2-code,servers-java-kt
```

### 2. DeepSearch Híbrida com Expansão de Grafo
Executa busca vetorial densa combinada com busca exata por símbolos e grafo de dependências AST-Lite:
```bash
./bin/hermes --deep-search "MoveToPawn e colisão no mapa" --scope client-l2-code --compare elbera-code
```

### 3. Consulta RAG Rápida (Sub-milissegundo)
Consulta instantânea via Unix Socket (`/tmp/hermes_rag.sock`):
```bash
./bin/hermes --query "como usar ffi e rust no l2" --scope skills
```

### 4. Monitoramento Contínuo (Live Watcher)
Monitora alterações de código no `Client L2` e `Lineage2Elbera` e sincroniza o Turbovec em tempo real:
```bash
./bin/hermes --watch
```

### 5. Sincronização Incremental Pontual
Atualiza o índice vetorial para um único arquivo modificado:
```bash
./bin/hermes --sync-file crates/l2-net/src/lib.rs --scope client-l2-code
```

### 6. Diagnóstico de Governança e Qualidade (Maestro Gates)
```bash
./bin/hermes --verify-watchdog      # Valida Loop Detector e Watchdog Anti-Travamento
./bin/hermes --verify-l2            # Validação formal do Client L2 (cargo fmt + check)
./bin/hermes --verify-elbera        # Validação formal do Lineage2Elbera (Node tests)
./bin/hermes --verify-governance    # Checagem de políticas de gravação em arquivos
```

---

## 📚 Escopos Indexados no Turbovec RAG (3.456 Chunks)

| Escopo | Descrição | Tecnologias Cobertas |
|---|---|---|
| `client-l2-code` | Base de código do Client L2 nativo | Rust 2024, WGPU, Shaders WGSL, Rede Tokio |
| `client-l2-kb` | Dossiê e engenharia reversa do Client L2 | Documentações, protocolos, format spec |
| `elbera-code` | Web Client, Gateway e Ferramentas | Three.js, WebGL/WebGPU, WebSocket Node.js |
| `elbera-docs` | Manuais de arquitetura e handoffs do Elbera | Especificações de UI, mapas, física |
| `interlude-dll-forensics` | Descompilações Ghidra C e Headers de DLLs | 27 DLLs oficiais da NCSoft (Engine, Core, L2UI) |
| `l2off-cpp` | Fontes C++ de servidores oficiais e extenders | Vanganth Extender, IOCP C++ NCSOFT |
| `servers-java-kt` | Protocolos e emuladores de servidor | L2JLucera2, RUSaCis, l2kt, Nexora |
| `skills` | Catálogo oficial de skills de engenharia | Rust FFI, TDD, Clean Architecture, Performance |

---

## 🛠️ Build e Testes

O projeto utiliza **Gradle com Kotlin DSL** e **Java 21/25**.

```bash
# Executar toda a suíte de testes unitários (25 testes)
./gradlew test

# Gerar o Fat JAR de distribuição oficial
./gradlew shadowJar

# Inicializar o RAG Daemon em background (Unix Socket)
./bin/hermes-daemon start

# Verificar status do RAG Daemon
./bin/hermes-daemon status
```

---

## 📂 Estrutura de Diretórios

```
hermes-3.0/
├── bin/
│   ├── hermes                  # Launcher oficial da CLI
│   └── hermes-daemon           # Gerenciador do Daemon em background
├── dist/
│   └── hermes.jar              # Fat JAR de release executável
├── src/br/com/hermes/
│   ├── Main.kt                 # Ponto de entrada da CLI
│   ├── agent/
│   │   ├── AgentPool.kt        # Pool concorrente com Coroutines
│   │   ├── Blackboard.kt       # Shared Blackboard concorrente
│   │   ├── AgentWatchdog.kt    # Fiscalização de passos, tempo e heartbeats
│   │   └── LoopDetector.kt     # Detecção de repetições e ciclos oscilatórios
│   ├── maestro/                # Governança e Resolution Engine
│   ├── project/                # Verificadores de projetos (L2 e Elbera)
│   ├── rag/
│   │   ├── TurbovecBridge.kt   # Conexão nativa via Unix Domain Socket
│   │   ├── IncrementalIndexer.kt # Upsert cirúrgico de arquivos alterados
│   │   └── FileWatcherDaemon.kt  # Monitor FSEvents macOS para live sync
│   └── search/
│       ├── DeepSearchEngine.kt        # Fusão RRF e correlação cross-repo
│       ├── DependencyGraphExpander.kt # AST-Lite Code Walk (Rust/Java/TS)
│       └── ResearchOrchestrator.kt    # Orquestrador de 2 agentes concorrentes
├── scripts/ingest/
│   ├── hermes_indexer.py       # Indexador master Turbovec 4-bit
│   ├── hermes_incremental.py   # Upsert incremental via Python
│   ├── hermes_query.py         # Consulta vetorial standalone
│   └── hermes_rag_daemon.py    # Daemon do Unix Domain Socket na RAM
└── test/                       # 25 Testes automatizados 100% verdes
```

---

**Hermes 3.0** — Desenvolvido para máxima velocidade, resiliência e engenharia de ponta.
