package com.shade.app.ui.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object Settings : Screen("settings")
    object WebPairing : Screen("web_pairing")
    object Contacts : Screen("contacts")
    object Chat : Screen("chat/{chatId}/{chatName}") {
        fun createRoute(chatId: String, chatName: String) = "chat/$chatId/$chatName"
    }
    object Profile : Screen("profile/{shadeId}") {
        fun createRoute(shadeId: String) = "profile/$shadeId"
    }
    object SecurityAudit : Screen("security_audit")
    object Qr : Screen("qr")
    object QrScanner : Screen("qr_scanner")
    object MyProfile : Screen("my_profile")
    object CreateGroup : Screen("create_group")
    object GroupDetail : Screen("group_detail/{groupId}") {
        fun createRoute(groupId: String) = "group_detail/$groupId"
    }
}
