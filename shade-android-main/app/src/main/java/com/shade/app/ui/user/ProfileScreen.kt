package com.shade.app.ui.user

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shade.app.ui.components.AvatarImage
import com.shade.app.ui.theme.AccentPurple
import com.shade.app.ui.theme.NeonPurple
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val contact by viewModel.contactState.collectAsState()
    var nameText by remember(contact) { mutableStateOf(contact?.savedName ?: "") }
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        viewModel.saveSuccess.collectLatest {
            Toast.makeText(context, "Kişi başarıyla güncellendi", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            Surface(
                color = scheme.surface,
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.onSurface
                        )
                    }
                    Text(
                        text = "Profil",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Avatar with gradient ring
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .background(
                            Brush.linearGradient(colors = listOf(AccentPurple, NeonPurple)),
                            CircleShape
                        )
                )
                AvatarImage(
                    imagePath = contact?.profileImagePath,
                    fallbackLetter = (contact?.savedName ?: contact?.profileName ?: contact?.shadeId ?: "?"),
                    size = 120.dp,
                    backgroundColor = scheme.surfaceContainerHigh,
                    textColor = AccentPurple,
                    fontSize = 48.sp
                )
            }

            // Shade ID Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = scheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp, scheme.outline
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Shade ID",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentPurple,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        contact?.shadeId ?: "Yükleniyor...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    // Display name (profile name set by the contact themselves)
                    val profileName = contact?.profileName
                    if (!profileName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            profileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Name input
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text("Kişiyi Kaydet") },
                placeholder = { Text("İsim", color = scheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = AccentPurple
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPurple,
                    unfocusedBorderColor = scheme.outline,
                    focusedLabelColor = AccentPurple,
                    unfocusedLabelColor = scheme.onSurfaceVariant,
                    focusedTextColor = scheme.onSurface,
                    unfocusedTextColor = scheme.onSurface,
                    cursorColor = AccentPurple,
                    focusedLeadingIconColor = AccentPurple,
                    unfocusedLeadingIconColor = scheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.saveContact(nameText)
                    onBackClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = nameText.isNotBlank() && (nameText != contact?.savedName),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPurple,
                    disabledContainerColor = scheme.surfaceContainerHigh
                )
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save")
                Spacer(Modifier.width(8.dp))
                Text(
                    "Kaydet ve Dön",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
