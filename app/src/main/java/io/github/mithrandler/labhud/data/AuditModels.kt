package io.github.mithrandler.labhud.data

import kotlinx.serialization.Serializable

/** An API token as lists show it (POST /api/tokens/list): never the token itself, nor its hash. */
@Serializable
data class ApiToken(
    val id: String = "",
    val name: String = "",
    /** read, act or admin */
    val scope: String = "",
    val made: Long = 0,
    /** Unix time, 0 = never */
    val expires: Long = 0,
    /** last use, 0 = never */
    val last: Long = 0,
    val ip: String = "",
    val expired: Boolean = false,
)

@Serializable
data class ApiTokens(
    val ok: Boolean = false,
    val error: String = "",
    val tokens: List<ApiToken> = emptyList(),
    /** false without LABHUD_DATA: no tokens at all */
    val enabled: Boolean = false,
)

/** POST /api/tokens/create: the only answer that carries [token]. */
@Serializable
data class NewToken(
    val ok: Boolean = false,
    val error: String = "",
    val token: String = "",
    val id: String = "",
    val name: String = "",
    val scope: String = "",
)

/** One line of the audit log (GET /api/v1/audit). */
@Serializable
data class AuditRow(
    /** ISO 8601, UTC: 2026-10-18T13:01:36Z */
    val ts: String = "",
    /** admin, phone <name>, token <name>, cli, anonymous */
    val who: String = "",
    val action: String = "",
    val target: String = "",
    val ip: String = "",
    val ua: String = "",
    val ok: Boolean = false,
)

@Serializable
data class AuditPage(
    val ok: Boolean = false,
    val error: String = "",
    val rows: List<AuditRow> = emptyList(),
    val more: Boolean = false,
    val offset: Int = 0,
    val limit: Int = 100,
    val enabled: Boolean = false,
    val days: Int = 90,
)
