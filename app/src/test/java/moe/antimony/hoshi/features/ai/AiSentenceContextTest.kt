package moe.antimony.hoshi.features.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiSentenceContextTest {
    @Test
    fun withoutContextKeepsBookFormat() {
        assertEquals("Word: 言葉\nSentence: 言葉を選ぶ。", aiUserMessage("言葉", "言葉を選ぶ。", null))
        assertEquals("言葉を選ぶ。", aiUserMessage(null, "言葉を選ぶ。", null))
    }

    @Test
    fun withContextUsesTheSubtitleBlock() {
        val context = AiSentenceContext(before = listOf("一", "二"), after = listOf("四", "五"))

        assertEquals(
            "Word: 三\nContext before:\n一\n二\nSentence: 三だ\nContext after:\n四\n五",
            aiUserMessage("三", "三だ", context),
        )
        assertEquals(
            "Context before:\n一\n二\nSentence: 三だ\nContext after:\n四\n五",
            aiUserMessage(null, "三だ", context),
        )
    }

    @Test
    fun emptyContextSidesStayLabelled() {
        assertEquals(
            "Context before:\nSentence: 最初\nContext after:\n次",
            aiUserMessage(null, "最初", AiSentenceContext(before = emptyList(), after = listOf("次"))),
        )
    }
}
