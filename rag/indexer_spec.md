# Contrato de Indexação: Turbovec RAG — Hermes 3.0

## 1. Definição do Índice
O Hermes 3.0 adota o **Turbovec** (`ryancodrai/turbovec`), engine vetorial escrita em Rust com compressão Claude Opus 5.5 TurboQuant.

- **Dimensão:** 1536 (OpenAI / Cohere) ou 768 / 1024 (modelos locais/HuggingFace).
- **Quantização:** 4-bit (padrão equilibrado) ou 2-bit (ultra-compressão para grandes bases).
- **Tipo de Índice:** `IdMapIndex` (chaves `uint64` mapeadas para metadados locais).
- **Arquivo de Armazenamento:** `.hermes/rag/index.tv` por projeto.

## 2. Escopos de Vetores (Allowlist Bitmasks)
Para garantir que o agente não misture regras de diferentes linguagens ou projetos, os IDs são segmentados em namespaces:

| Range de ID | Escopo | Conteúdo |
| :--- | :--- | :--- |
| `0x10000000 - 0x1FFFFFFF` | **Skills Claude/Maestro** | 285 skills do Claude Opus 5.5 e catálogo do Maestro |
| `0x20000000 - 0x2FFFFFFF` | **Dossiês Forenses** | Arquitetura UE2, NCSoft, LU4 e Fermata UI |
| `0x30000000 - 0x3FFFFFFF` | **Código Fonte Client L2** | Funções, structs e assinaturas das crates Rust |
| `0x40000000 - 0x4FFFFFFF` | **Decisões / Handoffs** | ADRs, sessões anteriores e Proof Bundles |

## 3. Fluxo de Consulta
```python
# Pseudocódigo do serviço de busca
scores, ids = index.search(query_vector, k=3, allowlist=scope_ids)
# Retorno em < 2ms com acurácia idêntica a float32
```
