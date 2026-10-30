# Hermes 3.0 — Enterprise Agent Runtime

[![Runtime](https://img.shields.io/badge/Runtime-Java%2025%20%2B%20Kotlin%20Coroutines-orange)]()
[![Governance](https://img.shields.io/badge/Governance-Maestro%20Resolution%20Engine-purple)]()
[![RAG](https://img.shields.io/badge/RAG-Turbovec%20TurboQuant%204--bit-blue)](https://github.com/ryancodrai/turbovec)
[![Status](https://img.shields.io/badge/Version-v0.0%20(Baseline)-green)]()

O **Hermes 3.0** é a unificação do motor de alta concorrência **Hermes 2.0** (Java 25 + Kotlin Coroutines) com a governança e contratos de prova do **Orquestrador Maestro**, acelerado pelo motor vetorial in-process **Turbovec (Claude Opus 5.5 TurboQuant 4-bit)**.

---

## 🚀 Destaques do Release v0.0

1. **Indexação das 285 Skills do Claude Opus 5.5:**
   - Catálogo completo formatado em [`docs/SKILLS_CATALOG.md`](docs/SKILLS_CATALOG.md).
   - Manifesto estruturado em JSON pronto para embedding vetorial em [`manifests/skills_manifest.json`](manifests/skills_manifest.json).
2. **Mapeamento e Dossiê Completo do Client L2:**
   - Monorepo Rust (2024 Edition) com 4.589 arquivos técnicos catalogados em [`manifests/client_l2_index.json`](manifests/client_l2_index.json).
   - Dossiê arquitetural detalhando as **33 crates**, os **3 executáveis** (`l2-client`, `l2-studio`, `l2encdec`), os parsers UE2, o pipeline de renderização (6 passos), o handshake de rede C6 (Protocol 746) e as 24 ferramentas de engenharia reversa em [`docs/CLIENT_L2_KNOWLEDGE_BASE.md`](docs/CLIENT_L2_KNOWLEDGE_BASE.md).
   - Nota de revisão: o `README.md` do `client-l2` menciona "11 crates", mas o disco declara 33 — o dossiê reflete o estado real verificado em disco.
3. **Especificação de Porte do Maestro para Kotlin:**
   - Eliminação de dependência de Node.js em runtime.
   - Mapeamento detalhado dos módulos em [`docs/MAESTRO_PORT_SPEC.md`](docs/MAESTRO_PORT_SPEC.md).
4. **Contrato de RAG In-Process com Turbovec:**
   - Busca vetorial sub-milissegundo em [`rag/indexer_spec.md`](rag/indexer_spec.md).

---

## 📂 Estrutura do Repositório
```
hermes-3.0/
├── docs/
│   ├── ARCHITECTURE.md             # Arquitetura geral do sistema
│   ├── CLIENT_L2_KNOWLEDGE_BASE.md # Radiografia técnica do Client L2
│   ├── MAESTRO_PORT_SPEC.md        # Especificação de porte JS -> Kotlin
│   └── SKILLS_CATALOG.md           # As 285 skills do Claude Opus 5.5
├── manifests/
│   ├── client_l2_index.json        # 4.589 arquivos indexados do Client L2
│   └── skills_manifest.json        # Manifesto JSON das 285 skills
├── rag/
│   └── indexer_spec.md             # Contrato de indexação com Turbovec
└── README.md
```
