# Arquitetura do Hermes 3.0

## 1. Visão Geral
O **Hermes 3.0** é a evolução do runtime agêntico autônomo, unificando a velocidade e concorrência bruta em **Java 25 + Kotlin Coroutines** com os guardrails de governança do **Orquestrador Maestro** e o motor vetorial in-process **Turbovec (TurboQuant 4-bit)**.

```
┌─────────────────────────────────────────────────────────────┐
│                     HERMES 3.0 RUNTIME                      │
├─────────────────────────────────────────────────────────────┤
│  1. ENTRADA: Prompt em Linguagem Natural                    │
│  2. RECUPERAÇÃO SEMÂNTICA: Turbovec RAG (sub-milissegundo)  │
│     - Injeta a skill certa (entre 285 disponíveis)          │
│     - Injeta regras do projeto e dossiês técnicos           │
│  3. GOVERNANÇA MAESTRO (Portada para Kotlin):               │
│     - ChangeGovernance (escopo cirúrgico)                   │
│     - PreToolUse / PostToolUse Hooks                        │
│  4. EXECUÇÃO PARALELA (AgentPool):                          │
│     - Workers concorrentes em Kotlin Coroutines             │
│     - Shared Blackboard entre sub-agentes                   │
│  5. FECHAMENTO: Maestro Resolution Engine                    │
│     - Validação estrita: Linters, Testes, Diff limpo        │
│     - Emissão do Proof Bundle comprovado                    │
│  6. AUTO-INDEXAÇÃO (Write-Through):                         │
│     - Gravação incremental no índice vetorial Turbovec      │
└─────────────────────────────────────────────────────────────┘
```

## 2. Pilares de Engenharia

### A. Zero Dependência de Node.js em Runtime
- O Hermes 3.0 é distribuído como um único Fat JAR executável (`hermes.jar`) de alta performance.
- A governança antes executada em Node.js pelo Maestro é totalmente portada para Kotlin idiomático nativo.

### B. TurboQuant 4-Bit RAG (Turbovec)
- Redução de até 7.8x na memória comparado a vetores float32 tradicionais.
- Desempenho 4.4x mais rápido que FAISS via instruções SIMD no processador.
- Persistência incremental (`sync`) de baixo overhead.

### C. Proof Bundle e Entrega Verificada
- Eliminação completa de alucinações de término.
- Nenhuma tarefa é marcada como `COMPLETED_VERIFIED` sem provas reais coletadas de execuções no terminal (exit code 0).
