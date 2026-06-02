package com.shade.app.ui.audit

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shade.app.R
import com.shade.app.data.remote.dto.AuditLogItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen(
    onBackClick: () -> Unit,
    viewModel: SecurityAuditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audit_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchLogs() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Bilgi kartı
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.audit_info_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.fetchLogs() }) {
                                Text(stringResource(R.string.audit_retry))
                            }
                        }
                    }
                }
                uiState.logs.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.audit_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.logs) { log ->
                            AuditLogCard(log = log)
                        }
                    }
                }
            }
        }
    }
}

private data class AuditLogVisual(
    val icon: ImageVector,
    val iconColor: Color,
    @StringRes val labelResId: Int = 0,
    val labelOverride: String? = null,
)

private fun auditLogVisual(actionType: String): AuditLogVisual {
    val successGreen = Color(0xFF4CAF50)
    val registerPurple = Color(0xFF7C4DFF)
    val failRed = Color(0xFFE53935)
    val failOrange = Color(0xFFFF6D00)
    val deviceBlue = Color(0xFF1E88E5)
    val infraGray = Color(0xFF78909C)
    return when (actionType) {
        "USER_REGISTERED" -> AuditLogVisual(Icons.Default.PersonAdd, registerPurple, R.string.audit_registered)
        "LOGIN_SUCCESS" -> AuditLogVisual(Icons.Default.LockOpen, successGreen, R.string.audit_login_success)
        "LOGIN_FAILED_INVALID_OR_EXPIRED_CHALLENGE" ->
            AuditLogVisual(Icons.Default.Warning, failOrange, R.string.audit_login_failed_expired_challenge)
        "LOGIN_FAILED_INVALID_CHALLENGE" ->
            AuditLogVisual(Icons.Default.Warning, failOrange, R.string.audit_login_failed_invalid_challenge)
        "LOGIN_FAILED_ACCOUNT_MISSING" ->
            AuditLogVisual(Icons.Default.Warning, failRed, R.string.audit_login_failed_account_missing)
        "LOGIN_FAILED_INVALID_SIGNATURE" ->
            AuditLogVisual(Icons.Default.Warning, failRed, R.string.audit_login_failed_invalid_signature)
        "LOGIN_FAILED_INVALID_DEVICE_ID" ->
            AuditLogVisual(Icons.Default.Smartphone, failOrange, R.string.audit_login_failed_invalid_device_id)
        "LOGIN_FAILED_UNKNOWN_DEVICE" ->
            AuditLogVisual(Icons.Default.Smartphone, failOrange, R.string.audit_login_failed_unknown_device)
        "LOGIN_FAILED_DEVICE_LOOKUP" ->
            AuditLogVisual(Icons.Default.Smartphone, deviceBlue, R.string.audit_login_failed_device_lookup)
        "LOGIN_FAILED_DEVICE_UPDATE" ->
            AuditLogVisual(Icons.Default.Smartphone, deviceBlue, R.string.audit_login_failed_device_update)
        "LOGIN_FAILED_DEVICE_CREATE" ->
            AuditLogVisual(Icons.Default.Smartphone, deviceBlue, R.string.audit_login_failed_device_create)
        "LOGIN_FAILED_TOKEN_ISSUE" ->
            AuditLogVisual(Icons.Default.ErrorOutline, infraGray, R.string.audit_login_failed_token_issue)
        else -> AuditLogVisual(Icons.Default.DeviceUnknown, Color.Gray, labelOverride = actionType)
    }
}

@Composable
fun AuditLogCard(log: AuditLogItem) {
    val visual = auditLogVisual(log.actionType)
    val icon = visual.icon
    val iconColor = visual.iconColor
    val label = visual.labelOverride ?: stringResource(visual.labelResId)

    val formattedTime = try {
        val instant = Instant.parse(log.timestamp)
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (e: Exception) {
        log.timestamp
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (log.ipAddress.isNotBlank() && log.ipAddress != "system") {
                    Text(
                        text = "IP: ${log.ipAddress}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
