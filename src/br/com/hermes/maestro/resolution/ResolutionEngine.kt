package br.com.hermes.maestro.resolution

import kotlinx.serialization.Serializable

@Serializable
enum class ResolutionStatus {
    COMPLETED_VERIFIED,
    REPAIR_REQUIRED,
    FAILED_GATES
}

@Serializable
data class ProofBundle(
    val taskId: String,
    val modifiedFiles: List<String>,
    val testsExecuted: Boolean,
    val testExitCode: Int,
    val linterExitCode: Int,
    val verifiedAtEpochMs: Long,
    val status: ResolutionStatus,
    val notes: String
)

object ResolutionEngine {
    fun verifyDelivery(
        taskId: String,
        modifiedFiles: List<String>,
        testExitCode: Int,
        linterExitCode: Int
    ): ProofBundle {
        val testsPassed = testExitCode == 0
        val linterPassed = linterExitCode == 0
        val hasChanges = modifiedFiles.isNotEmpty()

        val (status, notes) = when {
            !testsPassed -> ResolutionStatus.REPAIR_REQUIRED to "Testes falharam (exit code $testExitCode). Auto-reparo exigido."
            !linterPassed -> ResolutionStatus.FAILED_GATES to "Linters / formatters falharam (exit code $linterExitCode)."
            !hasChanges -> ResolutionStatus.FAILED_GATES to "Nenhum arquivo modificado na tarefa."
            else -> ResolutionStatus.COMPLETED_VERIFIED to "Entrega comprovada: testes verdes e linter aprovado."
        }

        return ProofBundle(
            taskId = taskId,
            modifiedFiles = modifiedFiles,
            testsExecuted = true,
            testExitCode = testExitCode,
            linterExitCode = linterExitCode,
            verifiedAtEpochMs = System.currentTimeMillis(),
            status = status,
            notes = notes
        )
    }
}
