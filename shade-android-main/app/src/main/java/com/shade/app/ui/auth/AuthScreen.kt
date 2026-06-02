package com.shade.app.ui.auth

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shade.app.R
import com.shade.app.ui.theme.*
import kotlinx.coroutines.launch

enum class AuthStep {
    WELCOME, LOGIN, REGISTER, RECOVER
}

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> {
                if ((uiState as AuthUiState.Success).mnemonic.isEmpty()) {
                    onAuthSuccess()
                }
            }
            else -> {}
        }
    }

    AuthScreenContent(
        uiState = uiState,
        onLogin = { shadeId, mnemonic ->
            viewModel.login(shadeId, mnemonic, "Android Device")
        },
        onRegister = { _ ->
            viewModel.register("Android Device")
        },
        onResetUiState = {
            viewModel.resetUiState()
        },
        onAuthSuccess = onAuthSuccess
    )
}

@Composable
fun AuthScreenContent(
    uiState: AuthUiState,
    onLogin: (String, List<String>) -> Unit,
    onRegister: (String) -> Unit = {},
    onResetUiState: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    var currentStep by rememberSaveable { mutableStateOf(AuthStep.WELCOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(currentStep) {
        onResetUiState()
    }

    BackHandler(enabled = currentStep != AuthStep.WELCOME) {
        currentStep = AuthStep.WELCOME
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            scheme.background,
                            scheme.primaryContainer.copy(alpha = 0.55f),
                            scheme.background
                        )
                    )
                )
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState != AuthStep.WELCOME) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut())
                    }.using(
                        SizeTransform(clip = false)
                    )
                },
                label = "auth_transition"
            ) { step ->
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (step) {
                        AuthStep.WELCOME -> WelcomeLayout(onNavigate = { currentStep = it })
                        AuthStep.LOGIN -> LoginLayout(
                            uiState = uiState,
                            onLogin = onLogin,
                            onBack = { currentStep = AuthStep.WELCOME }
                        )
                        AuthStep.REGISTER -> RegisterLayout(
                            uiState = uiState,
                            onRegister = onRegister,
                            onBack = { currentStep = AuthStep.WELCOME },
                            snackbarHostState = snackbarHostState,
                            onAuthSuccess = {
                                onResetUiState()
                                currentStep = AuthStep.LOGIN
                            },
                        )
                        AuthStep.RECOVER -> RecoveryLayout(
                            uiState = uiState,
                            onRecover = onLogin,
                            onBack = { currentStep = AuthStep.WELCOME },
                            onAuthSuccess = onAuthSuccess
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI components
// ─────────────────────────────────────────────────────────────────────────────

/** Full-width gradient primary button used on every auth screen. */
@Composable
private fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val gradient = if (enabled)
        Brush.horizontalGradient(listOf(ElectricPurple, AccentPurple))
    else
        Brush.horizontalGradient(listOf(ElectricPurple.copy(alpha = 0.35f), AccentPurple.copy(alpha = 0.35f)))

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
        } else {
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}

/** Small chip showing end-to-end encryption status. */
@Composable
private fun SecurityBadge() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SuccessGreen.copy(alpha = 0.10f),
        border = BorderStroke(0.8.dp, SuccessGreen.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.end_to_end_encrypted),
                style = MaterialTheme.typography.labelSmall,
                color = SuccessGreen,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Standard OutlinedTextField with accent purple styling. */
@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentPurple,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = AccentPurple,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            cursorColor = AccentPurple
        )
    )
}

