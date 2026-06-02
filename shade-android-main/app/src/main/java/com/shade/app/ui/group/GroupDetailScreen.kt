package com.shade.app.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.shade.app.data.local.entities.ContactEntity
import com.shade.app.data.local.entities.GroupMemberEntity
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.ui.theme.AccentPurple
import com.shade.app.ui.theme.TextMuted
import com.shade.app.ui.theme.TextSecondary
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onLeft: () -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.leftGroup) {
        if (state.leftGroup) onLeft()
    }

    var confirmTarget by remember { mutableStateOf<GroupMemberEntity?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Grup Bilgisi", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (state.isOwner) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = "Grubu Sil",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GroupHeader(
                name = state.group?.name ?: "",
                memberCount = state.memberCount,
            )

            Spacer(Modifier.height(8.dp))

            ActionRow(
                isOwner = state.isOwner,
                onInviteClick = viewModel::createInvite,
                onLeaveClick = { confirmLeave = true },
                onAddMemberClick = { showAddMemberDialog = true },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Gönderilen Medyalar ──────────────────────────────────────────
            if (state.mediaMessages.isNotEmpty()) {
                Text(
                    text = "Gönderilen Medyalar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                MediaRow(mediaMessages = state.mediaMessages)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            Text(
                text = "Üyeler (${state.memberCount})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (state.isLoading && state.members.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = AccentPurple) }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(state.members, key = { it.userId }) { member ->
                        MemberRow(
                            member = member,
                            isMe = member.userId == state.myUserId,
                            canRemove = state.isOwner && member.userId != state.myUserId,
                            onRemove = { confirmTarget = member },
                        )
                    }
                }
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
    if (showAddMemberDialog) {
        AddMemberDialog(
            savedContacts = state.savedContacts,
            currentMemberUserIds = state.members.map { it.userId }.toSet(),
            onConfirm = { shadeId ->
                showAddMemberDialog = false
                viewModel.addMemberByShadeId(shadeId)
            },
            onDismiss = { showAddMemberDialog = false },
        )
    }

    state.inviteCode?.let { code ->
        InviteCodeDialog(code = code, onDismiss = viewModel::dismissInvite)
    }

    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Hata") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text("Tamam") }
            },
        )
    }

    confirmTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text("Üyeyi çıkar?") },
            text = {
                Text("${member.shadeId.ifBlank { member.userId }} bu gruptan kaldırılacak.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeMember(member.userId)
                    confirmTarget = null
                }) { Text("Çıkar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) { Text("Vazgeç") }
            },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Gruptan ayrıl?") },
            text = { Text("Bu gruptan ayrılırsanız yeni mesajları göremeyeceksiniz.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.leaveGroup()
                }) { Text("Ayrıl", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Vazgeç") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Grubu sil?") },
            text = { Text("Grup tüm üyeler için geri dönüşsüz şekilde silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteGroup()
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
private fun GroupHeader(name: String, memberCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(AccentPurple.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Group,
                contentDescription = null,
                tint = AccentPurple,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = name.ifBlank { "—" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$memberCount üye",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionRow(
    isOwner: Boolean,
    onInviteClick: () -> Unit,
    onLeaveClick: () -> Unit,
    onAddMemberClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onInviteClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPurple),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Davet linki")
            }
            OutlinedButton(
                onClick = onLeaveClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ayrıl")
            }
        }
        if (isOwner) {
            OutlinedButton(
                onClick = onAddMemberClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPurple),
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Üye Ekle")
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupMemberEntity,
    isMe: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canRemove, onClick = onRemove)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AccentPurple.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (member.shadeId.ifBlank { member.userId }).take(1).uppercase(),
                color = AccentPurple,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.shadeId.ifBlank { member.userId },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                )
                if (isMe) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "(siz)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
            }
            Text(
                text = if (member.role == "owner") "Yönetici" else "Üye",
                style = MaterialTheme.typography.bodySmall,
                color = if (member.role == "owner") AccentPurple else TextSecondary,
            )
        }

        if (canRemove) {
            Icon(
                Icons.Default.PersonRemove,
                contentDescription = "Çıkar",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun AddMemberDialog(
    savedContacts: List<ContactEntity>,
    currentMemberUserIds: Set<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var manualId by remember { mutableStateOf("") }
    val eligible = savedContacts.filter { it.userId !in currentMemberUserIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Üye Ekle") },
        text = {
            Column {
                if (eligible.isNotEmpty()) {
                    Text(
                        "Rehberden seç:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(eligible, key = { it.userId }) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onConfirm(contact.shadeId) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(AccentPurple.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = (contact.savedName ?: contact.shadeId).take(1).uppercase(),
                                        color = AccentPurple,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = contact.savedName ?: contact.shadeId,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = contact.shadeId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "veya Shade ID gir:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text("Eklemek istediğiniz kişinin Shade ID'sini girin.")
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = manualId,
                    onValueChange = { manualId = it },
                    label = { Text("Shade ID (örn: CG-1234-ABCD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(manualId) },
                enabled = manualId.isNotBlank(),
            ) { Text("Ekle", color = AccentPurple) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        },
    )
}

@Composable
private fun MediaRow(mediaMessages: List<MessageEntity>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(mediaMessages, key = { it.messageId }) { message ->
            val imagePath = message.imagePath
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (imagePath != null && File(imagePath).exists()) {
                    AsyncImage(
                        model = File(imagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = AccentPurple.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun InviteCodeDialog(code: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Davet kodu") },
        text = {
            Column {
                Text("Bu kodu paylaşarak yeni üye davet edebilirsiniz.")
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentPurple,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(code))
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Kopyala",
                                tint = AccentPurple,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        },
    )
}
