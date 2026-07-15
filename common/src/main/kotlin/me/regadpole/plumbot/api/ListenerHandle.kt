package me.regadpole.plumbot.api

/**
 * Handle returned from event subscription APIs.
 *
 * Implementations **SHALL** make [close] idempotent: calling [close] multiple
 * times MUST NOT throw and MUST NOT duplicate-side effects.
 */
@PublicApi
fun interface ListenerHandle {
    fun close()
}
