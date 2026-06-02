package com.shade.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shade.app.ui.theme.*

private val BACKGROUND_OPTIONS = listOf(
    null to "Varsayılan",
    0xFF1A1A2E.toInt() to "Gece Mavisi",
    0xFF0D1B2A.toInt() to "Derin Lacivert",
    0xFF1B1B1B.toInt() to "Siyah",
    0xFF1A2A1A.toInt() to "Orman Yeşili",
    0xFF2A1A1A.toInt() to "Bordo",
    0xFF1A1A3A.toInt() to "Mor Gece",
    0xFF2A2A1A.toInt() to "Çöl Altını",
    0xFF0A0A0A.toInt() to "Jet Siyahı"
)


private val LANGUAGES = listOf(
    "🇬🇧 İngilizce" to "en",
    "🇩🇪 Almanca" to "de",
    "🇫🇷 Fransızca" to "fr",
    "🇪🇸 İspanyolca" to "es",
    "🇸🇦 Arapça" to "ar",
    "🇷🇺 Rusça" to "ru",
    "🇨🇳 Çince" to "zh",
    "🇯🇵 Japonca" to "ja",
    "🇮🇹 İtalyanca" to "it",
    "🇧🇷 Portekizce" to "pt",
    "🇹🇷 Türkçe" to "tr"
)

/** Arkaplan rengi seçici dialog. */
@Composable
fun BackgroundPickerDialog(
    currentColor: Int?,
    onColorSelected: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("Arkaplan Rengi", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BACKGROUND_OPTIONS.forEach { (argb, label) ->
                    TextButton(
                        onClick = { onColorSelected(argb); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(if (argb == null) RichBlack else Color(argb), CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = if (currentColor == argb) AccentPurple else TextPrimary)
                            if (currentColor == argb) {
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.Check, null, tint = AccentPurple, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = TextMuted) }
        }
    )
}


/** Çeviri dili seçici dialog. */
@Composable
fun LanguagePickerDialog(
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dil Seçin", color = TextPrimary) },
        text = {
            LazyColumn {
                items(LANGUAGES) { (label, code) ->
                    TextButton(
                        onClick = { onLanguageSelected(code); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, modifier = Modifier.fillMaxWidth(), fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}
