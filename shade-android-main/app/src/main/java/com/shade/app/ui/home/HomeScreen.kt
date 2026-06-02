package com.shade.app.ui.home

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shade.app.R
import com.shade.app.data.local.model.ChatWithContact
import com.shade.app.ui.components.AvatarImage
import com.shade.app.ui.theme.AccentPurple
import com.shade.app.ui.theme.BubbleMine
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "SHADE_HOME"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onChatClick: (String, String) -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToCreateGroup: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()

    LaunchedEffect(Unit) {
        Log.d(TAG, "HomeScreen açıldı")
    }

    LaunchedEffect(loggedOut) {
        if (loggedOut) onLogout()
    }

    DisposableEffect(Unit) {
        onDispose { Log.d(TAG, "HomeScreen kapandı") }
    }

    val scheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.shade_logo),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        Log.d(TAG, "Ayarlar butonuna tıklandı")
                        onSettingsClick()
                    }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = scheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    titleContentColor = scheme.onSurface,
                    actionIconContentColor = scheme.onSurface,
                    scrolledContainerColor = scheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                // Secondary FAB — new group
                SmallFloatingActionButton(
                    onClick = {
                        Log.d(TAG, "Yeni grup FAB tıklandı")
                        onNavigateToCreateGroup()
                    },
                    containerColor = AccentPurple.copy(alpha = 0.85f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = stringResource(R.string.new_group)
                    )
                }
                // Primary FAB — new 1:1 chat
                FloatingActionButton(
                    onClick = {
                        Log.d(TAG, "Yeni mesaj FAB tıklandı → Kişiler ekranına geçiliyor")
                        onNavigateToContacts()
                    },
                    containerColor = AccentPurple,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = stringResource(R.string.new_chat)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.chats.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentPurple,
                        strokeWidth = 3.dp
                    )
                }
                uiState.chats.isEmpty() -> {
                    EmptyHomeState()
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.home_inbox_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp)
                            )
                        }
                        items(
                            items = uiState.chats,
                            key = { it.chat.chatId }
                        ) { chat ->
                            val isNonMember = chat.chat.chatId in uiState.nonMemberGroupIds
                            ChatItem(
                                chat = chat,
                                canDelete = !chat.chat.isGroup || isNonMember,
                                onClick = {
                                    Log.d(TAG, "Sohbete tıklandı: ${chat.chat.chatId}")
                                    onChatClick(chat.chat.chatId, chat.displayName)
                                },
                                onLongClick = { viewModel.deleteChat(chat) }
                            )
                        }
                    }
                }
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeState() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHigh,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = AccentPurple.copy(alpha = 0.85f)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatItem(
    chat: ChatWithContact,
    canDelete: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog && canDelete) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Sohbeti sil?") },
            text = { Text("${chat.displayName} sohbeti ve tüm mesajları silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onLongClick()
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("İptal") }
            }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (canDelete) showDeleteDialog = true }
            ),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (chat.chat.isGroup) {
                Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = BubbleMine) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Group, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            } else {
                AvatarImage(
                    imagePath = chat.contact?.profileImagePath,
                    fallbackLetter = chat.displayName,
                    size = 52.dp,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = formatTimestamp(chat.chat.lastMessageTimestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (chat.chat.unreadCount > 0) AccentPurple else scheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val rawLastMessage = chat.chat.lastMessage
                    val previewText = when {
                        rawLastMessage == null -> stringResource(R.string.home_last_message_placeholder)
                        rawLastMessage.trimStart().startsWith("{") && rawLastMessage.contains("imageId") -> "📷 Fotoğraf"
                        rawLastMessage.trimStart().startsWith("{") && rawLastMessage.contains("audioId") -> "🎤 Ses mesajı"
                        rawLastMessage.trimStart().startsWith("{") && rawLastMessage.contains("fileId") -> "📎 Dosya"
                        else -> rawLastMessage
                    }
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (chat.chat.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        UnreadBadge(count = chat.chat.unreadCount)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    val label = if (count > 99) "99+" else count.toString()
    Surface(
        shape = CircleShape,
        color = AccentPurple,
        modifier = Modifier.heightIn(min = 22.dp).defaultMinSize(minWidth = 22.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }

    return if (now.get(Calendar.DATE) == msgCal.get(Calendar.DATE) &&
        now.get(Calendar.MONTH) == msgCal.get(Calendar.MONTH) &&
        now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
    ) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
    }
}
