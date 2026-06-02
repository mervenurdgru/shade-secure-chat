package com.shade.app.ui.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shade.app.R
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.data.local.entities.MessageType
import com.shade.app.ui.theme.*
import com.shade.app.ui.util.UiText
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    isGroupChat: Boolean = false,
    senderName: String? = null,
    senderShadeId: String? = null,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    isDownloadingFile: Boolean = false,
    translatedText: String? = null,
    isTranslating: Boolean = false,
    onImageClick: (String) -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onDownloadAudioClick: () -> Unit = {},
    onDownloadFileClick: () -> Unit = {},
    onTranslateRequest: () -> Unit = {},
    onDeleteForMe: () -> Unit = {},
    onDeleteForEveryone: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null,
) {
    // Sistem mesajları (grup üye ayrıldı/çıkarıldı) — balon gösterme
    if (message.messageType == MessageType.SYSTEM) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Gray.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
        return
    }

    val dateFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = remember(message.timestamp) { dateFormatter.format(Date(message.timestamp)) }

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteForMeDialog by remember { mutableStateOf(false) }
    var showDeleteForEveryoneDialog by remember { mutableStateOf(false) }

    if (showDeleteForMeDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteForMeDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Mesajı Sil", color = TextPrimary) },
            text = { Text("Bu mesaj yalnızca senin için silinecek.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showDeleteForMeDialog = false; onDeleteForMe() }) {
                    Text("Sil", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteForMeDialog = false }) { Text("İptal", color = TextMuted) }
            }
        )
    }

    if (showDeleteForEveryoneDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteForEveryoneDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Herkesten Sil", color = TextPrimary) },
            text = { Text("Bu mesaj her iki taraf için de silinecek. Bu işlem geri alınamaz.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showDeleteForEveryoneDialog = false; onDeleteForEveryone?.invoke() }) {
                    Text("Herkesten Sil", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteForEveryoneDialog = false }) { Text("İptal", color = TextMuted) }
            }
        )
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (isMe) 18.dp else 4.dp,
        bottomEnd = if (isMe) 4.dp else 18.dp
    )
    val bgModifier = if (isMe) {
        Modifier.background(Brush.linearGradient(colors = listOf(BubbleMine, BubbleMineEnd)))
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isMe) 48.dp else 0.dp, end = if (isMe) 0.dp else 48.dp, top = 2.dp, bottom = 2.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            // Grup sohbetinde karşı tarafın adını göster
            if (isGroupChat && !isMe && (senderShadeId != null || senderName != null)) {
                Column(modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)) {
                    // Birinci satır: shadeId (her zaman)
                    Text(
                        text = senderShadeId ?: senderName ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                    // İkinci satır: profileName/savedName (shadeId'den farklıysa)
                    if (senderName != null && senderName != senderShadeId) {
                        Text(
                            text = senderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentPurple.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Box {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    if (onReply != null) {
                        DropdownMenuItem(
                            text = { Text("Yanıtla", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Reply, null, tint = AccentPurple) },
                            onClick = { showMenu = false; onReply() }
                        )
                    }
                    if (onEdit != null) {
                        DropdownMenuItem(
                            text = { Text("Düzenle", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = AccentPurple) },
                            onClick = { showMenu = false; onEdit() }
                        )
                    }
                    if (onDeleteForEveryone != null) {
                        DropdownMenuItem(
                            text = { Text("Herkesten Sil", color = Color(0xFFFF5252)) },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFFF5252)) },
                            onClick = { showMenu = false; showDeleteForEveryoneDialog = true }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Kendimden Sil", color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = TextSecondary) },
                        onClick = { showMenu = false; showDeleteForMeDialog = true }
                    )
                }

                Surface(
                    shape = bubbleShape,
                    color = if (isMe) Color.Transparent else BubbleOther,
                    border = if (!isMe) BorderStroke(0.5.dp, BubbleOtherBorder) else null,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = { showMenu = true }
                        )
                ) {
                    if (message.isDeleted) {
                        Row(
                            modifier = bgModifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Block, null, tint = if (isMe) Color.White.copy(0.5f) else TextMuted, modifier = Modifier.size(14.dp))
                            Text("Bu mesaj silindi", color = if (isMe) Color.White.copy(0.5f) else TextMuted, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                        }
                    } else {
                        Column(modifier = bgModifier) {
                            when (message.messageType) {
                                MessageType.IMAGE  -> ImageContent(message, isMe, isDownloading, downloadProgress, bubbleShape, timeString, onImageClick, onDownloadClick)
                                MessageType.TEXT   -> TextContent(message, isMe, translatedText, isTranslating, timeString)
                                MessageType.AUDIO  -> AudioMessageBubble(message, isMe, isDownloadingFile, onDownloadAudioClick, timeString)
                                MessageType.FILE   -> FileMessageBubble(message, isMe, isDownloadingFile, onDownloadFileClick, timeString)
                                MessageType.SYSTEM -> {} // handled above via early return
                            }
                        }
                    }
                }
            }

            // Çeviri butonu — yalnızca metin mesajları için
            if (message.messageType == MessageType.TEXT && !message.isDeleted) {
                IconButton(onClick = onTranslateRequest, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Language, contentDescription = "Çevir", modifier = Modifier.size(13.dp), tint = TextMuted.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun TextContent(
    message: MessageEntity,
    isMe: Boolean,
    translatedText: String?,
    isTranslating: Boolean,
    timeString: String,
) {
    Column {
        // Yanıt önizlemesi
        if (!message.replyToContent.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                color = if (isMe) Color.White.copy(0.12f) else SurfaceContainer.copy(0.7f),
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp)
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(3.dp).height(32.dp).background(if (isMe) Color.White.copy(0.8f) else AccentPurple, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text(message.replyToContent, style = MaterialTheme.typography.labelSmall, color = if (isMe) Color.White.copy(0.7f) else TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Text(
            text = message.content,
            color = if (isMe) Color.White else TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp)
        )

        if (isTranslating) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp).align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
                strokeWidth = 2.dp,
                color = if (isMe) Color.White else AccentPurple
            )
        }

        if (!translatedText.isNullOrBlank() && !isTranslating) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = if (isMe) Color.White.copy(0.3f) else Color.Gray.copy(0.3f))
            Text(translatedText, style = MaterialTheme.typography.bodySmall, color = if (isMe) Color.White.copy(0.85f) else TextSecondary, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.align(Alignment.End).padding(end = 10.dp, bottom = 6.dp, start = 10.dp)
        ) {
            if (message.isEdited) {
                Text("düzenlendi", style = MaterialTheme.typography.labelSmall, color = if (isMe) Color.White.copy(0.45f) else TextMuted, fontSize = 9.sp, fontStyle = FontStyle.Italic)
                Spacer(Modifier.width(2.dp))
            }
            Text(timeString, style = MaterialTheme.typography.labelSmall, color = if (isMe) Color.White.copy(0.65f) else TextMuted, fontSize = 10.sp)
            if (isMe) MessageStatusIcon(message.status)
        }
    }
}

