package com.shade.app

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.shade.app.data.preferences.ThemeMode
import com.shade.app.data.preferences.ThemePreferenceRepository
import com.shade.app.security.KeyVaultManager
import com.shade.app.security.RootDetectionManager
import kotlinx.coroutines.launch
import com.shade.app.ui.audit.SecurityAuditScreen
import com.shade.app.ui.auth.AuthScreen
import com.shade.app.ui.chat.ChatScreen
import com.shade.app.ui.contacts.ContactsScreen
import com.shade.app.ui.group.CreateGroupScreen
import com.shade.app.ui.group.GroupDetailScreen
import com.shade.app.ui.home.HomeScreen
import com.shade.app.ui.navigation.Screen
import com.shade.app.ui.myprofile.MyProfileScreen
import com.shade.app.ui.settings.SettingsScreen
import com.shade.app.ui.theme.ShadeTheme
import com.shade.app.ui.user.ProfileScreen
import com.shade.app.ui.webpairing.WebPairingScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "SHADE_NAV"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var keyVaultManager: KeyVaultManager

    @Inject
    lateinit var rootDetectionManager: RootDetectionManager

    @Inject
    lateinit var themePreferenceRepository: ThemePreferenceRepository

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.d("FCM", "Notification permission granted: $isGranted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyRecentAppsBranding()
        applySavedLocale()   // ← kayıtlı dil ayarını uygula
        // Ekran görüntüsü ve ekran kaydını engelle (gizlilik uygulaması için zorunlu)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        Log.d(TAG, "MainActivity onCreate")

        askNotificationPermission()

        val pendingChatId = intent?.getStringExtra("chatId")
        val pendingChatName = intent?.getStringExtra("chatName")

        val isRooted = rootDetectionManager.isDeviceRooted(this)

        setContent {
            val themeMode by themePreferenceRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            ShadeTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var rootWarningDismissed by remember { mutableStateOf(false) }

                    if (isRooted && !rootWarningDismissed) {
                        RootWarningDialog(onDismiss = { rootWarningDismissed = true })
                    } else {
                        AppNavigation(
                            keyVaultManager = keyVaultManager,
                            pendingChatId = pendingChatId,
                            pendingChatName = pendingChatName
                        )
                    }
                }
            }
        }

        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d("FCM", "Token: $token")
                    lifecycleScope.launch {
                        keyVaultManager.saveFcmToken(token)
                    }
                } else {
                    Log.e("FCM", "Token alınamadı", task.exception)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity onDestroy")
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /** SharedPreferences'tan kaydedilmiş dili okur ve uygular. */
    private fun applySavedLocale() {
        val lang = getSharedPreferences("shade_prefs", Context.MODE_PRIVATE)
            .getString("language", "en") ?: "en"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        Log.d(TAG, "Locale uygulandı: $lang")
    }

    /** Recent/overview küçük ikonunun güncel @mipmap/ic_launcher ile eşleşmesi için (splash ile uyumlu). */
    private fun applyRecentAppsBranding() {
        val label = getString(R.string.app_name)
        val color = ContextCompat.getColor(this, R.color.splash_screen_background)
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    val desc = ActivityManager.TaskDescription.Builder()
                        .setLabel(label)
                        .setIcon(R.mipmap.ic_launcher)
                        .build()
                    setTaskDescription(desc)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    @Suppress("DEPRECATION")
                    setTaskDescription(
                        ActivityManager.TaskDescription(label, R.mipmap.ic_launcher, color)
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Recent apps branding: ${e.message}")
        }
    }
}

