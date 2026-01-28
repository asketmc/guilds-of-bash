package core.primitives

/**
 * ## Role
 * - Outcome bucket for contract auto-resolution when drafts expire in inbox.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/internal
 */
enum class AutoResolveBucket {
    /**
     * Draft quietly removed, no negative consequences.
     */
    GOOD,

    /**
     * Draft stays in inbox, next auto-resolve scheduled for +7 days.
     */
    NEUTRAL,

    /**
     * Draft removed and regional stability decreased.
     */
    BAD
}