@Composable
private fun ImageContent(
    message: MessageEntity,
    isMe: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    bubbleShape: RoundedCornerShape,
    timeString: String,
    onImageClick: (String) -> Unit,
    onDownloadClick: () -> Unit,
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        if (message.imagePath != null) {
            AsyncImage(
                model = File(message.imagePath),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().clip(bubbleShape).clickable { onImageClick(message.imagePath) },
                contentScale = ContentScale.FillWidth
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                if (message.thumbnailPath != null) {
                    AsyncImage(model = File(message.thumbnailPath), contentDescription = null, modifier = Modifier.fillMaxWidth().clip(bubbleShape), contentScale = ContentScale.FillWidth, alpha = 0.5f)
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(SurfaceContainer))
                }

                if (isDownloading) {
                    val animatedProgress by animateFloatAsState(targetValue = downloadProgress, animationSpec = tween(300), label = "progress")
                    Surface(shape = CircleShape, color = Color.Black.copy(0.6f), modifier = Modifier.size(64.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { animatedProgress }, modifier = Modifier.size(56.dp), color = AccentPurple, trackColor = Color.White.copy(0.15f), strokeWidth = 3.dp)
                            Text("${(downloadProgress * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Surface(onClick = onDownloadClick, shape = CircleShape, color = Color.Black.copy(0.55f), modifier = Modifier.size(56.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Görseli indir", modifier = Modifier.size(26.dp), tint = Color.White)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(6.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(timeString, style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp)
                    if (isMe) MessageStatusIcon(message.status, isImageOverlay = true)
                }
            }
        }
    }
}

