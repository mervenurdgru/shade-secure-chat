package com.shade.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.ui.theme.*
import kotlinx.coroutines.delay

// Sola kaydırma eşiği (px)
private const val CANCEL_THRESHOLD = -200f

/**
 * Sohbet ekranının alt giriş çubuğu.
 */
@Composable
fun MessageInput(
    messageText: String,
    replyingTo: MessageEntity?,
    editingMessage: MessageEntity?,
    isRecording: Boolean,
    onMessageTextChange: (String) -> Unit,
    onSendOrConfirm: () -> Unit,
    onCancelReply: () -> Unit,
    onCancelEditing: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onRecordCancel: () -> Unit = {},
) {
    // Kayıt sırasında sürükleme miktarı — MicButton tarafından güncellenir
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val isCancelling = dragOffsetX < CANCEL_THRESHOLD / 2

    // Kayıt bittiğinde sıfırla
    LaunchedEffect(isRecording) {
        if (!isRecording) dragOffsetX = 0f
    }

    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (replyingTo != null) {
                ReplyIndicator(replyingTo = replyingTo, onCancel = onCancelReply)
            }
            if (editingMessage != null) {
                EditIndicator(onCancel = onCancelEditing)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // ── Sol: attachment butonları (sadece normal modda) ──────────
                if (!isRecording && editingMessage == null) {
                    IconButton(onClick = onPickImage) {
                        Icon(Icons.Default.Image, "Fotoğraf", tint = AccentPurple, modifier = Modifier.size(26.dp))
                    }
                    IconButton(onClick = onPickFile) {
                        Icon(Icons.Default.AttachFile, "Dosya", tint = AccentPurple, modifier = Modifier.size(26.dp))
                    }
                }

                // ── Orta: kayıt göstergesi VEYA metin alanı ─────────────────
                if (isRecording) {
                    RecordingStatus(
                        isCancelling = isCancelling,
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                    )
                } else {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = onMessageTextChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                if (editingMessage != null) "Düzenle..." else "Mesaj yaz...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = AccentPurple.copy(0.5f),
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = AccentPurple,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = { if (messageText.isNotBlank()) onSendOrConfirm() }
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                }

                // ── Sağ: gönder VEYA mikrofon ───────────────────────────────
                // ÖNEMLİ: MicButton her zaman bu aynı slotta → gesture kesilmez!
                // Sadece metin yazıldığında send butonu gösterilir.
                val showSend = messageText.isNotBlank() && !isRecording

                if (showSend) {
                    SendButton(
                        sendEnabled = true,
                        isEditing = editingMessage != null,
                        onClick = { onSendOrConfirm() }
                    )
                } else {
                    MicButton(
                        isRecording = isRecording,
                        isCancelling = isCancelling,
                        onRecordStart = onRecordStart,
                        onRecordStop = onRecordStop,
                        onRecordCancel = onRecordCancel,
                        onDragUpdate = { total -> dragOffsetX = total }
                    )
                }
            }
        }
    }
}

// ── Kayıt göstergesi (süre + iptal yönlendirmesi) ─────────────────────────────

@Composable
private fun RecordingStatus(
    isCancelling: Boolean,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        elapsedSeconds = 0
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeLabel = "%02d:%02d".format(minutes, seconds)

    if (isCancelling) {
        Text(
            text = "< İptal et",
            color = Color(0xFFFF5252),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier.padding(start = 8.dp)
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.padding(start = 8.dp)
        ) {
            Text(
                text = "⏺ $timeLabel",
                color = Color(0xFFFF5252),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "◁◁  iptal için kaydır",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

// ── Mikrofon butonu (sadece görsel + gesture) ─────────────────────────────────

@Composable
private fun MicButton(
    isRecording: Boolean,
    isCancelling: Boolean,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onRecordCancel: () -> Unit,
    onDragUpdate: (totalDragX: Float) -> Unit,
) {
    val bgColor = when {
        isCancelling && isRecording -> Color(0xFFFF5252).copy(alpha = 0.6f)
        isRecording                 -> Color(0xFFFF5252)
        else                        -> AccentPurple
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .background(color = bgColor, shape = CircleShape)
            .pointerInput(Unit) {
                // awaitEachGesture: her dokunuş için TEK seferlik çalışır.
                // parmak kalkmadan yeni gesture başlamaz → çoklu stop önlenir.
                awaitEachGesture {
                    awaitFirstDown()        // parmak basışını bekle
                    onRecordStart()

                    var totalDrag = 0f
                    var cancelled = false

                    loop@ while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break@loop

                        if (change.pressed) {
                            totalDrag += change.positionChange().x
                            change.consume()
                            onDragUpdate(totalDrag)   // parent'i güncelle (gösterge için)

                            if (totalDrag < CANCEL_THRESHOLD) {
                                cancelled = true
                                // Parmak kalkana kadar bekle
                                while (true) {
                                    val e2 = awaitPointerEvent()
                                    e2.changes.forEach { it.consume() }
                                    if (e2.changes.all { !it.pressed }) break
                                }
                                break@loop
                            }
                        } else {
                            break@loop   // parmak kalktı → normal bırakma
                        }
                    }

                    onDragUpdate(0f)   // sürüklemeyi sıfırla
                    if (cancelled) onRecordCancel() else onRecordStop()
                }
            }
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = "Ses Kaydı",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Gönder butonu ─────────────────────────────────────────────────────────────

@Composable
private fun SendButton(
    sendEnabled: Boolean,
    isEditing: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = if (sendEnabled) AccentPurple else SurfaceContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isEditing) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                contentDescription = "Gönder",
                tint = if (sendEnabled) Color.White else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Yanıt göstergesi ──────────────────────────────────────────────────────────

@Composable
private fun ReplyIndicator(replyingTo: MessageEntity, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentPurple.copy(0.10f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Reply, null, tint = AccentPurple, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Yanıtlanıyor", color = AccentPurple, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            Text(
                replyingTo.content.take(60),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, "İptal", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Düzenleme göstergesi ──────────────────────────────────────────────────────

@Composable
private fun EditIndicator(onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentPurple.copy(0.12f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Edit, null, tint = AccentPurple, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Mesajı düzenle",
            color = AccentPurple,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, "İptal", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
