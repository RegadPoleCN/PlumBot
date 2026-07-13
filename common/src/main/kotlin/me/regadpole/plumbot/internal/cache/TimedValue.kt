package me.regadpole.plumbot.internal.cache

import java.util.concurrent.CompletableFuture

data class TimedValue<V>(
    val future: CompletableFuture<V>,
    val timestamp: Long = System.currentTimeMillis()
)
