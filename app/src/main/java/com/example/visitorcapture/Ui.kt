package com.example.visitorcapture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF123049)
val NavyDeep = Color(0xFF0B2033)
val Panel = Color(0xFF173A58)
val PanelOn = Color(0xFF1D4363)
val LineC = Color(0xFFEAF2F8)
val LineDim = Color(0xFFB9CBDA)
val Steel = Color(0xFF7E96A8)
val Amber = Color(0xFFE8A33D)
val Paper = Color(0xFFF3F1EA)
val PaperLine = Color(0xFFC9C2AE)
val PaperInk = Color(0xFF0B2033)
val PaperHint = Color(0xFF8A5400)   // dark amber, readable on Paper
val Danger = Color(0xFFC0563F)
val Shape6 = RoundedCornerShape(6.dp)
val Display = FontFamily.SansSerif

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Button(onClick, modifier.heightIn(min = 56.dp), enabled = enabled, shape = Shape6,
        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = NavyDeep,
            disabledContainerColor = Amber.copy(alpha = 0.3f), disabledContentColor = NavyDeep.copy(alpha = 0.6f))) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), shape = Shape6,
        border = BorderStroke(1.5.dp, LineDim),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LineDim)) { Text(text, fontSize = 15.sp) }

@Composable
fun LinkButton(text: String, onClick: () -> Unit) =
    TextButton(onClick) { Text(text, color = Steel, fontSize = 14.sp) }

/** Top progress rail: Scan - Details - Customer - Products - Outcome - Done. A negative [step] hides the track (login screen). */
@Composable
fun ProgressRail(step: Int, labels: List<String>, attendedBy: String?,
                 leading: @Composable () -> Unit = {}, trailing: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(4.dp))
            NarenLogo(Modifier.height(34.dp), compact = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Visitor Log", color = Steel, fontSize = 15.sp)
                if (attendedBy != null)
                    Text("Attended by $attendedBy", color = Amber, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
        if (step >= 0) Column {
        Spacer(Modifier.height(6.dp))
        val last = (labels.size - 1).coerceAtLeast(1)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 12.dp)) {
            // Columns shrink below 64 dp so all steps still fit on a narrow phone.
            val colW = minOf(64.dp, maxWidth / labels.size.coerceAtLeast(1))
            Box(Modifier.padding(start = colW / 2, end = colW / 2, top = 10.dp)) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(LineDim.copy(alpha = 0.35f)))
                Box(Modifier.fillMaxWidth(step.toFloat() / last).height(2.dp).background(Amber))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEachIndexed { i, l ->
                    Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            if (i == step) Box(Modifier.size(22.dp).clip(CircleShape).background(Amber.copy(alpha = 0.18f)))
                            Box(Modifier.size(12.dp).clip(CircleShape)
                                .background(if (i <= step) Amber else Navy)
                                .border(2.dp, if (i <= step) Amber else LineDim.copy(alpha = 0.6f), CircleShape))
                        }
                        Text(l, fontSize = if (colW < 60.dp) 11.sp else 12.sp, color = if (i == step) LineC else Steel,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        }
    }
}

data class TileItem(val label: String, val icon: ImageVector, val selected: Boolean, val badge: String? = null)

/** Selectable "block" tile with LED, like the interests screen of the kiosk */
@Composable
fun Tile(item: TileItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 120.dp).clip(Shape6)
            .background(if (item.selected) PanelOn else Panel)
            .border(1.5.dp, if (item.selected) Amber else LineC.copy(alpha = 0.18f), Shape6)
            .clickable(onClick = onClick).padding(18.dp)
    ) {
        Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape)
            .background(if (item.selected) Amber else LineC.copy(alpha = 0.25f)))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(item.icon, null, tint = LineC, modifier = Modifier.size(34.dp))
            Text(item.label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = Display, color = LineC)
            item.badge?.let { Text(it, fontSize = 12.sp, color = if (item.selected) Amber else Steel) }
        }
    }
}

