package com.music.streamx.ui.components

/** Screen coordinates stay meaningful while the sheet moves underneath a finger. */
internal class MenuTapGuard(private val touchSlop: Float) {
    private var downX = 0f
    private var downY = 0f
    var allowsTap = true
        private set

    fun down(x: Float, y: Float) {
        downX = x
        downY = y
        allowsTap = true
    }

    fun move(x: Float, y: Float) {
        val dx = x - downX
        val dy = y - downY
        if (dx * dx + dy * dy > touchSlop * touchSlop) allowsTap = false
    }

    fun cancel() { allowsTap = false }
}
