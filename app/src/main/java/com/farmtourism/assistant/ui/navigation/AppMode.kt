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

enum class TouristTab(val title: String, val subtitle: String) {
    CHAT("💬 Ask Noor", "Inquiry Assistant"),
    REVIEWS("⭐ Leave Review", "Visitor Feedback")
}

enum class FarmerTab(val title: String, val hindiTitle: String) {
    INBOX("💬 Inquiries", "पर्यटक संदेश"),
    REVIEW_INSIGHTS("⭐ Review Insights", "समीक्षा एवं सुझाव")
}

