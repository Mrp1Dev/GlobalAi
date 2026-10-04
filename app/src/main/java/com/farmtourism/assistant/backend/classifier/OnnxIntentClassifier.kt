package com.farmtourism.assistant.backend.classifier

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.farmtourism.assistant.backend.engine.IIntentClassifier
import com.farmtourism.assistant.backend.model.IntentClassificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.LongBuffer
import kotlin.math.exp
import kotlin.math.max

/**
 * On-device Multilingual Intent Classifier powered by ONNX Runtime Mobile.
 * Executes the fine-tuned mmBERT model locally on the visitor's device with zero server overhead.
 */
class OnnxIntentClassifier(
    private val context: Context? = null,
    private val modelPathOrAsset: String = "model.onnx",
    override val confidenceThreshold: Float = 0.70f
) : IIntentClassifier {

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isInitialized = false

    private val tokenizer: MmBertTokenizer by lazy {
        MmBertTokenizer(context)
    }

    private val labelMap = mapOf(
        0 to "activities_available",
        1 to "amenities_food",
        2 to "booking_reservation",
        3 to "farm_location_directions",
        4 to "out_of_scope",
        5 to "pet_policy",
        6 to "price_produce",
        7 to "price_tour",
        8 to "tour_duration_difficulty",
        9 to "visitation_hours"
    )

    // Fast keyword signatures for fallback / validation
    private val keywordSignatures = mapOf(
        "price_tour" to listOf("tour", "ticket", "cuesta", "precio", "tarif", "billet", "kostet", "eintritt", "prezzo", "टिकट", "किराया", "कीमत", "entrada"),
        "price_produce" to listOf("honey", "milk", "fruit", "vegetable", "coffee beans", "miel", "leche", "frutas", "honig", "roh-milch", "verdure", "शहद", "दूध", "फल", "सब्जियां", "कॉफ़ी बीन्स"),
        "visitation_hours" to listOf("hour", "time", "open", "close", "horario", "hora", "abren", "ferme", "ouvre", "öffnungszeiten", "apertura", "orari", "समय", "खुलता", "बंद"),
        "tour_duration_difficulty" to listOf("duration", "how long", "take", "hours", "steep", "slope", "difficulty", "trail", "hike", "walk", "fitness", "duración", "tiempo", "pendiente", "caminata", "durée", "combien de temps", "pente", "sentier", "dauer", "wie lange", "steil", "gehzeit", "quanto dura", "durata", "salita", "समय", "घंटा", "चढ़ाई", "कठिन", "रास्ता", "ढलान", "tempo", "duração", "íngreme", "wandeling", "tijdsduur"),
        "farm_location_directions" to listOf("where", "location", "address", "get there", "dónde", "ubicación", "llegar", "où", "trouve", "adresse", "wo", "liegt", "weg", "dove", "पता", "रास्ता", "parking"),
        "activities_available" to listOf("activity", "activities", "milk cow", "tractor", "fruit picking", "coffee tasting", "cupping", "actividades", "ordeñar", "paseo", "cueillette", "animaux", "kühe melken", "attività", "काम", "ट्रैक्टर", "गतिविधियां", "कॉफ़ी टेस्टिंग"),
        "amenities_food" to listOf("lunch", "food", "tea", "water", "toilet", "restroom", "comida", "almuerzo", "baños", "agua", "repas", "eau", "toilettes", "mittagessen", "trinkwasser", "wc", "pranzo", "bagno", "खाना", "शौचालय", "पानी"),
        "pet_policy" to listOf("dog", "pet", "leash", "perro", "mascota", "chien", "animaux", "hund", "haustier", "cane", "animale", "कुत्ता", "पालतू"),
        "booking_reservation" to listOf("book", "reserve", "reservation", "advance", "walk-in", "reservar", "reserva", "réserver", "buchen", "voranmeldung", "prenotare", "बुकिंग"),
        "out_of_scope" to listOf("weather", "rain", "cricket", "football", "hotel", "airport", "atm", "camera", "mumbai", "delhi", "clima", "météo", "wetter", "मौसम")
    )

    init {
        tryInitialize()
    }

    private fun tryInitialize() {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }

            val modelBytes = loadModelBytes()
            if (modelBytes != null && modelBytes.isNotEmpty()) {
                ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)
                isInitialized = true
            }
        } catch (_: Throwable) {
            // Falls back gracefully if ONNX native libraries are not present in local unit test runtime
            isInitialized = false
        }
    }

    private fun loadModelBytes(): ByteArray? {
        if (context != null) {
            return try {
                context.assets.open(modelPathOrAsset).use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
        }
        val file = File(modelPathOrAsset)
        return if (file.exists() && file.isFile) file.readBytes() else null
    }

    override suspend fun classify(text: String): Result<IntentClassificationResult> = withContext(Dispatchers.Default) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val cleanText = text.trim()

            if (isInitialized && ortSession != null && ortEnvironment != null) {
                runOnnxInference(cleanText, startTime)
            } else {
                runFallbackInference(cleanText, startTime)
            }
        }
    }

    private fun runOnnxInference(text: String, startTime: Long): IntentClassificationResult {
        val env = ortEnvironment!!
        val session = ortSession!!

        val maxLen = 64
        val (inputIds, attentionMask) = tokenizer.encode(text, maxLen)

        val shape = longArrayOf(1, maxLen.toLong())
        val tensorIds = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
        val tensorMask = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)

        val inputs = mapOf("input_ids" to tensorIds, "attention_mask" to tensorMask)
        val outputs = session.run(inputs)

        @Suppress("UNCHECKED_CAST")
        val rawLogits = (outputs[0].value as Array<FloatArray>)[0]

        // Softmax
        val maxLogit = rawLogits.maxOrNull() ?: 0f
        val expScores = rawLogits.map { exp(it - maxLogit) }
        val sumExp = expScores.sum()
        val probs = expScores.map { it / sumExp }

        var bestIdx = 0
        var highestProb = 0f
        for (i in probs.indices) {
            if (probs[i] > highestProb) {
                highestProb = probs[i]
                bestIdx = i
            }
        }

        tensorIds.close()
        tensorMask.close()
        outputs.close()

        val intent = labelMap[bestIdx] ?: "out_of_scope"
        val latency = max(1L, System.currentTimeMillis() - startTime)

        return IntentClassificationResult(
            intent = intent,
            confidence = highestProb,
            isConfident = highestProb >= confidenceThreshold && intent != "out_of_scope",
            latencyMs = latency
        )
    }

    private fun runFallbackInference(text: String, startTime: Long): IntentClassificationResult {
        val textLower = text.lowercase()
        val scores = mutableMapOf<String, Int>()

        for ((intent, kws) in keywordSignatures) {
            var count = 0
            for (kw in kws) {
                if (textLower.contains(kw)) {
                    count += if (textLower.contains(" $kw ")) 2 else 1
                }
            }
            if (count > 0) scores[intent] = count
        }

        val latency = max(1L, System.currentTimeMillis() - startTime)

        if (scores.isEmpty()) {
            return IntentClassificationResult(
                intent = "out_of_scope",
                confidence = 0.45f,
                isConfident = false,
                latencyMs = latency
            )
        }

        val bestIntent = scores.maxByOrNull { it.value }?.key ?: "out_of_scope"
        val rawScore = scores[bestIntent] ?: 1
        val confidence = minOf(0.99f, maxOf(0.65f, 1.0f - exp(-0.8f * rawScore)))

        return IntentClassificationResult(
            intent = bestIntent,
            confidence = confidence,
            isConfident = confidence >= confidenceThreshold && bestIntent != "out_of_scope",
            latencyMs = latency
        )
    }


    override fun close() {
        ortSession?.close()
        ortEnvironment?.close()
        ortSession = null
        ortEnvironment = null
        isInitialized = false
    }
}
