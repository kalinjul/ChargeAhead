package de.autoapp.shared

/** Where the ChargeAhead backend is, and what it takes to be let in. */
data class BackendConfig(
    val baseUrl: String,
    val token: String,
) {
    companion object {

        /** `null` unless both are actually configured — a half-configured backend is no backend. */
        fun of(baseUrl: String?, token: String?): BackendConfig? {
            val url = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val bearer = token?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return BackendConfig(url, bearer)
        }
    }
}
