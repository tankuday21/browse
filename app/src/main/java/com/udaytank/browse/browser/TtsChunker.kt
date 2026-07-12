package com.udaytank.browse.browser

/**
 * Splits plain article text into utterance-sized chunks for [android.speech.tts.TextToSpeech],
 * which rejects input longer than `getMaxSpeechInputLength()`. Sentences (". ", "! ", "? ",
 * or a newline from [HtmlText]) are packed greedily into chunks of at most [chunk]'s `maxLen`;
 * a single over-long sentence is hard-split at word boundaries, and a single over-long word
 * mid-word as a last resort. No blank chunks are ever emitted and no non-whitespace content
 * is dropped.
 */
object TtsChunker {

    /** A boundary after sentence-terminal punctuation followed by whitespace. */
    private val SENTENCE_END = Regex("(?<=[.!?])\\s+")

    fun chunk(text: String, maxLen: Int): List<String> {
        require(maxLen > 0) { "maxLen must be positive" }
        val sentences = text.split('\n')
            .flatMap { it.split(SENTENCE_END) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { hardSplit(it, maxLen) }
        return pack(sentences, maxLen)
    }

    /** Greedily joins pieces (already <= maxLen each) with single spaces into chunks <= maxLen. */
    private fun pack(pieces: List<String>, maxLen: Int): List<String> {
        val chunks = mutableListOf<String>()
        val sb = StringBuilder()
        for (piece in pieces) {
            when {
                sb.isEmpty() -> sb.append(piece)
                sb.length + 1 + piece.length <= maxLen -> sb.append(' ').append(piece)
                else -> {
                    chunks += sb.toString()
                    sb.setLength(0)
                    sb.append(piece)
                }
            }
        }
        if (sb.isNotEmpty()) chunks += sb.toString()
        return chunks
    }

    /** Splits one over-long sentence at word boundaries; chops words longer than maxLen mid-word. */
    private fun hardSplit(sentence: String, maxLen: Int): List<String> {
        if (sentence.length <= maxLen) return listOf(sentence)
        val words = sentence.split(' ').filter { it.isNotEmpty() }
        val pieces = mutableListOf<String>()
        val sb = StringBuilder()
        for (word in words) {
            var rest = word
            while (rest.length > maxLen) {
                if (sb.isNotEmpty()) {
                    pieces += sb.toString()
                    sb.setLength(0)
                }
                pieces += rest.substring(0, maxLen)
                rest = rest.substring(maxLen)
            }
            if (rest.isEmpty()) continue
            when {
                sb.isEmpty() -> sb.append(rest)
                sb.length + 1 + rest.length <= maxLen -> sb.append(' ').append(rest)
                else -> {
                    pieces += sb.toString()
                    sb.setLength(0)
                    sb.append(rest)
                }
            }
        }
        if (sb.isNotEmpty()) pieces += sb.toString()
        return pieces
    }
}