@Composable
fun TileGrid(items: List<TileItem>, onTap: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { t -> Tile(t, Modifier.weight(1f)) { onTap(t.label) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) =
    FilterChip(selected, onClick, label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Panel, labelColor = LineC,
            selectedContainerColor = Amber, selectedLabelColor = NavyDeep, selectedLeadingIconColor = NavyDeep),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected,
            borderColor = LineDim.copy(alpha = 0.4f), selectedBorderColor = Amber))

/** Read-only dropdown on the dark background. Shows [placeholder] until an option is picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(placeholder: String, options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(selected.orEmpty(), {}, readOnly = true, singleLine = true,
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), colors = darkFieldColors())
        ExposedDropdownMenu(open, { open = false }, containerColor = PanelOn) {
            options.forEach { o ->
                DropdownMenuItem({ Text(o, color = if (o == selected) Amber else LineC) }, { onSelect(o); open = false })
            }
        }
    }
}

@Composable
fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LineC, unfocusedTextColor = LineC, cursorColor = Amber,
    focusedBorderColor = Amber, unfocusedBorderColor = LineC.copy(alpha = 0.25f),
    focusedContainerColor = Panel, unfocusedContainerColor = Panel,
    focusedPlaceholderColor = Steel, unfocusedPlaceholderColor = Steel,
    focusedLabelColor = Amber, unfocusedLabelColor = Steel,
    focusedSupportingTextColor = Steel, unfocusedSupportingTextColor = Steel,
    focusedLeadingIconColor = LineDim, unfocusedLeadingIconColor = Steel,
    focusedTrailingIconColor = LineDim, unfocusedTrailingIconColor = Steel)

// ---- building blocks for the pages opened from the side menu and the gear ----

/** Slim top bar with a back arrow, used by every page outside the visit flow. */
@Composable
fun SectionTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = LineC) }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = LineC,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
        actions()
    }

/** Scrollable page body under a [SectionTopBar]. */
@Composable
fun SectionPage(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {},
                content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize()) {
        SectionTopBar(title, onBack, actions)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp), content = content)
    }

/** Small caps label above a group of settings. */
@Composable
fun SectionHeader(text: String) =
    Text(text.uppercase(), fontSize = 12.sp, color = Steel, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp))

/** Rounded card that holds a list of setting rows. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel), content = content)

@Composable
fun GroupDivider() = HorizontalDivider(Modifier.padding(start = 54.dp), color = LineC.copy(alpha = 0.08f))

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null,
               trailing: @Composable () -> Unit = {}) =
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LineDim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = LineC)
            subtitle?.let { Text(it, fontSize = 13.sp, color = Steel) }
        }
        trailing()
    }

@Composable
fun Chevron() = Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Steel)

@Composable
fun SettingSwitch(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) =
    SettingRow(icon, title, subtitle, onClick = { onChange(!checked) }) {
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedThumbColor = NavyDeep, checkedTrackColor = Amber, checkedBorderColor = Amber,
            uncheckedThumbColor = LineDim, uncheckedTrackColor = NavyDeep, uncheckedBorderColor = LineDim.copy(alpha = 0.5f)))
    }

fun initials(name: String) =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** Round badge with the person's initials. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp, container: Color = Amber, content: Color = NavyDeep) =
    Box(Modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Text(initials(name), color = content, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
    }

/** Small rounded label, e.g. "OEM" or "Mail sent" on a visitor card. */
@Composable
fun Pill(text: String, color: Color = Amber, textColor: Color = color) =
    Text(text, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 3.dp))

/** The logo artwork is dark on transparent/white, so it always sits on a white plate. */
@Composable
fun NarenLogo(modifier: Modifier = Modifier, compact: Boolean = false) =
    Image(painterResource(R.drawable.naren_logo), "Naren - Empowering quality and trust",
        modifier.clip(RoundedCornerShape(if (compact) 4.dp else 10.dp)).background(Color.White)
            .padding(horizontal = if (compact) 8.dp else 18.dp, vertical = if (compact) 4.dp else 14.dp),
        contentScale = ContentScale.Fit)
