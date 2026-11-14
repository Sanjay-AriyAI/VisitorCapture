package com.example.visitorcapture

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pages reachable from the side menu (top left) and the gear (top right). [Visit] is the visitor flow itself. */
enum class AppPage { Visit, Profile, Logs, Mail, Settings, About }

/** Side menu that slides in from the left: who is signed in, then the app's pages. */
@Composable
fun AppDrawer(
    current: AppPage, employee: EmployeeInfo?, deviceName: String,
    visitorCount: Int, mailPending: Int, mailConfigured: Boolean, visitInProgress: Boolean,
    onSelect: (AppPage) -> Unit, onNewVisit: () -> Unit, onSwitchUser: () -> Unit
) {
    ModalDrawerSheet(Modifier.width(300.dp), drawerContainerColor = NavyDeep, drawerContentColor = LineC) {
        Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 16.dp)) {
            // header: brand + signed-in staff member
            Column(Modifier.padding(horizontal = 12.dp)) {
                NarenLogo(Modifier.height(38.dp), compact = true)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(employee?.name.orEmpty())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(employee?.name ?: "Not signed in", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            color = LineC, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(employee?.title?.takeIf { it.isNotBlank() }).joinToString()
                        if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = Amber, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text("Device · $deviceName", fontSize = 12.sp, color = Steel, modifier = Modifier.padding(top = 10.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = LineC.copy(alpha = 0.1f))

            DrawerItem(Icons.Default.Home, "Home", current == AppPage.Visit,
                badge = if (visitInProgress) "In progress" else null) { onSelect(AppPage.Visit) }
            if (visitInProgress) DrawerItem(Icons.Default.Add, "Start new visit", false) { onNewVisit() }
            DrawerItem(Icons.Default.Person, "Profile", current == AppPage.Profile) { onSelect(AppPage.Profile) }
            DrawerItem(Icons.Default.Groups, "Visitor logs", current == AppPage.Logs,
                badge = visitorCount.toString()) { onSelect(AppPage.Logs) }
            DrawerItem(Icons.Default.Email, "Mail settings", current == AppPage.Mail,
                badge = when { !mailConfigured -> "Set up"; mailPending > 0 -> "$mailPending to send"; else -> null },
                badgeColor = if (!mailConfigured) Danger else Amber) { onSelect(AppPage.Mail) }
            DrawerItem(Icons.Default.Settings, "Settings", current == AppPage.Settings) { onSelect(AppPage.Settings) }
            DrawerItem(Icons.Default.Info, "About", current == AppPage.About) { onSelect(AppPage.About) }

            Spacer(Modifier.weight(1f))
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = LineC.copy(alpha = 0.1f))
            DrawerItem(Icons.Default.SwapHoriz, "Switch user", false) { onSwitchUser() }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, selected: Boolean, badge: String? = null,
                       badgeColor: Color = Steel, onClick: () -> Unit) =
    NavigationDrawerItem(
        label = { Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        selected = selected, onClick = onClick,
        icon = { Icon(icon, null) },
        badge = badge?.let { { Pill(it, color = if (selected) Amber else badgeColor) } },
        modifier = Modifier.padding(vertical = 2.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = PanelOn, unselectedContainerColor = Color.Transparent,
            selectedIconColor = Amber, unselectedIconColor = LineDim,
            selectedTextColor = LineC, unselectedTextColor = LineDim))
