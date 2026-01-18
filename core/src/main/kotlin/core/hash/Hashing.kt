package core.hash

import core.Event
import core.serde.serialize
import core.serde.serializeEvents
import core.state.GameState
import java.security.MessageDigest

/**
 * Stable hashing for test-oracle usage.
 * - SHA-256 of canonical JSON
 * - Output as lowercase hex string (64 chars)
 */

fun hashState(state: GameState): String {
    val json = serialize(state)
    return sha256Hex(json)
}

fun hashEvents(events: List<Event>): String {
    val json = serializeEvents(events)
    return sha256Hex(json)
}

private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}
