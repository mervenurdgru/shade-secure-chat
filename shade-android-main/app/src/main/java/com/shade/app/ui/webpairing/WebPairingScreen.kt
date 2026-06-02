package com.shade.app.ui.webpairing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shade.app.R
import com.shade.app.ui.theme.AccentPurple
import com.shade.app.ui.theme.SuccessGreen
import com.shade.app.ui.util.UiText

private val WebPairingUiState.isAwaitingPairing: Boolean
    get() = when (this) {
        WebPairingUiState.Authorizing,
        WebPairingUiState.Connecting,
        WebPairingUiState.Syncing -> true
        else -> false
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPairingScreen(
    onBackClick: () -> Unit,
    viewModel: WebPairingViewModel = hiltViewModel()
) {
    var isScanning by remember { mutableStateOf(false) }
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState !is WebPairingUiState.Idle && uiState !is WebPairingUiState.Error) {
            isScanning = false
        }
    }

    BackHandler(enabled = isScanning) {
        isScanning = false
    }

    BackHandler(enabled = uiState.isAwaitingPairing && !isScanning) {
        viewModel.cancelPairingAttempt()
        onBackClick()
    }

    val scheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        UiText.StringResource(R.string.pair_web).asString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                isScanning -> isScanning = false
                                uiState.isAwaitingPairing -> {
                                    viewModel.cancelPairingAttempt()
                                    onBackClick()
                                }
                                else -> onBackClick()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isScanning) {
                QrScanner(
                    modifier = Modifier.fillMaxSize(),
                    onQrText = { raw ->
                        isScanning = false
                        viewModel.onQrScanned(raw)
                    },
                    onCancelScan = { isScanning = false }
                )
            } else {
                PairingStateContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    uiState = uiState,
                    onScanClick = {
                        viewModel.reset()
                        isScanning = true
                    },
                    onDisconnect = viewModel::disconnect,
                    onCancelWaiting = viewModel::cancelPairingAttempt
                )
            }
        }
    }
}

@Composable
private fun PairingStateContent(
    modifier: Modifier = Modifier,
    uiState: WebPairingUiState,
    onScanClick: () -> Unit,
    onDisconnect: () -> Unit,
    onCancelWaiting: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (uiState) {
                    is WebPairingUiState.Idle -> {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = UiText.StringResource(R.string.connect_with_web).asString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = UiText.StringResource(R.string.scan_qr).asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onScanClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                UiText.StringResource(R.string.scan_qr).asString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    WebPairingUiState.Authorizing,
                    WebPairingUiState.Connecting,
                    WebPairingUiState.Syncing -> {
                        val titleRes = when (uiState) {
                            WebPairingUiState.Authorizing -> R.string.authenticating
                            WebPairingUiState.Connecting -> R.string.connection_establishing
                            WebPairingUiState.Syncing -> R.string.web_pairing_sending_batches
                            else -> R.string.authenticating
                        }
                        val subtitleRes = when (uiState) {
                            WebPairingUiState.Authorizing -> R.string.server_secure_session
                            WebPairingUiState.Connecting -> R.string.sync_with_web
                            WebPairingUiState.Syncing -> R.string.web_pairing_sending_batches_sub
                            else -> R.string.server_secure_session
                        }
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = AccentPurple,
                            trackColor = scheme.outline.copy(alpha = 0.35f)
                        )
                        Text(
                            text = UiText.StringResource(titleRes).asString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = UiText.StringResource(subtitleRes).asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        TextButton(
                            onClick = onCancelWaiting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                UiText.StringResource(R.string.pairing_cancel).asString(),
                                color = AccentPurple
                            )
                        }
                    }

                    WebPairingUiState.Connected -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = UiText.StringResource(R.string.sync_ok).asString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = UiText.StringResource(R.string.phone_web_sync).asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = scheme.surface.copy(alpha = 0.6f)
                        ) {
                            Text(
                                modifier = Modifier.padding(14.dp),
                                text = UiText.StringResource(R.string.connection_ten_min).asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = scheme.surface.copy(alpha = 0.45f)
                        ) {
                            Text(
                                modifier = Modifier.padding(14.dp),
                                text = UiText.StringResource(R.string.sync_warning).asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
                            border = BorderStroke(1.dp, scheme.outline)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = UiText.StringResource(R.string.sync_interrupt).asString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = UiText.StringResource(R.string.sync_stops).asString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is WebPairingUiState.Error -> {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = UiText.StringResource(R.string.error_occurred).asString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onScanClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(10.dp))
                            Text(UiText.StringResource(R.string.try_again).asString())
                        }
                    }
                }
            }
        }
    }
}
