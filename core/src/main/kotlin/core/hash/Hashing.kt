package core.hash

import core.Event
import core.serde.serialize
import core.serde.serializeEvents
import core.state.GameState
import java.security.MessageDigest

/**
 * Computes a stable hash of a [GameState] for test-oracle usage.
 *
 * The hash is:
 * - SHA-256 over the UTF-8 bytes of a canonical JSON representation
 * - encoded as a lowercase hex string (64 chars)
 *
 * ## Contract
 * Returns a lowercase hex SHA-256 digest of `serialize(state)`.
 *
 * ## Preconditions
 * - `serialize(state)` must succeed.
 *
 * ## Postconditions
 * - Result length is 64.
 * - Result matches `sha256Hex(serialize(state))`.
 *
 * ## Determinism
 * Deterministic iff `serialize(state)` is deterministic/canonical for equivalent states.
 *
 * ## Complexity
 * - Time: O(n) where n = UTF-8 byte length of the JSON string.
 * - Memory: O(n) for the UTF-8 byte array (plus O(1) digest output).
 *
 * @param state State to hash.
 * @return Lowercase hex SHA-256 digest of the canonical JSON serialization.
 */
fun hashState(state: GameState): String {
    val json = serialize(state)
    return sha256Hex(json)
}

/**
 * Computes a stable hash of an ordered event list for test-oracle usage.
 *
 * The hash is:
 * - SHA-256 over the UTF-8 bytes of a canonical JSON representation
 * - encoded as a lowercase hex string (64 chars)
 *
 * ## Contract
 * Returns a lowercase hex SHA-256 digest of `serializeEvents(events)`.
 *
 * ## Preconditions
 * - `serializeEvents(events)` must succeed.
 *
 * ## Postconditions
 * - Result length is 64.
 * - Result matches `sha256Hex(serializeEvents(events))`.
 * - The ordering of `events` affects the hash.
 *
 * ## Determinism
 * Deterministic iff `serializeEvents(events)` is deterministic/canonical for equivalent event sequences.
 *
 * ## Complexity
 * - Time: O(n) where n = UTF-8 byte length of the JSON string.
 * - Memory: O(n) for the UTF-8 byte array (plus O(1) digest output).
 *
 * @param events Ordered list of events to hash.
 * @return Lowercase hex SHA-256 digest of the canonical JSON serialization.
 */
fun hashEvents(events: List<Event>): String {
    val json = serializeEvents(events)
    return sha256Hex(json)
}

/**
 * Computes SHA-256 over UTF-8 bytes of [input] and encodes the result as lowercase hex.
 *
 * ## Contract
 * Returns a 64-character lowercase hex string representing SHA-256(input UTF-8 bytes).
 *
 * ## Preconditions
 * - A JCA provider must support `"SHA-256"`.
 *
 * ## Postconditions
 * - Result length is 64.
 * - Output is lowercase hex.
 *
 * ## Determinism
 * Deterministic given identical [input] and SHA-256 provider behavior.
 *
 * ## Complexity
 * - Time: O(n) where n = input UTF-8 byte length.
 * - Memory: O(n) for the UTF-8 byte array.
 */
private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}
