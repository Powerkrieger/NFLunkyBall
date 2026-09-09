package com.example.nflunkyball.ble

/** Fixed small palette so a reaction fits the 1-byte [EmojiPacket.emojiCode] on the wire. */
object EmojiPalette {
    val emojis = listOf("🍺", "🔥", "😂", "👏", "😱", "🎉")

    fun codeFor(emoji: String): Byte = emojis.indexOf(emoji).takeIf { it >= 0 }?.toByte() ?: 0

    fun emojiFor(code: Byte): String = emojis.getOrElse(code.toInt()) { emojis[0] }
}
