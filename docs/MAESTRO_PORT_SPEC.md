# Especificação de Porte: Maestro → Kotlin (Hermes 3.0)

## 1. Mapeamento de Módulos

| Módulo Maestro (JS/Node) | Módulo Hermes 3.0 (Kotlin) | Responsabilidade |
| :--- | :--- | :--- |
| `change-governance.js` | `br.com.hermes.maestro.governance.ChangeGovernance` | Garante alterações cirúrgicas e valida que arquivos proibidos não sejam tocados. |
| `capture-policy.js` | `br.com.hermes.maestro.governance.CapturePolicy` | Filtra logs e comandos para impedir vazamento de chaves, segredos ou dados sensíveis. |
| `git-context.js` | `br.com.hermes.maestro.git.GitContext` | Captura branch, worktrees, commits recentes e diff unstaged com baixo overhead. |
| `task-classifier.js` | `br.com.hermes.maestro.classifier.TaskClassifier` | Classifica complexidade da tarefa (Direct vs. Decomposed) via Turbovec. |
| `lock.js` / `lock-async.js` | `br.com.hermes.maestro.lock.LockManager` | Concorrência atômica via `kotlinx.coroutines.sync.Mutex`. |
| `context-slices.js` | `br.com.hermes.maestro.context.ContextSlices` | Monta fatias compactas de contexto (Head + Tail) para a janela de tokens. |
| `maestro-resolution-engine.md` | `br.com.hermes.maestro.resolution.ResolutionEngine` | Validador do Definition of Done (DoD) e gerador do Proof Bundle. |

## 2. Contrato do Proof Bundle
```kotlin
data class ProofBundle(
    val taskId: String,
    val modifiedFiles: List<String>,
    val gitDiffStat: String,
    val testsExecuted: Boolean,
    val testExitCode: Int,
    val linterExitCode: Int,
    val verifiedAtEpochMs: Long,
    val status: ResolutionStatus
)

enum class ResolutionStatus {
    COMPLETED_VERIFIED,
    REPAIR_REQUIRED,
    FAILED_GATES
}
```
