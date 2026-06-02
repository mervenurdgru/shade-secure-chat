package com.shade.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shade.app.R
import com.shade.app.data.preferences.ThemeMode
import com.shade.app.ui.theme.AccentPurple
import com.shade.app.ui.theme.ErrorRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onSecurityAuditClick: () -> Unit,
    onWebPairingClick: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val loggedOut by viewModel.loggedOut.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    LaunchedEffect(loggedOut) {
        if (loggedOut) onLogout()
    }

    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var appearanceExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }

    // Seçili dil — SharedPreferences'tan okunur
    var currentLang by remember {
        val saved = context.getSharedPreferences("shade_prefs", Context.MODE_PRIVATE)
            .getString("language", "en") ?: "en"
        mutableStateOf(saved)
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AppearanceHeaderRow(
                        expanded = appearanceExpanded,
                        currentLabel = when (themeMode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                        },
                        onToggle = { appearanceExpanded = !appearanceExpanded }
                    )
                    AnimatedVisibility(
                        visible = appearanceExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            ThemeAppearanceRow(
                                title = stringResource(R.string.theme_system),
                                subtitle = stringResource(R.string.theme_system_subtitle),
                                icon = Icons.Default.BrightnessAuto,
                                selected = themeMode == ThemeMode.SYSTEM,
                                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                                modifier = Modifier.padding(start = 24.dp)
                            )
                            ThemeAppearanceRow(
                                title = stringResource(R.string.theme_light),
                                subtitle = stringResource(R.string.theme_light_subtitle),
                                icon = Icons.Default.LightMode,
                                selected = themeMode == ThemeMode.LIGHT,
                                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                                modifier = Modifier.padding(start = 24.dp)
                            )
                            ThemeAppearanceRow(
                                title = stringResource(R.string.theme_dark),
                                subtitle = stringResource(R.string.theme_dark_subtitle),
                                icon = Icons.Default.DarkMode,
                                selected = themeMode == ThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                                modifier = Modifier.padding(start = 24.dp)
                            )
                        }
                    }
                }
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = scheme.outlineVariant
                )
            }
            // ── Profil ────────────────────────────────────────────────────────
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_profile),
                    subtitle = stringResource(R.string.settings_profile_subtitle),
                    icon = Icons.Default.Person,
                    onClick = onNavigateToProfile
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_contacts),
                    subtitle = stringResource(R.string.settings_contacts_subtitle),
                    icon = Icons.Default.People,
                    onClick = onNavigateToContacts
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_security_audit),
                    subtitle = stringResource(R.string.settings_security_audit_subtitle),
                    icon = Icons.Default.Security,
                    onClick = onSecurityAuditClick
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_web_connect),
                    subtitle = stringResource(R.string.settings_web_connect_subtitle),
                    icon = Icons.Default.QrCodeScanner,
                    onClick = onWebPairingClick
                )
            }
            // ── Dil seçici ────────────────────────────────────────────────────
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = scheme.outlineVariant
                )
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { languageExpanded = !languageExpanded }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_language),
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (currentLang == "tr") stringResource(R.string.lang_turkish)
                                       else stringResource(R.string.lang_english),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (languageExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = languageExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            ThemeAppearanceRow(
                                title = stringResource(R.string.lang_english),
                                subtitle = "English",
                                icon = Icons.Default.Language,
                                selected = currentLang != "tr",
                                onClick = {
                                    context.getSharedPreferences("shade_prefs", Context.MODE_PRIVATE)
                                        .edit().putString("language", "en").apply()
                                    currentLang = "en"
                                    languageExpanded = false
                                    (context as? Activity)?.recreate()
                                },
                                modifier = Modifier.padding(start = 24.dp)
                            )
                            ThemeAppearanceRow(
                                title = stringResource(R.string.lang_turkish),
                                subtitle = "Türkçe",
                                icon = Icons.Default.Language,
                                selected = currentLang == "tr",
                                onClick = {
                                    context.getSharedPreferences("shade_prefs", Context.MODE_PRIVATE)
                                        .edit().putString("language", "tr").apply()
                                    currentLang = "tr"
                                    languageExpanded = false
                                    (context as? Activity)?.recreate()
                                },
                                modifier = Modifier.padding(start = 24.dp)
                            )
                        }
                    }
                }
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = scheme.outlineVariant
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_logout),
                    subtitle = stringResource(R.string.settings_logout_subtitle),
                    icon = Icons.Default.ExitToApp,
                    iconTint = ErrorRed,
                    titleColor = ErrorRed,
                    onClick = viewModel::logout
                )
            }
        }
    }
}

@Composable
private fun AppearanceHeaderRow(
    expanded: Boolean,
    currentLabel: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Palette,
            contentDescription = null,
            tint = AccentPurple,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.theme_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ThemeAppearanceRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentPurple,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = AccentPurple,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color = AccentPurple,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
