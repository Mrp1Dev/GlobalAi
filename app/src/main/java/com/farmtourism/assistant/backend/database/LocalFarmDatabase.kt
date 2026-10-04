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
                        val sampleValHi = obj.optString("sample_slot_value_hi", "")
                        val defaultReplyHi = obj.optString("default_reply_template_hi", "")

                        val sampleMap = mutableMapOf<String, String>()
                        if (sampleVal.isNotBlank()) sampleMap["en"] = sampleVal
                        if (sampleValHi.isNotBlank()) sampleMap["hi"] = sampleValHi

                        val replyMap = mutableMapOf<String, String>()
                        replyMap["en"] = defaultReply
                        if (defaultReplyHi.isNotBlank()) replyMap["hi"] = defaultReplyHi

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
                            sampleSlotValuesByLang = sampleMap,
                            replyTemplatesByLang = replyMap,
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
                sampleSlotValue = "500",
                sampleSlotValuesByLang = mapOf("en" to "500 rs", "hi" to "₹500 प्रति व्यक्ति"),
                replyTemplatesByLang = mapOf("hi" to "हमारे कॉफी फार्म वॉक का टिकट प्रति व्यक्ति {tour_price_inr} है, जिसमें गाइडेड टूर और टेस्टिंग शामिल है।")
            ),
            IntentTemplate(
                intent = "price_produce",
                slotKey = "produce_pricing_info",
                description = "Cost of fruits, vegetables, honey, milk",
                noorPromptTemplates = mapOf(
                    "hi" to "खेत के ताजे फल, सब्जियों और जैविक उत्पादों की कीमत क्या है?"
                ),
                defaultReplyTemplate = "Our fresh produce prices: {produce_pricing_info}.",
                sampleSlotValue = "Organic honey is ₹400/jar, seasonal fruits are ₹80/kg",
                sampleSlotValuesByLang = mapOf(
                    "en" to "Roasted Arabica coffee is ₹450/250g, unroasted green parchment is ₹300/kg, wild forest honey is ₹350/jar, maize is ₹50/kg, and organic beans are ₹120/kg.",
                    "hi" to "भुनी अरेबिका कॉफी ₹450/250g, जंगली शहद ₹350/जार, और जैविक बीन्स ₹120/kg"
                ),
                replyTemplatesByLang = mapOf("hi" to "हमारे पहाड़ी फार्म में आपका स्वागत है! फार्म से खरीदारी के लिए दरें: {produce_pricing_info}।")
            ),
            IntentTemplate(
                intent = "visitation_hours",
                slotKey = "opening_hours",
                description = "Opening times, seasonal availability",
                noorPromptTemplates = mapOf(
                    "hi" to "खेत पर्यटकों के लिए कब खुलता और बंद होता है?"
                ),
                defaultReplyTemplate = "The farm is open for visitors from {opening_hours}.",
                sampleSlotValue = "9:00 AM to 6:00 PM Tuesday through Sunday",
                sampleSlotValuesByLang = mapOf(
                    "en" to "8:30 AM to 5:30 PM (Tuesday to Sunday, closed Mondays for harvesting)",
                    "hi" to "सुबह 8:30 से शाम 5:30 बजे (मंगलवार से रविवार, सोमवार कटाई हेतु बंद)"
                ),
                replyTemplatesByLang = mapOf("hi" to "आप {opening_hours} के दौरान हमारे फार्म पर आ सकते हैं, हमें आपको कॉफी बागान दिखाने में खुशी होगी।")
            ),
            IntentTemplate(
                intent = "farm_location_directions",
                slotKey = "location_directions",
                description = "Directions, public transport, parking",
                noorPromptTemplates = mapOf(
                    "hi" to "खेत का पता और यहाँ पहुँचने का रास्ता क्या है?"
                ),
                defaultReplyTemplate = "The farm is located at {location_directions}. Free parking is available on site.",
                sampleSlotValue = "5 km off Highway 48, near Lotus Village",
                sampleSlotValuesByLang = mapOf(
                    "en" to "Ondera Highlands, 12 km from district town along Valley Ridge Road; parking is free on site",
                    "hi" to "ओंडेरा हाइलैंड्स, वैली रिज रोड, जिला शहर से 12 किमी; फार्म पर मुफ्त पार्किंग उपलब्ध है"
                ),
                replyTemplatesByLang = mapOf("hi" to "आप हमें {location_directions} पर पा सकते हैं — मुख्य सड़क छोड़ने के बाद कॉफी के दिशा-संकेतों का पालन करें।")
            ),
            IntentTemplate(
                intent = "activities_available",
                slotKey = "farm_activities",
                description = "Milking cows, fruit picking, tractor rides",
                noorPromptTemplates = mapOf(
                    "hi" to "पर्यटक खेत पर क्या-क्या गतिविधियां कर सकते हैं?"
                ),
                defaultReplyTemplate = "Activities available on the farm include: {farm_activities}.",
                sampleSlotValue = "fruit picking, cow milking, tractor rides, and organic farming workshops",
                sampleSlotValuesByLang = mapOf(
                    "en" to "coffee harvesting walks, pulping mill demonstrations, artisanal cupping sessions, and guided highland nature trails",
                    "hi" to "कॉफी बीन्स चुनना, पल्पिंग मिल देखना, कॉफी टेस्टिंग सत्र और गाइडेड नेचर ट्रेल वॉक"
                ),
                replyTemplatesByLang = mapOf("hi" to "हमारे फार्म में आपका स्वागत है! यहाँ आप {farm_activities} का आनंद ले सकते हैं।")
            ),
            IntentTemplate(
                intent = "amenities_food",
                slotKey = "amenities_food_info",
                description = "Farm lunch, tea, restrooms, drinking water",
                noorPromptTemplates = mapOf(
                    "hi" to "खेत में भोजन, चाय, शौचालय और विश्राम की क्या सुविधाएं हैं?"
                ),
                defaultReplyTemplate = "Farm amenities: {amenities_food_info}.",
                sampleSlotValue = "clean restrooms, safe drinking water, and authentic vegetarian lunch",
                sampleSlotValuesByLang = mapOf(
                    "en" to "freshly brewed single-origin coffee, traditional highland bean lunch, clean western-style restrooms, and spring drinking water",
                    "hi" to "ताज़ी बनी कॉफी, पारंपरिक पहाड़ी दोपहर का भोजन, स्वच्छ शौचालय और शुद्ध प्राकृतिक पेयजल"
                ),
                replyTemplatesByLang = mapOf("hi" to "स्वागत है! फार्म पर आप सुंदर नजारों के साथ {amenities_food_info} का आनंद ले सकते हैं।")
            ),
            IntentTemplate(
                intent = "pet_policy",
                slotKey = "pet_policy_rules",
                description = "Rules regarding dogs and domestic pets",
                noorPromptTemplates = mapOf(
                    "hi" to "खेत में पालतू जानवरों (जैसे कुत्तों) के लिए क्या नियम हैं?"
                ),
                defaultReplyTemplate = "Our pet policy: {pet_policy_rules}.",
                sampleSlotValue = "friendly dogs on leashes are warmly welcome",
                sampleSlotValuesByLang = mapOf(
                    "en" to "well-behaved dogs on leashes are welcome on the outdoor trails; pets are restricted from the coffee drying tables",
                    "hi" to "पट्टे से बंधे शांत कुत्तों का बाहरी रास्तों पर स्वागत है; कॉफी सुखाने वाली मेजों के पास पालतू जानवर प्रतिबंधित हैं"
                ),
                replyTemplatesByLang = mapOf("hi" to "हमारी पालतू पशु नीति स्पष्ट है: {pet_policy_rules}।")
            ),
            IntentTemplate(
                intent = "booking_reservation",
                slotKey = "booking_requirements",
                description = "Advance booking vs walk-in entry",
                noorPromptTemplates = mapOf(
                    "hi" to "क्या खेत आने के लिए पहले से बुकिंग जरूरी है या सीधे आ सकते हैं?"
                ),
                defaultReplyTemplate = "Booking policy: {booking_requirements}.",
                sampleSlotValue = "advance booking is recommended on weekends, walk-ins welcome on weekdays",
                sampleSlotValuesByLang = mapOf(
                    "en" to "advance notice of at least 24 hours is required for guided tours and farm lunch; walk-ins may self-walk outer trails",
                    "hi" to "गाइडेड टूर और फार्म लंच के लिए कम से कम 24 घंटे पहले सूचना आवश्यक है; बिना बुकिंग बाहरी रास्तों पर घूम सकते हैं"
                ),
                replyTemplatesByLang = mapOf("hi" to "नमस्ते! हमारे गाइडेड टूर और फार्म लंच के लिए, {booking_requirements}।")
            ),
            IntentTemplate(
                intent = "tour_duration_difficulty",
                slotKey = "tour_duration_difficulty_info",
                description = "Tour duration, walking trail difficulty, and slope terrain",
                noorPromptTemplates = mapOf(
                    "hi" to "खेत भ्रमण (टूर) में कितना समय लगता है और चढ़ाई का रास्ता कैसा है?"
                ),
                defaultReplyTemplate = "Our guided farm tour takes about {tour_duration_difficulty_info}.",
                sampleSlotValue = "1.5 to 2 hours of gentle walking along highland coffee trails; comfortable walking shoes recommended",
                sampleSlotValuesByLang = mapOf(
                    "en" to "1.5 to 2 hours along gentle highland coffee trails; comfortable walking shoes and light rain jackets are recommended",
                    "hi" to "कॉफी बागानों में 1.5 से 2 घंटे की आसान वॉक; आरामदायक जूते पहनने की सलाह दी जाती है"
                ),
                replyTemplatesByLang = mapOf("hi" to "हमारे कॉफी ट्रेल टूर में लगभग {tour_duration_difficulty_info} का समय लगता है।")
            ),
            IntentTemplate(
                intent = "out_of_scope",
                slotKey = "none",
                description = "General small talk, weather, or out of scope queries",
                noorPromptTemplates = mapOf(
                    "hi" to "यह प्रश्न सीधा अनुवाद के जरिए नूर तक पहुँचाया जाएगा।"
                ),
                defaultReplyTemplate = "Let me connect you directly with Noor for your question.",
                sampleSlotValue = "",
                sampleSlotValuesByLang = mapOf("en" to "", "hi" to ""),
                replyTemplatesByLang = mapOf("hi" to "मुझे खेद है, मेरे पास यह जानकारी नहीं है, लेकिन मैं फार्म टूर या कॉफी के बारे में आपकी मदद कर सकती हूँ।")
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
