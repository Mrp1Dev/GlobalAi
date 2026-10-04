package com.farmtourism.assistant.backend.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.farmtourism.assistant.backend.model.IntentTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream

/**
 * High-performance, offline-first on-device SQLite database implementation for Noor's phone.
 * Supports persistent SQLite on Android devices and seamless in-memory execution for unit tests.
 */
class LocalFarmDatabase(
    private val context: Context? = null,
    dbName: String = "farm_tourism_assistant.db"
) : IFarmDatabase {

    private val mutex = Mutex()
    private val inMemorySlots = mutableMapOf<String, String>()
    private val inMemoryTemplates = mutableMapOf<String, IntentTemplate>()

    private val dbHelper: SQLiteOpenHelper? = if (context != null) {
        object : SQLiteOpenHelper(context, dbName, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE slots (
                        slot_key TEXT PRIMARY KEY,
                        slot_value TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE templates (
                        intent TEXT PRIMARY KEY,
                        slot_key TEXT NOT NULL,
                        description TEXT NOT NULL,
                        prompts_json TEXT NOT NULL,
                        default_reply TEXT NOT NULL,
                        sample_value TEXT
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                db.execSQL("DROP TABLE IF EXISTS slots")
                db.execSQL("DROP TABLE IF EXISTS templates")
                onCreate(db)
            }
        }
    } else null

    init {
        // Pre-seed default templates and baseline farm profile
        seedDefaultData()
    }

    private fun seedDefaultData() {
        // Baseline Farm Slots
        inMemorySlots["tour_price_inr"] = "500"
        inMemorySlots["opening_hours"] = "9:00 AM to 6:00 PM (Tuesday to Sunday)"
        inMemorySlots["location_directions"] = "Ondera Highland Coffee Farm, 5 km off Main Mountain Road"
        inMemorySlots["tour_duration_difficulty_info"] = "1.5 to 2 hours of gentle walking along highland coffee trails; comfortable shoes recommended"

        var loadedFromAssets = false
        if (context != null) {
            try {
                context.assets.open("intents.json").use { stream ->
                    val jsonStr = stream.bufferedReader().readText()
                    val root = JSONObject(jsonStr)
                    val intentsArray = root.getJSONArray("intents")
                    for (i in 0 until intentsArray.length()) {
                        val obj = intentsArray.getJSONObject(i)
                        val intent = obj.getString("intent")
                        val slotKey = obj.getString("slot_key")
                        val desc = obj.optString("description", "")
                        val defaultReply = obj.getString("default_reply_template")
                        val sampleVal = obj.optString("sample_slot_value", "")

                        val noorMap = mutableMapOf<String, String>()
                        if (obj.has("noor_prompt_template")) {
                            val promptObj = obj.getJSONObject("noor_prompt_template")
                            val keys = promptObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                noorMap[k] = promptObj.getString(k)
                            }
                        }

                        val replyVariations = mutableListOf<String>()
                        if (obj.has("reply_template_variations")) {
                            val rArr = obj.getJSONArray("reply_template_variations")
                            for (j in 0 until rArr.length()) {
                                replyVariations.add(rArr.getString(j))
                            }
                        }
                        if (replyVariations.isEmpty()) {
                            replyVariations.add(defaultReply)
                        }

                        val noorVariations = mutableMapOf<String, List<String>>()
                        if (obj.has("noor_prompt_variations")) {
                            val vObj = obj.getJSONObject("noor_prompt_variations")
                            val keys = vObj.keys()
                            while (keys.hasNext()) {
                                val lang = keys.next()
                                val list = mutableListOf<String>()
                                val arr = vObj.getJSONArray(lang)
                                for (k in 0 until arr.length()) {
                                    list.add(arr.getString(k))
                                }
                                noorVariations[lang] = list
                            }
                        }

                        val template = IntentTemplate(
                            intent = intent,
                            slotKey = slotKey,
                            description = desc,
                            noorPromptTemplates = noorMap,
                            defaultReplyTemplate = defaultReply,
                            sampleSlotValue = sampleVal,
                            replyTemplateVariations = replyVariations,
                            noorPromptVariations = noorVariations
                        )
                        inMemoryTemplates[intent] = template
                    }
                    loadedFromAssets = true
                }
            } catch (_: Exception) {
                loadedFromAssets = false
            }
        }

        if (loadedFromAssets) return

        // Baseline Intent Templates (aligned with intents.json)
        val defaultTemplates = listOf(
            IntentTemplate(
                intent = "price_tour",
                slotKey = "tour_price_inr",
                description = "Tour pricing and group rates",
                noorPromptTemplates = mapOf(
                    "hi" to "इस फार्म टूर की प्रति व्यक्ति कीमत क्या है?"
                ),
                defaultReplyTemplate = "The price for a guided farm tour is ₹{tour_price_inr} per person.",
                sampleSlotValue = "500"
            ),
            IntentTemplate(
                intent = "price_produce",
                slotKey = "produce_pricing_info",
                description = "Cost of fruits, vegetables, honey, milk",
                noorPromptTemplates = mapOf(
                    "hi" to "खेत के ताजे फल, सब्जियों और जैविक उत्पादों की कीमत क्या है?"
                ),
                defaultReplyTemplate = "Our fresh produce prices: {produce_pricing_info}.",
                sampleSlotValue = "Organic honey is ₹400/jar, seasonal fruits are ₹80/kg"
            ),
            IntentTemplate(
                intent = "visitation_hours",
                slotKey = "opening_hours",
                description = "Opening times, seasonal availability",
                noorPromptTemplates = mapOf(
                    "hi" to "खेत पर्यटकों के लिए कब खुलता और बंद होता है?"
                ),
                defaultReplyTemplate = "The farm is open for visitors from {opening_hours}.",
                sampleSlotValue = "9:00 AM to 6:00 PM Tuesday through Sunday"
            ),
            IntentTemplate(
                intent = "farm_location_directions",
                slotKey = "location_directions",
                description = "Directions, public transport, parking",
                noorPromptTemplates = mapOf(
                    "hi" to "खेत का पता और यहाँ पहुँचने का रास्ता क्या है?"
                ),
                defaultReplyTemplate = "The farm is located at {location_directions}. Free parking is available on site.",
                sampleSlotValue = "5 km off Highway 48, near Lotus Village"
            ),
            IntentTemplate(
                intent = "activities_available",
                slotKey = "farm_activities",
                description = "Milking cows, fruit picking, tractor rides",
                noorPromptTemplates = mapOf(
                    "hi" to "पर्यटक खेत पर क्या-क्या गतिविधियां कर सकते हैं?"
                ),
                defaultReplyTemplate = "Activities available on the farm include: {farm_activities}.",
                sampleSlotValue = "fruit picking, cow milking, tractor rides, and organic farming workshops"
            ),
            IntentTemplate(
                intent = "amenities_food",
                slotKey = "amenities_food_info",
                description = "Farm lunch, tea, restrooms, drinking water",
                noorPromptTemplates = mapOf(
                    "hi" to "खेत में भोजन, चाय, शौचालय और विश्राम की क्या सुविधाएं हैं?"
                ),
                defaultReplyTemplate = "Farm amenities: {amenities_food_info}.",
                sampleSlotValue = "clean restrooms, safe drinking water, and authentic vegetarian lunch"
            ),
            IntentTemplate(
                intent = "pet_policy",
                slotKey = "pet_policy_rules",
                description = "Rules regarding dogs and domestic pets",
                noorPromptTemplates = mapOf(
                    "hi" to "खेत में पालतू जानवरों (जैसे कुत्तों) के लिए क्या नियम हैं?"
                ),
                defaultReplyTemplate = "Our pet policy: {pet_policy_rules}.",
                sampleSlotValue = "friendly dogs on leashes are warmly welcome"
            ),
            IntentTemplate(
                intent = "booking_reservation",
                slotKey = "booking_requirements",
                description = "Advance booking vs walk-in entry",
                noorPromptTemplates = mapOf(
                    "hi" to "क्या खेत आने के लिए पहले से बुकिंग जरूरी है या सीधे आ सकते हैं?"
                ),
                defaultReplyTemplate = "Booking policy: {booking_requirements}.",
                sampleSlotValue = "advance booking is recommended on weekends, walk-ins welcome on weekdays"
            ),
            IntentTemplate(
                intent = "tour_duration_difficulty",
                slotKey = "tour_duration_difficulty_info",
                description = "Tour duration, walking trail difficulty, and slope terrain",
                noorPromptTemplates = mapOf(
                    "hi" to "खेत भ्रमण (टूर) में कितना समय लगता है और चढ़ाई का रास्ता कैसा है?"
                ),
                defaultReplyTemplate = "Our guided farm tour takes about {tour_duration_difficulty_info}.",
                sampleSlotValue = "1.5 to 2 hours of gentle walking along highland coffee trails; comfortable walking shoes recommended"
            ),
            IntentTemplate(
                intent = "out_of_scope",
                slotKey = "none",
                description = "General small talk, weather, or out of scope queries",
                noorPromptTemplates = mapOf(
                    "hi" to "यह प्रश्न सीधा अनुवाद के जरिए नूर तक पहुँचाया जाएगा।"
                ),
                defaultReplyTemplate = "Let me connect you directly with Noor for your question.",
                sampleSlotValue = ""
            )
        )

        for (template in defaultTemplates) {
            inMemoryTemplates[template.intent] = template
        }
    }

    override suspend fun getSlot(slotKey: String): String? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (dbHelper != null) {
                val db = dbHelper.readableDatabase
                val cursor = db.query(
                    "slots",
                    arrayOf("slot_value"),
                    "slot_key = ?",
                    arrayOf(slotKey),
                    null, null, null
                )
                cursor.use {
                    if (it.moveToFirst()) it.getString(0) else inMemorySlots[slotKey]
                }
            } else {
                inMemorySlots[slotKey]
            }
        }
    }

    override suspend fun setSlot(slotKey: String, value: String): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            inMemorySlots[slotKey] = value
            if (dbHelper != null) {
                val db = dbHelper.writableDatabase
                val values = ContentValues().apply {
                    put("slot_key", slotKey)
                    put("slot_value", value)
                    put("updated_at", System.currentTimeMillis())
                }
                db.insertWithOnConflict("slots", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    override suspend fun hasSlot(slotKey: String): Boolean = mutex.withLock {
        val value = inMemorySlots[slotKey]
        !value.isNullOrBlank()
    }

    override suspend fun getAllSlots(): Map<String, String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val result = HashMap(inMemorySlots)
            if (dbHelper != null) {
                val db = dbHelper.readableDatabase
                val cursor = db.query("slots", arrayOf("slot_key", "slot_value"), null, null, null, null, null)
                cursor.use {
                    while (it.moveToNext()) {
                        result[it.getString(0)] = it.getString(1)
                    }
                }
            }
            result
        }
    }

    override suspend fun deleteSlot(slotKey: String): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            inMemorySlots.remove(slotKey)
            if (dbHelper != null) {
                val db = dbHelper.writableDatabase
                db.delete("slots", "slot_key = ?", arrayOf(slotKey))
            }
        }
    }

    override suspend fun getTemplate(intent: String): IntentTemplate? = mutex.withLock {
        inMemoryTemplates[intent]
    }

    override suspend fun saveTemplate(template: IntentTemplate): Unit = mutex.withLock {
        inMemoryTemplates[template.intent] = template
    }

    override suspend fun getAllTemplates(): List<IntentTemplate> = mutex.withLock {
        inMemoryTemplates.values.toList()
    }

    override suspend fun clear(): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            inMemorySlots.clear()
            seedDefaultData()
            if (dbHelper != null) {
                val db = dbHelper.writableDatabase
                db.delete("slots", null, null)
            }
        }
    }
}
