package com.shade.app.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shade.app.R
import com.shade.app.data.local.entities.ContactEntity
import com.shade.app.ui.theme.AccentPurple
import com.shade.app.ui.theme.SurfaceContainer
import com.shade.app.ui.theme.TextMuted
import com.shade.app.ui.theme.TextPrimary
import com.shade.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onGroupCreated: (groupId: String, groupName: String) -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var groupName by remember { mutableStateOf("") }

    // Grup oluşturulunca yönlendir
    LaunchedEffect(uiState.createdGroupId) {
        val gid = uiState.createdGroupId
        if (gid != null) onGroupCreated(gid, groupName)
    }

    // Arama filtrelemesi
    val filteredContacts = remember(uiState.contacts, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) uiState.contacts
        else uiState.contacts.filter { contact ->
            val name = contact.savedName ?: contact.shadeId
            name.contains(uiState.searchQuery, ignoreCase = true) ||
                    contact.shadeId.contains(uiState.searchQuery, ignoreCase = true)
        }
    }

    val selectedCount = uiState.selectedUserIds.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.create_group_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (selectedCount > 0) {
                            Text(
                                "$selectedCount kişi seçildi",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentPurple
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.createGroup(groupName) },
                        enabled = groupName.isNotBlank() && selectedCount > 0 && !uiState.isLoading,
                    ) {
                        Text(
                            stringResource(R.string.create_group_action),
                            color = if (groupName.isNotBlank() && selectedCount > 0) AccentPurple
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            // ── Grup adı ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text(stringResource(R.string.group_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPurple,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = AccentPurple,
                    cursorColor = AccentPurple,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
            )

            // ── Arama çubuğu ──────────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("Kişi ara...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPurple,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = AccentPurple,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Kişi listesi ─────────────────────────────────────────────────
            if (uiState.contacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Henüz kişin yok",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Önce Kişiler ekranından kişi ekle",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Kişi bulunamadı", color = TextMuted)
                }
            } else {
                LazyColumn {
                    items(filteredContacts, key = { it.userId }) { contact ->
                        ContactPickerRow(
                            contact = contact,
                            isSelected = contact.userId in uiState.selectedUserIds,
                            onClick = { viewModel.toggleContact(contact.userId) }
                        )
                    }
                }
            }

            // ── Yükleniyor ────────────────────────────────────────────────────
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentPurple, modifier = Modifier.padding(16.dp))
                }
            }

            // ── Hata mesajı ──────────────────────────────────────────────────
            uiState.error?.let { err ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(err, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun ContactPickerRow(
    contact: ContactEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(AccentPurple.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            val initials = (contact.savedName ?: contact.shadeId)
                .take(1).uppercase()
            Text(
                text = initials,
                color = AccentPurple,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(14.dp))

        // İsim + Shade ID
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.savedName ?: contact.shadeId,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            if (contact.savedName != null) {
                Text(
                    text = contact.shadeId,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Seçim göstergesi
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) AccentPurple
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Seçildi",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
