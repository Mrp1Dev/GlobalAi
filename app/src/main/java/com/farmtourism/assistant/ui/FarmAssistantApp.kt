package com.farmtourism.assistant.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farmtourism.assistant.ui.components.FarmInfoBottomSheet
import com.farmtourism.assistant.ui.navigation.AppMode
import com.farmtourism.assistant.ui.screens.NoorChatView
import com.farmtourism.assistant.ui.screens.TouristChatView
import com.farmtourism.assistant.ui.theme.FarmTourismTheme
import com.farmtourism.assistant.ui.theme.Tier1Green
import com.farmtourism.assistant.ui.theme.Tier2Amber

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.farmtourism.assistant.ui.navigation.FarmerTab
import com.farmtourism.assistant.ui.navigation.TouristTab
import com.farmtourism.assistant.ui.screens.NoorReviewDashboardView
import com.farmtourism.assistant.ui.screens.TouristReviewView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmAssistantApp(
    viewModel: FarmAssistantViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    FarmTourismTheme(darkTheme = state.isDarkTheme) {
        Scaffold(
            topBar = {
                Surface(
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp
                ) {
                    Column {
                        // Clean Top Header
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            title = {
                                Column {
                                    Text(
                                        text = if (state.currentMode == AppMode.TOURIST) {
                                            state.farmerProfile.farmName
                                        } else {
                                            "नमस्ते ${state.farmerProfile.name}! (Noor's Portal)"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (state.currentMode == AppMode.TOURIST) {
                                            "Host: Noor (Hindi/हिन्दी) • On-Device AI"
                                        } else {
                                            "पर्यटक चैट एवं समीक्षा • ऑन-डिवाइस अनुवाद"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            actions = {
                                // Settings Sheet Button (Farm DB slots)
                                IconButton(onClick = { viewModel.toggleSettingsSheet(true) }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Farm Database Settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Dark / Light Mode Toggle
                                IconButton(onClick = { viewModel.toggleDarkTheme() }) {
                                    Icon(
                                        imageVector = if (state.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = "Toggle theme",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Clear Chat
                                IconButton(onClick = { viewModel.clearChat() }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Clear Chat",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )

                        // Role Switcher: Tourist vs Noor
                        RoleSwitchBar(
                            currentMode = state.currentMode,
                            pendingCount = state.pendingInquiriesCount,
                            onSelectMode = { viewModel.switchMode(it) }
                        )

                        // Mode-Specific Sub-Tabs: Chat vs Reviews
                        ModeSubTabBar(
                            currentMode = state.currentMode,
                            currentTouristTab = state.currentTouristTab,
                            currentFarmerTab = state.currentFarmerTab,
                            reviewsCount = state.submittedReviews.size,
                            onSelectTouristTab = { viewModel.switchTouristTab(it) },
                            onSelectFarmerTab = { viewModel.switchFarmerTab(it) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val activeTab = if (state.currentMode == AppMode.TOURIST) {
                    state.currentTouristTab.name
                } else {
                    state.currentFarmerTab.name
                }

                AnimatedContent(
                    targetState = "${state.currentMode}_$activeTab",
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ScreenTransition"
                ) {
                    when (state.currentMode) {
                        AppMode.TOURIST -> {
                            when (state.currentTouristTab) {
                                TouristTab.CHAT -> TouristChatView(
                                    state = state,
                                    viewModel = viewModel
                                )
                                TouristTab.REVIEWS -> TouristReviewView(
                                    state = state,
                                    viewModel = viewModel
                                )
                            }
                        }
                        AppMode.FARMER_NOOR -> {
                            when (state.currentFarmerTab) {
                                FarmerTab.INBOX -> NoorChatView(
                                    state = state,
                                    viewModel = viewModel
                                )
                                FarmerTab.REVIEW_INSIGHTS -> NoorReviewDashboardView(
                                    state = state,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings Bottom Sheet for Noor's SQLite DB
        if (state.showSettingsSheet) {
            FarmInfoBottomSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = { viewModel.toggleSettingsSheet(false) }
            )
        }
    }
}

@Composable
private fun RoleSwitchBar(
    currentMode: AppMode,
    pendingCount: Int,
    onSelectMode: (AppMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tourist Mode Button
        FilterChip(
            selected = currentMode == AppMode.TOURIST,
            onClick = { onSelectMode(AppMode.TOURIST) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text(
                    text = "🎒 Tourist Mode",
                    fontWeight = if (currentMode == AppMode.TOURIST) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                )
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        // Noor Mode Button
        FilterChip(
            selected = currentMode == AppMode.FARMER_NOOR,
            onClick = { onSelectMode(AppMode.FARMER_NOOR) },
            leadingIcon = {
                if (pendingCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = Tier2Amber) {
                                Text("$pendingCount", color = Color.White, fontSize = 9.sp)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Spa,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            },
            label = {
                Text(
                    text = if (pendingCount > 0) "🌾 Noor (Host) • $pendingCount" else "🌾 Noor (Host / किसान)",
                    fontWeight = if (currentMode == AppMode.FARMER_NOOR) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                )
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
    }
}

@Composable
private fun ModeSubTabBar(
    currentMode: AppMode,
    currentTouristTab: TouristTab,
    currentFarmerTab: FarmerTab,
    reviewsCount: Int,
    onSelectTouristTab: (TouristTab) -> Unit,
    onSelectFarmerTab: (FarmerTab) -> Unit
) {
    if (currentMode == AppMode.TOURIST) {
        val tabs = listOf(TouristTab.CHAT, TouristTab.REVIEWS)
        val selectedIndex = tabs.indexOf(currentTouristTab).coerceAtLeast(0)

        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (selectedIndex in tabPositions.indices) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == currentTouristTab
                Tab(
                    selected = isSelected,
                    onClick = { onSelectTouristTab(tab) },
                    text = {
                        Text(
                            text = tab.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }
    } else {
        val tabs = listOf(FarmerTab.INBOX, FarmerTab.REVIEW_INSIGHTS)
        val selectedIndex = tabs.indexOf(currentFarmerTab).coerceAtLeast(0)

        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.secondary,
            indicator = { tabPositions ->
                if (selectedIndex in tabPositions.indices) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == currentFarmerTab
                Tab(
                    selected = isSelected,
                    onClick = { onSelectFarmerTab(tab) },
                    text = {
                        Text(
                            text = if (tab == FarmerTab.REVIEW_INSIGHTS && reviewsCount > 0) {
                                "${tab.title} ($reviewsCount)"
                            } else {
                                "${tab.title} (${tab.hindiTitle})"
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                )
            }
        }
    }
}