@Composable
fun AppNavigation(
    keyVaultManager: KeyVaultManager,
    pendingChatId: String? = null,
    pendingChatName: String? = null
) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(keyVaultManager) {
        startDestination = if (keyVaultManager.hasStoredAccessToken()) {
            Screen.Home.route
        } else {
            Screen.Auth.route
        }
    }

    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize())
    } else {
        val start = startDestination!!
        LaunchedEffect(pendingChatId, pendingChatName, start) {
            if (pendingChatId != null && pendingChatName != null) {
                val chatRoute = Screen.Chat.createRoute(pendingChatId, pendingChatName)
                if (start == Screen.Auth.route) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                    navController.navigate(chatRoute)
                } else {
                    navController.navigate(chatRoute)
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = start
        ) {
        composable(Screen.Auth.route) {
            Log.d(TAG, "→ Auth ekranı")
            AuthScreen(
                viewModel = hiltViewModel(),
                onAuthSuccess = {
                    Log.d(TAG, "Auth başarılı → Home ekranına geçiliyor")
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            Log.d(TAG, "→ Home ekranı")
            HomeScreen(
                onChatClick = { chatId, chatName ->
                    Log.d(TAG, "Home → Chat: chatId=$chatId, chatName=$chatName")
                    navController.navigate(Screen.Chat.createRoute(chatId, chatName))
                },
                onNavigateToContacts = {
                    Log.d(TAG, "Home → Contacts ekranına geçiliyor")
                    navController.navigate(Screen.Contacts.route)
                },
                onNavigateToCreateGroup = {
                    Log.d(TAG, "Home → CreateGroup ekranına geçiliyor")
                    navController.navigate(Screen.CreateGroup.route)
                },
                onSettingsClick = {
                    Log.d(TAG, "Home → Settings")
                    navController.navigate(Screen.Settings.route)
                },
                onLogout = {
                    Log.d(TAG, "Çıkış yapıldı → Auth ekranına dönülüyor")
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            Log.d(TAG, "→ Settings ekranı")
            SettingsScreen(
                onNavigateBack = {
                    Log.d(TAG, "Settings → geri")
                    navController.popBackStack()
                },
                onNavigateToProfile = {
                    Log.d(TAG, "Settings → Profil")
                    navController.navigate(Screen.MyProfile.route)
                },
                onNavigateToContacts = {
                    Log.d(TAG, "Settings → Kişiler")
                    navController.navigate(Screen.Contacts.route)
                },
                onSecurityAuditClick = {
                    Log.d(TAG, "Settings → Güvenlik Günlüğü")
                    navController.navigate(Screen.SecurityAudit.route)
                },
                onWebPairingClick = {
                    Log.d(TAG, "Settings → Web'e Bağlan")
                    navController.navigate(Screen.WebPairing.route)
                },
                onLogout = {
                    Log.d(TAG, "Settings → Çıkış")
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.MyProfile.route) {
            Log.d(TAG, "→ MyProfile ekranı")
            MyProfileScreen(
                onBackClick = {
                    Log.d(TAG, "MyProfile → geri")
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.WebPairing.route) {
            Log.d(TAG, "→ WebPairing ekranı")
            WebPairingScreen(
                onBackClick = {
                    Log.d(TAG, "WebPairing → geri")
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Contacts.route) {
            Log.d(TAG, "→ Contacts ekranı")
            ContactsScreen(
                onBackClick = {
                    Log.d(TAG, "Contacts → geri (Home)")
                    navController.popBackStack()
                },
                onContactClick = { shadeId, displayName ->
                    Log.d(TAG, "Contacts → Chat: shadeId=$shadeId, name=$displayName")
                    navController.navigate(Screen.Chat.createRoute(shadeId, displayName))
                }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("chatName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            val chatName = backStackEntry.arguments?.getString("chatName")
            Log.d(TAG, "→ Chat ekranı: chatId=$chatId, chatName=$chatName")
            ChatScreen(
                onBackClick = {
                    Log.d(TAG, "Chat → geri")
                    navController.popBackStack()
                },
                onProfileClick = { shadeId ->
                    Log.d(TAG, "Chat → Profile: shadeId=$shadeId")
                    navController.navigate(Screen.Profile.createRoute(shadeId))
                },
                onGroupInfoClick = { groupId ->
                    Log.d(TAG, "Chat → GroupDetail: groupId=$groupId")
                    navController.navigate(Screen.GroupDetail.createRoute(groupId))
                }
            )
        }

        composable(
            route = Screen.Profile.route,
            arguments = listOf(navArgument("shadeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val shadeId = backStackEntry.arguments?.getString("shadeId")
            Log.d(TAG, "→ Profile ekranı: shadeId=$shadeId")
            ProfileScreen(
                onBackClick = {
                    Log.d(TAG, "Profile → geri")
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.SecurityAudit.route) {
            Log.d(TAG, "→ SecurityAudit ekranı")
            SecurityAuditScreen(
                onBackClick = {
                    Log.d(TAG, "SecurityAudit → geri")
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.CreateGroup.route) {
            Log.d(TAG, "→ CreateGroup ekranı")
            CreateGroupScreen(
                onBack = { navController.popBackStack() },
                onGroupCreated = { groupId, groupName ->
                    Log.d(TAG, "Group created: $groupId ($groupName)")
                    navController.navigate(Screen.Chat.createRoute(groupId, groupName)) {
                        popUpTo(Screen.CreateGroup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.GroupDetail.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            Log.d(TAG, "→ GroupDetail ekranı: groupId=$groupId")
            GroupDetailScreen(
                onBack = { navController.popBackStack() },
                onLeft = {
                    Log.d(TAG, "GroupDetail → ayrıldı, Home'a dönülüyor")
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
        }
    }
}

/**
 * Root tespiti uyarı diyalogu.
 * Kullanıcı "Devam Et" seçerse uyarı kapatılır; uygulama çalışmaya devam eder.
 * Güvenlik politikasına göre bu davranış "Çıkış yap" olarak da değiştirilebilir.
 */
@Composable
private fun RootWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* zorunlu kapatmayı engelle */ },
        title = { Text("⚠️ Güvenlik Uyarısı") },
        text = {
            Text(
                "Bu cihaz root'lu veya değiştirilmiş olabilir. " +
                "Şifrelenmiş verilerinizin güvenliği tehlikede olabilir. " +
                "Resmi, güvenli bir cihaz kullanmanız önerilir."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Riski Anlıyorum, Devam Et")
            }
        }
    )
}
