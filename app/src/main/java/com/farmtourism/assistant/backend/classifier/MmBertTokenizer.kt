package com.farmtourism.assistant.backend.classifier

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

/**
 * Pure-Kotlin port of the Hugging Face tokenizer shipped in `assets/tokenizer.json`
 * (mmBERT / Gemma-style SentencePiece BPE with byte fallback).
 *
 * Mirrors `tokenizers` encode() for this config, so on-device input ids match training:
 *   added-token split -> Replace(" " -> "▁") -> Metaspace(prepend always, split)
 *   -> BPE (byte fallback, fuse unk) -> <bos> ... <eos> -> truncate / pad to [maxLength].
 *
 * The algorithm was checked token-for-token against HF `tokenizers` on the full
 * training set plus emoji, Devanagari, CJK, whitespace and special-token edge cases.
 */
class MmBertTokenizer internal constructor(
    private val vocab: Map<String, Int>,
    private val merges: MergeTable,
    addedTokens: List<AddedToken>,
    val maxLength: Int = DEFAULT_MAX_LENGTH
) {

    data class AddedToken(
        val content: String,
        val id: Int,
        val lstrip: Boolean = false,
        val rstrip: Boolean = false
    )

    class Encoding(val inputIds: LongArray, val attentionMask: LongArray)

    private val bosId = idOf("<bos>", addedTokens)
    private val eosId = idOf("<eos>", addedTokens)
    private val padId = idOf("<pad>", addedTokens)
    private val unkId = idOf("<unk>", addedTokens)

    // Added tokens grouped by first char, longest first (leftmost-longest matching like HF).
    private val addedByFirstChar: Map<Char, List<AddedToken>> = addedTokens
        .filter { it.content.isNotEmpty() }
        .sortedByDescending { it.content.length }
        .groupBy { it.content[0] }

    // Vocab ids of the 256 "<0xXX>" byte-fallback tokens (-1 when missing).
    private val byteIds = IntArray(256) { b -> vocab[String.format(Locale.ROOT, "<0x%02X>", b)] ?: -1 }

    init {
        require(maxLength >= 2) { "maxLength must leave room for <bos> and <eos>" }
    }

    fun encode(text: String): Encoding {
        val ids = ArrayList<Int>(maxLength)
        splitOnAddedTokens(text) { segment, addedId ->
            if (addedId >= 0) ids.add(addedId) else tokenizeSegment(segment, ids)
        }

        val bodyLen = minOf(ids.size, maxLength - 2)
        val inputIds = LongArray(maxLength) { padId.toLong() }
        val attentionMask = LongArray(maxLength)
        inputIds[0] = bosId.toLong()
        for (i in 0 until bodyLen) inputIds[i + 1] = ids[i].toLong()
        inputIds[bodyLen + 1] = eosId.toLong()
        for (i in 0..bodyLen + 1) attentionMask[i] = 1L
        return Encoding(inputIds, attentionMask)
    }

    /** Emits plain-text segments (addedId = -1) and added/special tokens in order. */
    private inline fun splitOnAddedTokens(text: String, emit: (String, Int) -> Unit) {
        val buf = StringBuilder()
        var i = 0
        while (i < text.length) {
            val match = addedByFirstChar[text[i]]?.firstOrNull { text.startsWith(it.content, i) }
            if (match == null) {
                buf.append(text[i])
                i++
                continue
            }
            if (match.lstrip) {
                while (buf.isNotEmpty() && buf[buf.length - 1].isWhitespace()) buf.setLength(buf.length - 1)
            }
            if (buf.isNotEmpty()) {
                emit(buf.toString(), -1)
                buf.setLength(0)
            }
            emit(match.content, match.id)
            i += match.content.length
            if (match.rstrip) {
                while (i < text.length && text[i].isWhitespace()) i++
            }
        }
        if (buf.isNotEmpty()) emit(buf.toString(), -1)
    }

    /** Replace + Metaspace: spaces become "▁", prepend "▁", split before every "▁". */
    private fun tokenizeSegment(segment: String, out: MutableList<Int>) {
        var s = segment.replace(' ', SPACE)
        if (!s.startsWith(SPACE)) s = SPACE + s
        var start = 0
        for (i in 1 until s.length) {
            if (s[i] == SPACE) {
                bpe(s.substring(start, i), out)
                start = i
            }
        }
        bpe(s.substring(start), out)
    }

    private fun bpe(word: String, out: MutableList<Int>) {
        // 1. One symbol per Unicode code point; unknown chars fall back to UTF-8 byte tokens.
        var symbols = IntArray(word.length * 4)
        var n = 0
        var lastWasUnk = false
        var i = 0
        while (i < word.length) {
            val cpLen = Character.charCount(word.codePointAt(i))
            val ch = word.substring(i, i + cpLen)
            i += cpLen

            val id = vocab[ch]
            if (id != null) {
                symbols[n++] = id
                lastWasUnk = false
                continue
            }
            val bytes = ch.toByteArray(Charsets.UTF_8)
            if (bytes.all { byteIds[it.toInt() and 0xFF] >= 0 }) {
                if (n + bytes.size > symbols.size) symbols = symbols.copyOf(symbols.size * 2 + bytes.size)
                for (b in bytes) symbols[n++] = byteIds[b.toInt() and 0xFF]
                lastWasUnk = false
            } else if (!lastWasUnk) { // fuse_unk: consecutive unknowns collapse into one <unk>
                symbols[n++] = unkId
                lastWasUnk = true
            }
        }

        // 2. Repeatedly apply the lowest-rank merge (leftmost on ties) until none applies.
        while (n > 1) {
            var bestRank = Int.MAX_VALUE
            var bestPos = -1
            var bestId = -1
            for (p in 0 until n - 1) {
                val slot = merges.find(symbols[p], symbols[p + 1])
                if (slot >= 0 && merges.rankAt(slot) < bestRank) {
                    bestRank = merges.rankAt(slot)
                    bestPos = p
                    bestId = merges.newIdAt(slot)
                }
            }
            if (bestPos < 0) break
            symbols[bestPos] = bestId
            System.arraycopy(symbols, bestPos + 2, symbols, bestPos + 1, n - bestPos - 2)
            n--
        }

        for (k in 0 until n) out.add(symbols[k])
    }

    private fun idOf(token: String, addedTokens: List<AddedToken>): Int =
        addedTokens.firstOrNull { it.content == token }?.id
            ?: vocab[token]
            ?: throw IllegalStateException("Tokenizer is missing special token $token")

    /**
     * Open-addressing map (leftId, rightId) -> (rank, mergedId).
     * Primitive arrays keep ~580k merges at ~16 MB instead of ~60 MB of boxed HashMap entries.
     */
    internal class MergeTable(expectedSize: Int) {
        private val bits: Int
        private val keys: LongArray
        private val ranks: IntArray
        private val newIds: IntArray
        private var size = 0

        init {
            var capacity = 16
            while (capacity * LOAD_FACTOR < expectedSize) capacity = capacity shl 1
            bits = Integer.numberOfTrailingZeros(capacity)
            keys = LongArray(capacity) { EMPTY }
            ranks = IntArray(capacity)
            newIds = IntArray(capacity)
        }

        /** Later duplicates overwrite earlier ones, matching HF's HashMap collect. */
        fun put(left: Int, right: Int, rank: Int, newId: Int) {
            val key = pack(left, right)
            var idx = slotFor(key)
            while (keys[idx] != EMPTY && keys[idx] != key) idx = (idx + 1) and (keys.size - 1)
            if (keys[idx] == EMPTY) {
                check(++size < keys.size) { "MergeTable full: raise its expected size" }
            }
            keys[idx] = key
            ranks[idx] = rank
            newIds[idx] = newId
        }

        /** Returns the slot index, or -1 when the pair has no merge. */
        fun find(left: Int, right: Int): Int {
            val key = pack(left, right)
            var idx = slotFor(key)
            while (true) {
                val k = keys[idx]
                if (k == key) return idx
                if (k == EMPTY) return -1
                idx = (idx + 1) and (keys.size - 1)
            }
        }

        fun rankAt(slot: Int): Int = ranks[slot]
        fun newIdAt(slot: Int): Int = newIds[slot]

        private fun pack(left: Int, right: Int): Long = (left.toLong() shl 32) or (right.toLong() and 0xFFFFFFFFL)
        private fun slotFor(key: Long): Int = ((key * -7046029254386353131L) ushr (64 - bits)).toInt()

        private companion object {
            const val EMPTY = -1L // packed keys of non-negative ids are always >= 0
            const val LOAD_FACTOR = 0.6
        }
    }

    companion object {
        const val DEFAULT_MAX_LENGTH = 64
        private const val SPACE = '▁'

        /** Test/helper factory from in-memory parts. */
        internal fun fromParts(
            vocab: Map<String, Int>,
            merges: List<Pair<String, String>>,
            addedTokens: List<AddedToken>,
            maxLength: Int = DEFAULT_MAX_LENGTH
        ): MmBertTokenizer {
            val table = MergeTable(merges.size)
            merges.forEachIndexed { rank, (a, b) -> addMerge(table, vocab, a, b, rank) }
            return MmBertTokenizer(vocab, table, addedTokens, maxLength)
        }

        /**
         * Streams a HF `tokenizer.json` (37 MB for mmBERT) without building a DOM.
         * Expects "model.vocab" to appear before "model.merges", as `tokenizers` writes it.
         */
        fun load(input: InputStream): MmBertTokenizer {
            var addedTokens: List<AddedToken> = emptyList()
            var vocab: HashMap<String, Int>? = null
            var merges: MergeTable? = null
            var maxLength = DEFAULT_MAX_LENGTH

            JsonReader(InputStreamReader(input.buffered(1 shl 16), Charsets.UTF_8)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "added_tokens" -> addedTokens = readAddedTokens(r)
                        "truncation" -> maxLength = readMaxLength(r) ?: maxLength
                        "model" -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "type" -> {
                                        val type = r.nextString()
                                        check(type == "BPE") { "Unsupported tokenizer model type: $type" }
                                    }
                                    "vocab" -> vocab = readVocab(r)
                                    "merges" -> merges = readMerges(
                                        r,
                                        vocab ?: throw IllegalStateException("tokenizer.json: merges appear before vocab")
                                    )
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }

            return MmBertTokenizer(
                vocab ?: throw IllegalStateException("tokenizer.json has no model.vocab"),
                merges ?: throw IllegalStateException("tokenizer.json has no model.merges"),
                addedTokens,
                maxLength
            )
        }

        private fun readAddedTokens(r: JsonReader): List<AddedToken> {
            val tokens = ArrayList<AddedToken>()
            r.beginArray()
            while (r.hasNext()) {
                var content = ""
                var id = -1
                var lstrip = false
                var rstrip = false
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "content" -> content = r.nextString()
                        "id" -> id = r.nextInt()
                        "lstrip" -> lstrip = r.nextBoolean()
                        "rstrip" -> rstrip = r.nextBoolean()
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                if (id >= 0) tokens.add(AddedToken(content, id, lstrip, rstrip))
            }
            r.endArray()
            return tokens
        }

        private fun readMaxLength(r: JsonReader): Int? {
            if (r.peek() == JsonToken.NULL) {
                r.nextNull()
                return null
            }
            var maxLength: Int? = null
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() == "max_length") maxLength = r.nextInt() else r.skipValue()
            }
            r.endObject()
            return maxLength
        }

        private fun readVocab(r: JsonReader): HashMap<String, Int> {
            val vocab = HashMap<String, Int>(1 shl 19)
            r.beginObject()
            while (r.hasNext()) vocab[r.nextName()] = r.nextInt()
            r.endObject()
            return vocab
        }

        private fun readMerges(r: JsonReader, vocab: Map<String, Int>): MergeTable {
            val table = MergeTable(EXPECTED_MERGES)
            var rank = 0
            r.beginArray()
            while (r.hasNext()) {
                val left: String
                val right: String
                if (r.peek() == JsonToken.BEGIN_ARRAY) { // newer format: ["a", "b"]
                    r.beginArray()
                    left = r.nextString()
                    right = r.nextString()
                    r.endArray()
                } else { // legacy format: "a b"
                    val merge = r.nextString()
                    val space = merge.indexOf(' ')
                    left = merge.substring(0, space)
                    right = merge.substring(space + 1)
                }
                addMerge(table, vocab, left, right, rank++)
            }
            r.endArray()
            return table
        }

        private fun addMerge(table: MergeTable, vocab: Map<String, Int>, left: String, right: String, rank: Int) {
            val leftId = vocab[left] ?: return
            val rightId = vocab[right] ?: return
            val newId = vocab[left + right] ?: return
            table.put(leftId, rightId, rank, newId)
        }

        private const val EXPECTED_MERGES = 600_000
    }
}
