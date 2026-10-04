package com.farmtourism.assistant.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farmtourism.assistant.backend.pipeline.Tier2PromptRequest
import com.farmtourism.assistant.ui.FarmAssistantUiState
import com.farmtourism.assistant.ui.FarmAssistantViewModel
import com.farmtourism.assistant.ui.components.FarmChatBubble
import com.farmtourism.assistant.ui.navigation.AppMode
import com.farmtourism.assistant.ui.theme.Tier1Green
import com.farmtourism.assistant.ui.theme.Tier2Amber

@Composable
fun NoorChatView(
    state: FarmAssistantUiState,
    viewModel: FarmAssistantViewModel,
    modifier: Modifier = Modifier
) {
    var replyText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.messages.size, state.pendingTier2Request, state.pendingTier3FarmerPrompt) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Chat Message List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.messages.isEmpty() && state.pendingTier2Request == null && state.pendingTier3FarmerPrompt == null) {
                NoorEmptyState(
                    farmerName = state.farmerProfile.name
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items = state.messages, key = { it.id }) { message ->
                        FarmChatBubble(
                            message = message,
                            currentMode = AppMode.FARMER_NOOR
                        )
                    }

                    // Active Tier 2 Inline Card in Noor's Chat
                    state.pendingTier2Request?.let { request ->
                        item {
                            NoorInlineTier2Card(
                                request = request,
                                isSubmitting = state.isNoorSubmitting,
                                onSubmit = { value -> viewModel.answerTier2(value) },
                                onDismiss = { viewModel.dismissPendingTier2() }
                            )
                        }
                    }

                    // If Tier 3 pending, show hint
                    state.pendingTier3FarmerPrompt?.let { fallback ->
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Tier2Amber.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint = Tier2Amber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "नूर, पर्यटक आपके उत्तर की प्रतीक्षा कर रहे हैं। नीचे बॉक्स में अपना जवाब लिखें:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Tier2Amber,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Reply Chips for Noor
        NoorQuickRepliesRow(
            onSelect = { replyText = it }
        )

        // Bottom Input Bar for Noor
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = {
                        Text("संदेश लिखें (हिंदी में)...", fontSize = 14.sp)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (replyText.isNotBlank() && !state.isNoorSubmitting) {
                            val text = replyText
                            replyText = ""
                            focusManager.clearFocus()
                            viewModel.answerTier3(text)
                        }
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = {
                        if (replyText.isNotBlank() && !state.isNoorSubmitting) {
                            val text = replyText
                            replyText = ""
                            focusManager.clearFocus()
                            viewModel.answerTier3(text)
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ) {
                    if (state.isNoorSubmitting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoorInlineTier2Card(
    request: Tier2PromptRequest,
    isSubmitting: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var valueInput by remember(request) {
        val defaultText = request.defaultReplyForNoor.ifBlank {
            request.template.getSampleSlotValue("hi")
        }
        mutableStateOf(defaultText)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Tier2Amber.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "नूर के लिए सवाल (Fill in the blank):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Tier2Amber
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = request.promptForNoor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Suggestions in Hindi for Noor
            val suggestions = when (request.slotKey) {
                "tour_price_inr" -> listOf("₹500 प्रति व्यक्ति", "₹750 प्रति व्यक्ति", "₹1000 प्रति व्यक्ति")
                "opening_hours" -> listOf("सुबह 9:00 से शाम 6:00 बजे", "सुबह 8:30 से शाम 5:30 बजे (सोमवार बंद)")
                "pet_policy_rules" -> listOf("पट्टे पर बंधे कुत्तों का स्वागत है", "पालतू जानवरों की अनुमति नहीं है")
                "booking_requirements" -> listOf("24 घंटे पहले अग्रिम बुकिंग आवश्यक", "बिना बुकिंग सीधे आ सकते हैं")
                "farm_activities" -> listOf("कॉफी बीन चुनना और टेस्टिंग", "फार्म वॉक और नेचर ट्रेल")
                "amenities_food_info" -> listOf("ताज़ी कॉफी, भोजन और शौचालय", "पीने का पानी और विश्राम शेड")
                "produce_pricing_info" -> listOf("भुनी कॉफी ₹450, शहद ₹350", "ऑर्गेनिक कॉफी ₹500/पैकेट")
                "location_directions" -> listOf("हाईवे से 5 किमी, लोटस विलेज के पास", "वैली रिज रोड, जिला शहर से 12 किमी")
                "tour_duration_difficulty_info" -> listOf("1.5 से 2 घंटे की आसान वॉक", "1 घंटा, मध्यम चढ़ाई")
                else -> emptyList()
            }
            if (suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { s ->
                        SuggestionChip(
                            onClick = { valueInput = s },
                            label = { Text(s, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = valueInput,
                    onValueChange = { valueInput = it },
                    placeholder = { Text("नूर का उत्तर (हिंदी में)...") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (valueInput.isNotBlank() && !isSubmitting) {
                            onSubmit(valueInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Tier2Amber),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("भेजें (Send)", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoorQuickRepliesRow(
    onSelect: (String) -> Unit
) {
    val quickReplies = listOf(
        "हाँ, आप कल आ सकते हैं।",
        "खेत सुबह 9 बजे खुलता है।",
        "हाँ, हमारे पास ताज़ा जैविक शहद उपलब्ध है।",
        "हाँ, दोपहर का शाकाहारी भोजन मिलेगा।"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        quickReplies.forEach { qr ->
            SuggestionChip(
                onClick = { onSelect(qr) },
                label = { Text(qr, fontSize = 11.sp) },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun NoorEmptyState(
    farmerName: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MarkEmailRead,
            contentDescription = null,
            tint = Tier1Green,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "नमस्ते $farmerName! सभी पर्यटकों को उत्तर दिया जा चुका है।",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "जब कोई विदेशी पर्यटक सवाल पूछेगा, तो उसका सवाल हिंदी में यहाँ दिखाई देगा।",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
