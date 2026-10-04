package com.farmtourism.assistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farmtourism.assistant.backend.model.MessageSender
import com.farmtourism.assistant.ui.model.UiChatMessage
import com.farmtourism.assistant.ui.navigation.AppMode
import com.farmtourism.assistant.ui.theme.Tier1Green
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FarmChatBubble(
    message: UiChatMessage,
    currentMode: AppMode,
    modifier: Modifier = Modifier
) {
    val isTouristView = currentMode == AppMode.TOURIST
    // In Tourist view, tourist's messages are on the right (sent), Noor's are on the left (received).
    // In Noor's view, Noor's messages are on the right (sent), tourist's are on the left (received).
    val isMyMessage = if (isTouristView) {
        message.sender == MessageSender.TOURIST
    } else {
        message.sender == MessageSender.FARMER
    }

    val displayText = if (isTouristView) message.touristText else message.noorText
    val timeFormatted = formatTime(message.timestamp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMyMessage) 16.dp else 4.dp,
                bottomEnd = if (isMyMessage) 4.dp else 16.dp
            ),
            color = if (isMyMessage) {
                if (isTouristView) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Sender label
                if (!isMyMessage) {
                    Text(
                        text = if (isTouristView) "Noor (Host Farmer)" else "विदेशी पर्यटक (Tourist)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isTouristView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Main Message Content
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Footer: Timestamp & Auto-reply badge
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isAutoReply) {
                        Text(
                            text = "⚡ Instant reply",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Tier1Green,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }

                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}