/** Inline error card shown below a button when uiState is Error. */
@Composable
private fun ErrorCard(message: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Surface(
        color = ErrorRed.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, ErrorRed.copy(alpha = 0.3f))
    ) {
        Text(
            text = message,
            color = ErrorRed,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Welcome
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WelcomeLayout(onNavigate: (AuthStep) -> Unit) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(if (isLandscape) 20.dp else 52.dp))

        // ── Logo with radial glow ─────────────────────────────────────────────
        Box(contentAlignment = Alignment.Center) {
            // Soft purple glow behind logo
            Box(
                modifier = Modifier
                    .size(if (isLandscape) 130.dp else 180.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AccentPurple.copy(alpha = 0.18f), Color.Transparent)
                        ),
                        CircleShape
                    )
            )
            Image(
                painter = painterResource(id = R.drawable.shade_logo),
                contentDescription = "Shade Logo",
                modifier = Modifier.size(if (isLandscape) 72.dp else 108.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = if (isLandscape) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 6.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Tagline
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Security badge
        SecurityBadge()

        Spacer(modifier = Modifier.height(if (isLandscape) 24.dp else 40.dp))

        // ── Info bubbles ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.7f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InfoBubble(
                text = stringResource(R.string.privacy_motto),
                icon = Icons.Default.Lock
            )
            InfoBubble(
                text = stringResource(R.string.no_personal_data),
                icon = Icons.Default.VisibilityOff
            )
            InfoBubble(
                text = stringResource(R.string.free_anonymous),
                icon = Icons.Default.AccountCircle
            )
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 44.dp))

        // ── Buttons ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.6f else 1f)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GradientButton(
                text = stringResource(R.string.login),
                onClick = { onNavigate(AuthStep.LOGIN) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = { onNavigate(AuthStep.REGISTER) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                border = BorderStroke(1.5.dp, AccentPurple.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AccentPurple
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    stringResource(R.string.register),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(
                onClick = { onNavigate(AuthStep.RECOVER) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.recover_account),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun InfoBubble(text: String, icon: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = scheme.onSurface.copy(alpha = 0.05f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.5.dp, scheme.outline.copy(alpha = 0.6f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Left accent stripe
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(ElectricPurple, AccentPurple)))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AccentPurple.copy(alpha = 0.13f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentPurple,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Login
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LoginLayout(
    uiState: AuthUiState,
    onLogin: (String, List<String>) -> Unit,
    onBack: () -> Unit
) {
    var shadeIdInput by remember { mutableStateOf("") }
    var mnemonicInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Brand mark
            Image(
                painter = painterResource(id = R.drawable.shade_logo),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.login),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            SecurityBadge()
            Spacer(modifier = Modifier.height(32.dp))

            // ── Form ──────────────────────────────────────────────────────────
            AuthTextField(
                value = shadeIdInput,
                onValueChange = { shadeIdInput = it },
                label = stringResource(R.string.shade_id)
            )
            Spacer(modifier = Modifier.height(14.dp))
            AuthTextField(
                value = mnemonicInput,
                onValueChange = { mnemonicInput = it },
                label = stringResource(R.string.mnemonic_label),
                placeholder = stringResource(R.string.mnemonic_placeholder)
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.mnemonic_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            GradientButton(
                text = stringResource(R.string.login),
                onClick = {
                    val mnemonicList = mnemonicInput.trim().split("\\s+".toRegex())
                    onLogin(shadeIdInput, mnemonicList)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = shadeIdInput.isNotBlank() && mnemonicInput.isNotBlank(),
                isLoading = uiState is AuthUiState.Loading
            )

            if (uiState is AuthUiState.Error) {
                ErrorCard(message = uiState.message.asString())
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Register
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RegisterLayout(
    uiState: AuthUiState,
    onRegister: (String) -> Unit,
    onBack: () -> Unit,
    onAuthSuccess: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var disclaimerChecked by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.shade_logo),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.register),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.register_notice),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            SecurityBadge()
            Spacer(modifier = Modifier.height(28.dp))

            // ── Security disclaimer card (kayıt sonrası gizlenir) ─────────────
            if (uiState !is AuthUiState.Success) Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { disclaimerChecked = !disclaimerChecked },
                color = WarningAmber.copy(alpha = 0.07f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (disclaimerChecked) SuccessGreen.copy(alpha = 0.5f)
                    else WarningAmber.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (disclaimerChecked) SuccessGreen else WarningAmber,
                        modifier = Modifier
                            .size(22.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.security_notice_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.security_notice_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Checkbox(
                        checked = disclaimerChecked,
                        onCheckedChange = { disclaimerChecked = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = SuccessGreen,
                            uncheckedColor = WarningAmber.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState !is AuthUiState.Success) {
                GradientButton(
                    text = stringResource(R.string.register_safely),
                    onClick = { onRegister("") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = disclaimerChecked,
                    isLoading = uiState is AuthUiState.Loading
                )

                if (!disclaimerChecked) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.security_notice_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            when (uiState) {
                is AuthUiState.Success -> {
                    SuccessSection(uiState, snackbarHostState, onAuthSuccess)
                }
                is AuthUiState.Error -> {
                    ErrorCard(message = uiState.message.asString())
                }
                else -> {}
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Success (mnemonic reveal after register)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SuccessSection(state: AuthUiState.Success, snackbarHostState: SnackbarHostState, onAuthSuccess: () -> Unit = {}) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = SuccessGreen.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, SuccessGreen.copy(alpha = 0.35f))
        ) {
            Text(
                state.message.asString(),
                color = SuccessGreen,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        state.shadeId?.let { id ->
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            val clipData = ClipData.newPlainText("shadeId", id)
                            clipboard.setClipEntry(ClipEntry(clipData))
                            snackbarHostState.showSnackbar(context.getString(R.string.id_copied))
                        }
                    },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.shade_id),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = id,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = AccentPurple.copy(alpha = 0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy ID",
                                tint = AccentPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (state.mnemonic.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))

            // Warning banner
            Surface(
                color = ErrorRed.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.8.dp, ErrorRed.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.save_mnemonic_warning),
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                userScrollEnabled = false
            ) {
                items(state.mnemonic.withIndex().toList()) { (index, word) ->
                    Surface(
                        modifier = Modifier.padding(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentPurple,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = word,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            GradientButton(
                text = stringResource(R.string.copy_mnemonic),
                onClick = {
                    val textToCopy = state.mnemonic.joinToString(" ")
                    scope.launch {
                        val clipData = ClipData.newPlainText("mnemonic", textToCopy)
                        clipboard.setClipEntry(ClipEntry(clipData))
                        snackbarHostState.showSnackbar(context.getString(R.string.mnemonic_copied))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onAuthSuccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                border = BorderStroke(1.5.dp, AccentPurple.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPurple),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.login),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recovery
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RecoveryLayout(
    uiState: AuthUiState,
    onRecover: (String, List<String>) -> Unit,
    onBack: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    var shadeIdInput by remember { mutableStateOf("") }
    var mnemonicInput by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success && uiState.mnemonic.isEmpty()) {
            onAuthSuccess()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.shade_logo),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.recovery_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.recovery_description),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            SecurityBadge()
            Spacer(modifier = Modifier.height(32.dp))

            AuthTextField(
                value = shadeIdInput,
                onValueChange = { shadeIdInput = it },
                label = stringResource(R.string.shade_id)
            )
            Spacer(modifier = Modifier.height(14.dp))
            AuthTextField(
                value = mnemonicInput,
                onValueChange = { mnemonicInput = it },
                label = stringResource(R.string.mnemonic_label),
                placeholder = stringResource(R.string.mnemonic_placeholder),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.recovery_mnemonic_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            GradientButton(
                text = stringResource(R.string.recover_button),
                onClick = {
                    val mnemonicList = mnemonicInput.trim().split("\\s+".toRegex())
                    onRecover(shadeIdInput, mnemonicList)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = shadeIdInput.isNotBlank() && mnemonicInput.trim().split("\\s+".toRegex()).size == 12,
                isLoading = uiState is AuthUiState.Loading
            )

            if (uiState is AuthUiState.Error) {
                ErrorCard(message = uiState.message.asString())
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
fun AuthScreenPreview() {
    AuthScreenContent(
        uiState = AuthUiState.Idle,
        onLogin = { _, _ -> },
        onRegister = {},
        onResetUiState = {},
        onAuthSuccess = {}
    )
}
