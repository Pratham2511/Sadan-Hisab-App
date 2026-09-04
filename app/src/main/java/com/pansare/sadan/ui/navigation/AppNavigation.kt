package com.pansare.sadan.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** The five primary areas, each with a real Material icon — never a bare letter. */
enum class TopLevel(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    DASHBOARD("dashboard", "Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    ROOMS("rooms", "Rooms", Icons.Filled.MeetingRoom, Icons.Outlined.MeetingRoom),
    PAYMENTS("payments", "Payments", Icons.Filled.Payments, Icons.Outlined.Payments),
    REPORTS("reports", "Reports", Icons.Filled.Assessment, Icons.Outlined.Assessment),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

/** Secondary destinations. Arguments are typed and built through helper functions. */
object Routes {
    const val ADD_TENANT = "add_tenant?roomId={roomId}"
    const val TENANT_PROFILE = "tenant/{tenantId}"
    const val EDIT_TENANT = "tenant/{tenantId}/edit"
    const val LEDGER = "tenant/{tenantId}/ledger"
    const val RECORD_PAYMENT = "tenant/{tenantId}/pay"
    const val EDIT_PAYMENT = "payment/{paymentId}/edit"
    const val DEFAULTERS = "defaulters"
    const val ISSUES = "issues"
    const val IMPORT = "import"

    fun addTenant(roomId: Long = 0L) = "add_tenant?roomId=$roomId"
    fun tenantProfile(id: Long) = "tenant/$id"
    fun editTenant(id: Long) = "tenant/$id/edit"
    fun ledger(id: Long) = "tenant/$id/ledger"
    fun recordPayment(id: Long) = "tenant/$id/pay"
    fun editPayment(id: Long) = "payment/$id/edit"
}
