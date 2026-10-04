package com.farmtourism.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farmtourism.assistant.backend.review.model.Recommendation
import com.farmtourism.assistant.backend.review.model.ReviewAspect
import com.farmtourism.assistant.backend.review.model.ReviewClassification
import com.farmtourism.assistant.backend.review.model.Sentiment
import com.farmtourism.assistant.backend.review.model.Severity
import com.farmtourism.assistant.backend.review.model.SubmittedReview
import com.farmtourism.assistant.backend.review.rules.RecommendationRuleEngine
import com.farmtourism.assistant.ui.FarmAssistantUiState
import com.farmtourism.assistant.ui.FarmAssistantViewModel
import com.farmtourism.assistant.ui.theme.Tier1Green
import com.farmtourism.assistant.ui.theme.Tier2Amber

@Composable
fun NoorReviewDashboardView(
    state: FarmAssistantUiState,
    viewModel: FarmAssistantViewModel
) {
    val scrollState = rememberScrollState()
    val selectedReview = state.selectedReview

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RateReview,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "पर्यटक समीक्षा एवं सुझाव (Review Insights)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "विदेशी पर्यटकों की समीक्षाओं का हिंदी अनुवाद और फार्म सुधार सुझाव।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Horizontal Review Selector Chips / Stream
        Column {
            Text(
                text = "प्राप्त समीक्षाएं (${state.submittedReviews.size}) • Select Review:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.submittedReviews.forEachIndexed { index, review ->
                    val isSelected = review.id == selectedReview?.id
                    val sentiment = review.analysisResult?.classification?.overallSentiment ?: Sentiment.NEUTRAL
                    val severity = review.analysisResult?.classification?.reviewSeverity ?: Severity.LOW

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectReview(review.id) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val dotColor = when {
                                    severity == Severity.HIGH -> Color(0xFFDC2626)
                                    sentiment == Sentiment.POSITIVE -> Tier1Green
                                    sentiment == Sentiment.NEGATIVE -> Color(0xFFDC2626)
                                    else -> Tier2Amber
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = review.author,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        }

        if (selectedReview == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("कोई समीक्षा उपलब्ध नहीं है (No reviews submitted yet).")
                }
            }
        } else {
            val analysis = selectedReview.analysisResult

            // 1. Original Review & On-Device Hindi Translation Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Language Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "मूल भाषा: ${analysis?.sourceLanguageName ?: "विदेशी भाषा"} (${analysis?.sourceLanguageCode?.uppercase() ?: "EXT"})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Sentiment Badge
                        val sentimentLabel = analysis?.classification?.formattedOverallSentiment ?: "Neutral"
                        val (badgeBg, badgeFg) = when {
                            sentimentLabel.contains("Negative", ignoreCase = true) -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
                            sentimentLabel.contains("Positive", ignoreCase = true) -> Color(0xFFDCFCE7) to Color(0xFF15803D)
                            else -> Color(0xFFFEF3C7) to Color(0xFFB45309)
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = badgeBg
                        ) {
                            Text(
                                text = sentimentLabel,
                                color = badgeFg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Original Text
                    Text(
                        text = "\"${selectedReview.originalText}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Hindi Translation
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🇮🇳 हिंदी अनुवाद (ऑन-डिवाइस Google ML Kit):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (!analysis?.translatedText.isNullOrBlank()) {
                            analysis!!.translatedText
                        } else {
                            selectedReview.originalText
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 2. Aspects / Areas Identified
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "पहचाने गए मुख्य क्षेत्र (Areas Mentioned)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val aspects = analysis?.classification?.aspects ?: emptyList()
                    if (aspects.isEmpty()) {
                        Text(
                            text = "इस समीक्षा में कोई विशिष्ट कमी या प्रशंसा अलग से चिन्हित नहीं हुई।",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            aspects.forEach { aspect ->
                                AspectItemRow(aspect = aspect)
                            }
                        }
                    }
                }
            }

            // 3. Operational Improvement Recommendations
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Tier2Amber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "कार्रवाई योग्य सुझाव (Suggested Actions)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val recommendations = analysis?.recommendations ?: emptyList()
                    if (recommendations.isEmpty()) {
                        Text(
                            text = "इस समीक्षा के आधार पर कोई त्वरित बदलाव की आवश्यकता नहीं है।",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            recommendations.forEach { rec ->
                                val isAccepted = selectedReview.acceptedSuggestions.contains(rec.aspect)
                                RecommendationCard(
                                    rec = rec,
                                    isAccepted = isAccepted,
                                    onToggleAccept = {
                                        viewModel.toggleAcceptSuggestion(selectedReview.id, rec.aspect)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 4. World Bank Human-in-the-Loop Guardrail Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "मानवीय नियंत्रण (Human-in-the-Loop Principle)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = RecommendationRuleEngine.GUARDRAIL_DISCLAIMER_HI,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            // 5. Latency Telemetry
            if (analysis?.timings != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "⏱️ भाषा: ${analysis.timings.detectionMs}ms · अनुवाद: ${analysis.timings.translationMs}ms · वर्गीकरण: ${analysis.timings.classificationMs}ms · कुल: ${analysis.timings.totalMs}ms ($0 Cost)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun AspectItemRow(aspect: ReviewAspect) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = aspect.aspectDisplayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "विश्वसनीयता: ${(aspect.confidence * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Sentiment Tag
                val (sentBg, sentFg) = when (aspect.sentiment) {
                    Sentiment.POSITIVE -> Color(0xFFDCFCE7) to Color(0xFF15803D)
                    Sentiment.NEGATIVE -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
                    Sentiment.NEUTRAL -> Color(0xFFF3F4F6) to Color(0xFF4B5563)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = sentBg) {
                    Text(
                        text = aspect.sentiment.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = sentFg,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Severity Tag if high or medium
                if (aspect.severity != Severity.LOW) {
                    val sevBg = if (aspect.severity == Severity.HIGH) Color(0xFFFEE2E2) else Color(0xFFFEF3C7)
                    val sevFg = if (aspect.severity == Severity.HIGH) Color(0xFFB91C1C) else Color(0xFFB45309)
                    Surface(shape = RoundedCornerShape(8.dp), color = sevBg) {
                        Text(
                            text = "${aspect.severity.displayName} Priority",
                            color = sevFg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    rec: Recommendation,
    isAccepted: Boolean,
    onToggleAccept: () -> Unit
) {
    val borderColor = if (isAccepted) Tier1Green else MaterialTheme.colorScheme.outlineVariant

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        color = if (isAccepted) Tier1Green.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority Tag
                val (priorityBg, priorityFg) = when (rec.priority) {
                    Severity.HIGH -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
                    Severity.MEDIUM -> Color(0xFFFEF3C7) to Color(0xFFB45309)
                    Severity.LOW -> Color(0xFFDCFCE7) to Color(0xFF15803D)
                }

                Surface(shape = RoundedCornerShape(6.dp), color = priorityBg) {
                    Text(
                        text = "${rec.priority.displayName} Priority",
                        color = priorityFg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Accept / Dismiss Toggle Button
                OutlinedButton(
                    onClick = onToggleAccept,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isAccepted) Icons.Default.CheckCircle else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isAccepted) Tier1Green else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAccepted) "स्वीकृत (Accepted)" else "स्वीकारें (Accept)",
                        fontSize = 11.sp,
                        color = if (isAccepted) Tier1Green else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action in Hindi
            if (rec.localizedActionHindi.isNotBlank()) {
                Text(
                    text = rec.localizedActionHindi,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Action in English
            Text(
                text = rec.actionVerb,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
