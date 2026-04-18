package com.yongjincompany.anecdote.internal

internal fun interface Clock {
    fun now(): Long
}

internal object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
