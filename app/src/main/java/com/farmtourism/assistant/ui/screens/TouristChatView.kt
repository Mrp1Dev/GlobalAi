package com.farmtourism.assistant.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farmtourism.assistant.backend.model.Languages
import com.farmtourism.assistant.ui.FarmAssistantUiState
import com.farmtourism.assistant.ui.FarmAssistantViewModel
import com.farmtourism.assistant.ui.components.FarmChatBubble
import com.farmtourism.assistant.ui.navigation.AppMode
import com.farmtourism.assistant.ui.theme.Tier1Green
import com.farmtourism.assistant.ui.theme.Tier2Amber

@Composable
fun TouristChatView(
    state: FarmAssistantUiState,
    viewModel: FarmAssistantViewModel,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.messages.size, state.touristWaitingForReply) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Subtle Language Bar
        TouristLanguageBar(
            selectedLang = state.selectedTouristLanguage,
            onSelectLang = { viewModel.setTouristLanguage(it) }
        )

        // Chat Message List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.messages.isEmpty()) {
                TouristWelcomeView(
                    farmName = state.farmerProfile.farmName,
                    farmerName = state.farmerProfile.name,
                    onSelectPrompt = { prompt ->
                        inputText = prompt
                        viewModel.sendTouristMessage(prompt)
                    }
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
                            currentMode = AppMode.TOURIST
                        )
                    }

                    // Waiting for Noor's reply indicator
                    if (state.touristWaitingForReply) {
                        item {
                            WaitingForNoorBubble()
                        }
                    }
                }
            }
        }

        // Quick Suggestion Chips (when chat is started)
        if (state.messages.isNotEmpty() && !state.touristWaitingForReply) {
            QuickSamplePromptsRow(
                onSelectPrompt = { prompt ->
                    inputText = prompt
                    viewModel.sendTouristMessage(prompt)
                }
            )
        }

        // Bottom Input Composer
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
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = if (state.selectedTouristLanguage == "auto") {
                                "Ask in any language..."
                            } else {
                                "Ask in ${Languages.getDisplayName(state.selectedTouristLanguage)}..."
                            },
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() && !state.isProcessing) {
                            val text = inputText
                            inputText = ""
                            focusManager.clearFocus()
                            viewModel.sendTouristMessage(text)
                        }
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank() && !state.isProcessing) {
                            val text = inputText
                            inputText = ""
                            focusManager.clearFocus()
                            viewModel.sendTouristMessage(text)
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
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
private fun WaitingForNoorBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Noor is writing a reply...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TouristLanguageBar(
    selectedLang: String,
    onSelectLang: (String) -> Unit
) {
    val languages = listOf(
        "auto" to "✨ Auto",
        "es" to "🇪🇸 Spanish",
        "fr" to "🇫🇷 French",
        "de" to "🇩🇪 German",
        "it" to "🇮🇹 Italian",
        "en" to "🇬🇧 English"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Language:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            languages.forEach { (code, label) ->
                FilterChip(
                    selected = selectedLang == code,
                    onClick = { onSelectLang(code) },
                    label = { Text(label, fontSize = 11.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun QuickSamplePromptsRow(
    onSelectPrompt: (String) -> Unit
) {
    val samples = listOf(
        "¿Cuánto cuesta el tour?",
        "Quelle est l'adresse?",
        "Habt ihr frische Äpfel?",
        "Do you have clean restrooms?"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        samples.forEach { sample ->
            SuggestionChip(
                onClick = { onSelectPrompt(sample) },
                label = { Text(sample, fontSize = 11.sp) },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun TouristWelcomeView(
    farmName: String,
    farmerName: String,
    onSelectPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Spa, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Welcome to $farmName",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Ask any questions in your native language. Host farmer $farmerName will answer you back in your language.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Try asking:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(8.dp))

        val prompts = listOf(
            "🇪🇸 ¿Cuánto cuesta una visita guiada?",
            "🇫🇷 Quelle est l'adresse de la ferme?",
            "🇩🇪 Kann man Kühe melken?",
            "🇬🇧 What time do you open?"
        )

        prompts.forEach { p ->
            val clean = p.substring(3).trim()
            OutlinedButton(
                onClick = { onSelectPrompt(clean) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Text(p, fontSize = 13.sp)
            }
        }
    }
}
