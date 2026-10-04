package com.farmtourism.assistant.ui.navigation

enum class AppMode(
    val title: String,
    val subtitle: String,
    val hindiTitle: String
) {
    TOURIST(
        title = "Tourist Mode",
        subtitle = "Ask questions in foreign languages",
        hindiTitle = "पर्यटक पोर्टल"
    ),
    FARMER_NOOR(
        title = "Noor's Mode (Host)",
        subtitle = "Farmer dashboard & Hindi replies",
        hindiTitle = "नूर का किसान पोर्टल"
    )
}

enum class TouristTab(val title: String) {
    CHAT("Ask Noor"),
    EXPLORE("Farm Guide & Highlights")
}

enum class FarmerTab(val title: String, val hindiTitle: String) {
    INBOX("Inbox", "संदेश"),
    KNOWLEDGE_BASE("Farm DB", "खेत डेटाबेस"),
    EDGE_SETTINGS("Offline ML", "ऑफ़लाइन मॉडल")
}
