package com.farmtourism.assistant.backend.classifier

import com.farmtourism.assistant.backend.classifier.MmBertTokenizer.AddedToken
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MmBertTokenizerTest {

    private val vocab = mapOf(
        "<pad>" to 0, "<eos>" to 1, "<bos>" to 2, "<unk>" to 3,
        "▁" to 4, "h" to 5, "i" to 6, "▁h" to 7, "▁hi" to 8,
        "<0xC3>" to 9, "<0xA9>" to 10, "a" to 11, "aa" to 12, "<mask>" to 13
    )

    private val tokenizer = MmBertTokenizer.fromParts(
        vocab = vocab,
        merges = listOf("▁" to "h", "▁h" to "i", "a" to "a"),
        addedTokens = listOf(
            AddedToken("<pad>", 0), AddedToken("<eos>", 1), AddedToken("<bos>", 2),
            AddedToken("<unk>", 3), AddedToken("<mask>", 13, lstrip = true)
        ),
        maxLength = 8
    )

    private fun assertIds(text: String, vararg body: Long) {
        val expected = LongArray(8).also { ids ->
            ids[0] = 2L
            body.forEachIndexed { i, id -> ids[i + 1] = id }
            ids[body.size + 1] = 1L
        }
        val expectedMask = LongArray(8) { if (it < body.size + 2) 1L else 0L }
        val enc = tokenizer.encode(text)
        assertArrayEquals("ids for '$text'", expected, enc.inputIds)
        assertArrayEquals("mask for '$text'", expectedMask, enc.attentionMask)
    }

    @Test fun mergesWordWithMetaspacePrefix() = assertIds("hi", 8)

    @Test fun splitsWordsOnSpaces() = assertIds("hi hi", 8, 8)

    @Test fun fallsBackToUtf8ByteTokens() = assertIds("é", 4, 9, 10)

    @Test fun fusesConsecutiveUnknownChars() = assertIds("zz", 4, 3)

    @Test fun appliesEqualRankMergesLeftmostFirst() = assertIds("aaa", 4, 12, 11)

    @Test fun matchesAddedTokensAndHonoursLstrip() = assertIds("hi <mask>", 8, 13)

    @Test fun truncatesToLeaveRoomForSpecialTokens() = assertIds("hi hi hi hi hi hi hi", 8, 8, 8, 8, 8, 8)

    @Test fun encodesEmptyTextAsBosEos() = assertIds("")
}
