package moe.antimony.hoshi.features.ai

/** Lines around the analysed sentence, e.g. neighbouring subtitle lines; only for understanding. */
data class AiSentenceContext(
    val before: List<String>,
    val after: List<String>,
)

/**
 * The user message for an AI lookup. Without context it keeps the original book format: the
 * word nuance gets "Word:/Sentence:" and the sentence breakdown gets the bare sentence.
 */
internal fun aiUserMessage(word: String?, sentence: String, context: AiSentenceContext?): String {
    if (context == null) return if (word == null) sentence else "Word: $word\nSentence: $sentence"
    return buildString {
        if (word != null) append("Word: ").append(word).append('\n')
        append("Context before:\n")
        context.before.forEach { append(it).append('\n') }
        append("Sentence: ").append(sentence).append('\n')
        append("Context after:")
        context.after.forEach { append('\n').append(it) }
    }
}
