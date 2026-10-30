package br.com.hermes.maestro.governance

import java.io.File

sealed interface GovernanceDecision {
    data object Allowed : GovernanceDecision
    data class Denied(val reason: String) : GovernanceDecision
}

object ChangeGovernance {
    private val blockedPatterns = listOf(
        ".git/",
        ".env",
        ".key",
        ".pem",
        "target/",
        "dist/",
        "build/"
    )

    fun validateFileWrite(targetPath: String): GovernanceDecision {
        val cleanPath = targetPath.replace("\\", "/")
        for (blocked in blockedPatterns) {
            if (cleanPath.contains(blocked)) {
                return GovernanceDecision.Denied("Modificação bloqueada pela governança Maestro: arquivo sensível ou proibido ($blocked)")
            }
        }
        return GovernanceDecision.Allowed
    }

    fun validateScope(modifiedFile: String, allowedScope: List<String>): GovernanceDecision {
        if (allowedScope.isEmpty()) return GovernanceDecision.Allowed
        val isAllowed = allowedScope.any { scope -> modifiedFile.startsWith(scope) }
        return if (isAllowed) {
            GovernanceDecision.Allowed
        } else {
            GovernanceDecision.Denied("Arquivo $modifiedFile fora do escopo autorizado: $allowedScope")
        }
    }
}

object CapturePolicy {
    private val secretRegexes = listOf(
        Regex("sk-[a-zA-Z0-9]{20,}"),
        Regex("ghp_[a-zA-Z0-9]{20,}"),
        Regex("gho_[a-zA-Z0-9]{20,}"),
        Regex("AIza[0-9A-Za-z-_]{35}")
    )

    fun sanitizeOutput(text: String): String {
        var sanitized = text
        for (regex in secretRegexes) {
            sanitized = regex.replace(sanitized, "[REDACTED_SECRET]")
        }
        return sanitized
    }
}
