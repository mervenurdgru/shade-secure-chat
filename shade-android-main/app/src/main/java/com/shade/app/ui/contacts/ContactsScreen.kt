package com.shade.app.ui.contacts

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shade.app.data.local.entities.ContactEntity
import com.shade.app.ui.components.AvatarImage

private const val TAG = "SHADE_CONTACTS"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onBackClick: () -> Unit,
    onContactClick: (String, String) -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lookupState by viewModel.lookupState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var shadeIdInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        Log.d(TAG, "ContactsScreen açıldı")
    }

    LaunchedEffect(lookupState) {
        if (lookupState is ContactLookupState.Success) {
            showAddDialog = false
            shadeIdInput = ""
            viewModel.resetLookupState()
        }
    }

    DisposableEffect(Unit) {
        onDispose { Log.d(TAG, "ContactsScreen kapandı") }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                viewModel.resetLookupState()
            },
            title = { Text("Yeni Kişi Ekle") },
            text = {
                Column {
                    Text(
                        text = "Eklemek istediğin kişinin Shade ID'sini gir.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = shadeIdInput,
                        onValueChange = { shadeIdInput = it },
                        label = { Text("Shade ID") },
                        placeholder = { Text("Örn: CG-####-####") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = lookupState is ContactLookupState.Error
                    )
                    if (lookupState is ContactLookupState.Error) {
                        Text(
                            text = (lookupState as ContactLookupState.Error).message.asString(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        Log.d(TAG, "Kişi aranıyor: $shadeIdInput")
                        viewModel.startLookup(shadeIdInput, onContactClick)
                    },
                    enabled = shadeIdInput.isNotBlank() && lookupState !is ContactLookupState.Loading
                ) {
                    if (lookupState is ContactLookupState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Ekle ve Mesaj Gönder")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    viewModel.resetLookupState()
                }) { Text("İptal") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kişiler") },
                navigationIcon = {
                    IconButton(onClick = {
                        Log.d(TAG, "Geri butonuna tıklandı → Home'a dönülüyor")
                        onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                Log.d(TAG, "Yeni kişi ekleme FAB tıklandı")
                showAddDialog = true
            }) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Kişi Ekle")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Kişi ara...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (uiState.searchQuery.isBlank()) "Henüz hiç kişin yok."
                            else "Kişi bulunamadı.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (uiState.searchQuery.isBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sağ alttaki + butonuna bas ve Shade ID gir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.contacts, key = { it.userId }) { contact ->
                        ContactItem(
                            contact = contact,
                            onClick = {
                                if (!contact.isBlocked) {
                                    val displayName = contact.savedName ?: contact.shadeId
                                    Log.d(TAG, "Kişiye tıklandı: ${contact.shadeId} → Chat açılıyor")
                                    onContactClick(contact.shadeId, displayName)
                                }
                            },
                            onDelete = {
                                Log.d(TAG, "Kişi silme isteği: ${contact.shadeId}")
                                viewModel.deleteContact(contact)
                            },
                            onToggleBlock = {
                                Log.d(TAG, "Engel toggle: ${contact.shadeId}")
                                viewModel.toggleBlock(contact)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactItem(
    contact: ContactEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleBlock: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    val displayName = contact.savedName ?: contact.shadeId

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Kişiyi Sil") },
            text = { Text("$displayName kişisini silmek istediğine emin misin?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("İptal") }
            }
        )
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text(if (contact.isBlocked) "Engeli Kaldır" else "Kişiyi Engelle") },
            text = {
                Text(
                    if (contact.isBlocked)
                        "$displayName kişisinin engelini kaldırmak istiyor musun?"
                    else
                        "$displayName kişisini engellemek istiyor musun? Engellenince mesaj gönderemezsin."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onToggleBlock()
                    showBlockDialog = false
                }) {
                    Text(
                        if (contact.isBlocked) "Engeli Kaldır"
                        else "Engelle",
                        color = if (contact.isBlocked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) { Text("İptal") }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarImage(
            imagePath = contact.profileImagePath,
            fallbackLetter = displayName,
            size = 50.dp,
            backgroundColor = if (contact.isBlocked)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.primaryContainer,
            textColor = if (contact.isBlocked)
                MaterialTheme.colorScheme.onErrorContainer
            else
                MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (contact.isBlocked)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (contact.isBlocked) "Engellendi • ${contact.shadeId}"
                else contact.shadeId,
                style = MaterialTheme.typography.bodySmall,
                color = if (contact.isBlocked)
                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = { showBlockDialog = true }) {
            Icon(
                if (contact.isBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                contentDescription = if (contact.isBlocked) "Engeli Kaldır" else "Engelle",
                tint = if (contact.isBlocked)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = { showDeleteDialog = true }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Sil",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
