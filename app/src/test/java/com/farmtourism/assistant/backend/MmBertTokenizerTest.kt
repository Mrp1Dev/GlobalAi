package com.farmtourism.assistant.backend

import com.farmtourism.assistant.backend.classifier.MmBertTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MmBertTokenizerTest {

    private lateinit var tokenizer: MmBertTokenizer

    @Before
    fun setup() {
        tokenizer = MmBertTokenizer()
    }

    @Test
    fun testEnglishTokenization_StructureAndSpecialTokens() {
        val prompt = "How much does a guided tour cost"
        val (inputIds, attentionMask) = tokenizer.encode(prompt, maxLen = 32)

        assertEquals(32, inputIds.size)
        assertEquals(32, attentionMask.size)

        // BOS token should be at index 0
        assertEquals(tokenizer.bosTokenId, inputIds[0])
        assertEquals(1L, attentionMask[0])

        // First word should have a valid token ID > 3 (not special token)
        assertTrue(inputIds[1] > 3L)

        // Find EOS token
        val eosIndex = inputIds.indexOf(tokenizer.eosTokenId)
        assertTrue("EOS token should be present in input_ids", eosIndex > 1)
        assertEquals(1L, attentionMask[eosIndex])

        // Padding tokens after EOS should be padTokenId (0L) and attention_mask 0L
        for (i in (eosIndex + 1) until 32) {
            assertEquals("Padding token should be 0", tokenizer.padTokenId, inputIds[i])
            assertEquals("Padding attention mask should be 0", 0L, attentionMask[i])
        }
    }

    @Test
    fun testHindiTokenization_ProducesValidTokens() {
        val hindiPrompt = "टूर की कीमत क्या है"
        val tokens = tokenizer.tokenize(hindiPrompt)

        assertTrue("Hindi tokenization should produce tokens", tokens.isNotEmpty())
        for (t in tokens) {
            assertTrue("Token ID should be positive", t > 0L)
            assertNotEquals("Should not be UNK if vocab is loaded", tokenizer.unkTokenId, t)
        }
    }

    @Test
    fun testDeterministicTokenization() {
        val text = "What are the visiting hours of the farm?"
        val run1 = tokenizer.tokenize(text)
        val run2 = tokenizer.tokenize(text)

        assertEquals("Tokenization must be deterministic", run1, run2)
    }
}
