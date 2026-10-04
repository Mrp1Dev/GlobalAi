package com.farmtourism.assistant.backend.classifier

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.farmtourism.assistant.backend.engine.IIntentClassifier
import com.farmtourism.assistant.backend.model.IntentClassificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.LongBuffer
import kotlin.math.exp
import kotlin.math.max

/**
 * On-device Multilingual Intent Classifier powered by ONNX Runtime Mobile.
 * Executes the fine-tuned mmBERT model locally on the visitor's device with zero server overhead.
 *
 * Artifacts (all written to app/src/main/assets by `data/train_classifier.py --export-onnx`):
 *  - model.onnx      fine-tuned INT8 classifier (inputs: input_ids, attention_mask -> logits)
 *  - tokenizer.json  HF tokenizer used in training, run on-device by [MmBertTokenizer]
 *  - label_map.json  logit index -> intent name
 *
 * Loading is lazy and off the main thread (call [warmUp] at startup). If any artifact is
 * missing or broken, the classifier falls back to keyword matching and reports it via
 * [activeEngine] / [loadError] instead of crashing.
 */
class OnnxIntentClassifier(
    private val context: Context? = null,
    private val modelPathOrAsset: String = "model.onnx",
    private val tokenizerPathOrAsset: String = "tokenizer.json",
    private val labelMapPathOrAsset: String = "label_map.json",
    override val confidenceThreshold: Float = 0.70f
) : IIntentClassifier {

    enum class Engine { NOT_LOADED, ONNX_MODEL, KEYWORD_FALLBACK }

    @Volatile
    var activeEngine: Engine = Engine.NOT_LOADED
        private set

    /** Why the ONNX model is not in use (null when it loaded fine). */
    @Volatile
    var loadError: String? = null
        private set

    private val loadMutex = Mutex()
    private var ortSession: OrtSession? = null
    private var tokenizer: MmBertTokenizer? = null
    private var labelMap: Map<Int, String> = DEFAULT_LABEL_MAP

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

    /** Loads tokenizer + model in the background so the first tourist query is fast. */
    override suspend fun warmUp() {
        ensureLoaded()
    }

    override suspend fun classify(text: String): Result<IntentClassificationResult> = runCatching {
        ensureLoaded()
        withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            val cleanText = text.trim()
            val session = ortSession
            val tok = tokenizer

            if (activeEngine == Engine.ONNX_MODEL && session != null && tok != null) {
                try {
                    runOnnxInference(session, tok, cleanText, startTime)
                } catch (t: Throwable) {
                    log("ONNX inference failed, using keyword fallback for this query: $t")
                    runFallbackInference(cleanText, startTime)
                }
            } else {
                runFallbackInference(cleanText, startTime)
            }
        }
    }

    private suspend fun ensureLoaded() {
        if (activeEngine != Engine.NOT_LOADED) return
        loadMutex.withLock {
            if (activeEngine != Engine.NOT_LOADED) return
            withContext(Dispatchers.IO) {
                activeEngine = try {
                    loadModel()
                    loadError = null
                    log("mmBERT ONNX classifier ready (${labelMap.size} intents, maxLength=${tokenizer?.maxLength})")
                    Engine.ONNX_MODEL
                } catch (t: Throwable) {
                    ortSession?.close()
                    ortSession = null
                    loadError = t.message ?: t.toString()
                    log("ONNX classifier unavailable, using keyword fallback: $loadError")
                    Engine.KEYWORD_FALLBACK
                }
            }
        }
    }

    private fun loadModel() {
        labelMap = loadLabelMap()

        val tok = openSource(tokenizerPathOrAsset)?.use { MmBertTokenizer.load(it) }
            ?: throw IllegalStateException("Tokenizer '$tokenizerPathOrAsset' not found")

        val modelFile = resolveModelFile()
            ?: throw IllegalStateException(
                "Model '$modelPathOrAsset' not found. Run `python data/train_classifier.py --export-onnx` " +
                    "to fine-tune mmBERT and copy model.onnx into app/src/main/assets/."
            )

        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        // Load from a file path so ONNX Runtime reads the model natively instead of
        // holding a 100+ MB ByteArray on the Java heap (OOM risk on budget phones).
        val session = OrtEnvironment.getEnvironment().createSession(modelFile.absolutePath, sessionOptions)
        ortSession = session
        tokenizer = tok

        // Sanity check + warm-up: the logits must line up with label_map.json.
        val logits = runLogits(session, tok, "hello")
        check(logits.size == labelMap.size) {
            "Model outputs ${logits.size} logits but label map has ${labelMap.size} intents"
        }
    }

    private fun runOnnxInference(
        session: OrtSession,
        tok: MmBertTokenizer,
        text: String,
        startTime: Long
    ): IntentClassificationResult {
        val rawLogits = runLogits(session, tok, text)

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

        val intent = labelMap[bestIdx] ?: "out_of_scope"
        val latency = max(1L, System.currentTimeMillis() - startTime)

        return IntentClassificationResult(
            intent = intent,
            confidence = highestProb,
            isConfident = highestProb >= confidenceThreshold && intent != "out_of_scope",
            latencyMs = latency
        )
    }

    private fun runLogits(session: OrtSession, tok: MmBertTokenizer, text: String): FloatArray {
        val env = OrtEnvironment.getEnvironment()
        val encoding = tok.encode(text)
        val shape = longArrayOf(1, encoding.inputIds.size.toLong())

        val inputs = HashMap<String, OnnxTensor>()
        try {
            for (name in session.inputNames) {
                val data = when (name) {
                    "input_ids" -> encoding.inputIds
                    "attention_mask" -> encoding.attentionMask
                    "token_type_ids" -> LongArray(encoding.inputIds.size)
                    else -> throw IllegalStateException("Unexpected model input '$name'")
                }
                inputs[name] = OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape)
            }
            session.run(inputs).use { outputs ->
                @Suppress("UNCHECKED_CAST")
                return (outputs[0].value as Array<FloatArray>)[0]
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
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

    private fun loadLabelMap(): Map<Int, String> {
        val json = openSource(labelMapPathOrAsset)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return DEFAULT_LABEL_MAP
        val idToLabel = JSONObject(json).getJSONObject("id_to_label")
        return idToLabel.keys().asSequence().associate { it.toInt() to idToLabel.getString(it) }
    }

    /** Opens an absolute/relative file path if it exists, otherwise an APK asset. */
    private fun openSource(pathOrAsset: String): InputStream? {
        val file = File(pathOrAsset)
        if (file.isFile) return file.inputStream()
        return try {
            context?.assets?.open(pathOrAsset)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * ONNX Runtime needs a real file path, so the bundled asset is extracted once into
     * no-backup storage and re-extracted only when the app is updated.
     */
    private fun resolveModelFile(): File? {
        File(modelPathOrAsset).takeIf { it.isFile }?.let { return it }
        val ctx = context ?: return null

        val target = File(ctx.noBackupFilesDir, "onnx/$modelPathOrAsset")
        val stampFile = File(target.parentFile, "$modelPathOrAsset.stamp")
        @Suppress("DEPRECATION")
        val stamp = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()
        if (target.isFile && stampFile.isFile && stampFile.readText() == stamp) return target

        val asset = try {
            ctx.assets.open(modelPathOrAsset)
        } catch (_: Exception) {
            return null
        }
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        asset.use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
        check(tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))) {
            "Could not extract $modelPathOrAsset"
        }
        stampFile.writeText(stamp)
        return target
    }

    private fun log(message: String) {
        // android.util.Log is a stub in plain JVM unit tests.
        runCatching { Log.i(TAG, message) }
    }

    override fun close() {
        // The OrtEnvironment is a process-wide singleton; only the session is ours to close.
        ortSession?.close()
        ortSession = null
        tokenizer = null
        activeEngine = Engine.NOT_LOADED
    }

    private companion object {
        const val TAG = "OnnxIntentClassifier"

        // Used only if label_map.json is missing; must match data/output/label_map.json.
        val DEFAULT_LABEL_MAP = mapOf(
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
    }
}
