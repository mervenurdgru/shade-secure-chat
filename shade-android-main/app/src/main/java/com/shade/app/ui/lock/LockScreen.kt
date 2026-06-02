package com.shade.app.ui.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shade.app.ui.theme.*

@Composable
fun LockScreen(
    pinError: Boolean = false,
    onPinComplete: (String) -> Unit,
    onBiometricRequest: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    // Auto-submit when 4 digits entered
    LaunchedEffect(pin) {
        if (pin.length == 4) {
            onPinComplete(pin)
            pin = ""   // reset so the user can retry if wrong
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RichBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text("🔒", fontSize = 48.sp)
            Text(
                "Shade Kilitli",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "PIN'inizi girin",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(8.dp))

            // PIN dot indicators
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                for (i in 0..3) {
                    val filled = i < pin.length
                    val color by animateColorAsState(
                        targetValue = if (pinError) Color(0xFFFF5252)
                        else if (filled) AccentPurple
                        else SurfaceElevated,
                        label = "pinDot$i"
                    )
                    Surface(
                        shape = CircleShape,
                        color = color,
                        modifier = Modifier.size(18.dp)
                    ) {}
                }
            }

            if (pinError) {
                Text(
                    "Yanlış PIN, tekrar deneyin",
                    color = Color(0xFFFF5252),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Numeric keypad
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫")
            )
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Box(modifier = Modifier.size(76.dp))
                            } else {
                                Surface(
                                    onClick = {
                                        when {
                                            key == "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            pin.length < 4 -> pin += key
                                        }
                                    },
                                    shape = CircleShape,
                                    color = SurfaceElevated,
                                    modifier = Modifier.size(76.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            key,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Biometric shortcut
            TextButton(
                onClick = onBiometricRequest,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Parmak İzi ile Aç", color = AccentPurple)
            }
        }
    }
}