@Composable
fun AudioMessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    timeString: String,
) {
    val speedSteps = remember { listOf(1.0f, 1.5f, 2.0f, 0.5f, 0.75f) }
    var speedIndex by remember { mutableStateOf(0) }
    val playbackSpeed = speedSteps[speedIndex]
    val speedLabel = when (playbackSpeed) {
        0.5f  -> "0.5×"
        0.75f -> "0.75×"
        1.0f  -> "1×"
        1.5f  -> "1.5×"
        2.0f  -> "2×"
        else  -> "1×"
    }

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var currentSec by remember { mutableStateOf(0) }
    val mediaPlayer = remember { android.media.MediaPlayer() }
    DisposableEffect(Unit) { onDispose { if (mediaPlayer.isPlaying) mediaPlayer.stop(); mediaPlayer.release() } }

    // Progress bar'ı her 200ms'de güncelle
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val duration = mediaPlayer.duration.takeIf { it > 0 } ?: 1
            val current = mediaPlayer.currentPosition
            progress = current.toFloat() / duration
            currentSec = current / 1000
            delay(200)
        }
    }

    val durationSec = ((message.audioDurationMs ?: 0L) / 1000).toInt()
    val displaySec = if (isPlaying) currentSec else durationSec
    val durationText = "%d:%02d".format(displaySec / 60, displaySec % 60)

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (message.audioPath != null) {
                IconButton(
                    onClick = {
                        if (isPlaying) { mediaPlayer.pause(); isPlaying = false }
                        else {
                            try {
                                if (!mediaPlayer.isPlaying) {
                                    mediaPlayer.reset()
                                    mediaPlayer.setDataSource(message.audioPath)
                                    mediaPlayer.prepare()
                                    mediaPlayer.setOnCompletionListener { isPlaying = false; progress = 0f; currentSec = 0 }
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(playbackSpeed)
                                }
                                mediaPlayer.start(); isPlaying = true
                            } catch (e: Exception) { android.util.Log.e("AudioBubble", "Playback error: ${e.message}") }
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = if (isMe) Color.White else AccentPurple, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = if (isMe) Color.White else AccentPurple, trackColor = if (isMe) Color.White.copy(0.3f) else SurfaceContainer)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(durationText, style = MaterialTheme.typography.labelSmall, color = if (isMe) Color.White.copy(0.7f) else TextMuted, fontSize = 10.sp)
                    }
                }
                // Hız butonu — sadece ses dosyası indirildiyse göster
                Surface(
                    onClick = {
                        speedIndex = (speedIndex + 1) % speedSteps.size
                        if (isPlaying && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            try { mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speedSteps[speedIndex]) }
                            catch (e: Exception) { android.util.Log.e("AudioBubble", "Speed error: ${e.message}") }
                        }
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isMe) Color.White.copy(0.2f) else AccentPurple.copy(0.15f),
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Text(
                        speedLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMe) Color.White else AccentPurple,
                        fontSize = 11.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            } else {
                if (isDownloading) CircularProgressIndicator(modifier = Modifier.size(32.dp), color = if (isMe) Color.White else AccentPurple, strokeWidth = 2.dp)
                else IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Download, "İndir", tint = if (isMe) Color.White else AccentPurple) }
                Text("🎤 $durationText", style = MaterialTheme.typography.bodySmall, color = if (isMe) Color.White.copy(0.7f) else TextMuted)
            }
        }
        Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(timeString, style = MaterialTheme.typography.labelSmall, color = if (isMe) Color.White.copy(0.65f) else TextMuted, fontSize = 10.sp)
            if (isMe) MessageStatusIcon(message.status)
        }
    }
}

@Composable
fun FileMessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    timeString: String,
) {
    val fileName = message.fileName ?: "Dosya"
    val fileSizeText = message.fileSizeBytes?.let { formatFileSize(it) } ?: ""

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.size(40.dp).background(if (isMe) Color.White.copy(0.15f) else AccentPurple.copy(0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.InsertDriveFile, null, tint = if (isMe) Color.White else AccentPurple, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(fileName, style = MaterialTheme.typography.bodySmall, color = if (isMe) Color.White else TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                if (fileSizeText.isNotBlank()) Text(fileSizeText, style = MaterialTheme.typography.labelSmall, color = if (isMe) Color.White.copy(0.65f) else TextMuted, fontSize = 10.sp)
            }
            if (message.filePath == null) {
                if (isDownloading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = if (isMe) Color.White else AccentPurple, strokeWidth = 2.dp)
                else IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Download, "İndir", tint = if (isMe) Color.White else AccentPurple) }
            } else {
                Icon(Icons.Default.CheckCircle, "İndirildi", tint = if (isMe) Color.White.copy(0.7f) else AccentPurple, modifier = Modifier.size(20.dp))
            }
        }
        Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(timeString, style = MaterialTheme.typography.labelSmall, color = if (isMe) Color.White.copy(0.65f) else TextMuted, fontSize = 10.sp)
            if (isMe) MessageStatusIcon(message.status)
        }
    }
}

@Composable
fun MessageStatusIcon(status: MessageStatus, isImageOverlay: Boolean = false) {
    val icon = when (status) {
        MessageStatus.PENDING   -> Icons.Default.AccessTime
        MessageStatus.SENT      -> Icons.Default.Check
        MessageStatus.DELIVERED -> Icons.Default.DoneAll
        MessageStatus.READ      -> Icons.Default.DoneAll
        MessageStatus.FAILED    -> Icons.Default.ErrorOutline
    }
    val tint = if (isImageOverlay) {
        when (status) { MessageStatus.READ -> ReadBlue; MessageStatus.FAILED -> ErrorRed; else -> Color.White }
    } else {
        when (status) {
            MessageStatus.PENDING   -> Color.White.copy(0.35f)
            MessageStatus.SENT      -> Color.White.copy(0.80f)
            MessageStatus.DELIVERED -> Color(0xFFB0BEC5)
            MessageStatus.READ      -> ReadBlue
            MessageStatus.FAILED    -> ErrorRed
        }
    }
    Icon(icon, contentDescription = status.name, modifier = Modifier.size(16.dp), tint = tint)
}

@Composable
fun UnreadMessagesHeader() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = AccentPurple.copy(0.15f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.5.dp, AccentPurple.copy(0.3f))
        ) {
            Text(
                text = UiText.StringResource(R.string.unread_messages).asString(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = AccentPurple,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}
