package com.example.visitorcapture

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ---- colour tokens: the historic names now follow the selected theme (see Theme.kt) ----
val Navy: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.bg
val NavyDeep: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.bgDeep
val Panel: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.card
val PanelOn: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.cardActive
val LineC: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.text
val LineDim: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textDim
val Steel: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.muted
val Amber: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accent
val AmberText: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentText
val Danger: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.danger
val Outline: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.outline
val IsDark: Boolean @Composable @ReadOnlyComposable get() = LocalAppColors.current.isDark
val OnAmber = Color(0xFF0B2033)     // text and icons on an amber fill, in both themes
// the Details "paper" card keeps its own fixed palette
val Paper = Color(0xFFF3F1EA)
val PaperLine = Color(0xFFC9C2AE)
val PaperInk = Color(0xFF0B2033)
val PaperHint = Color(0xFF8A5400)   // dark amber, readable on Paper
val Shape6 = RoundedCornerShape(12.dp)
val CardShape = RoundedCornerShape(16.dp)
val Display = Jakarta

/** Hairline border on cards in the light theme, where a flat fill alone would vanish into the background. */
@Composable
fun Modifier.cardBorder(shape: androidx.compose.ui.graphics.Shape = CardShape): Modifier =
    if (IsDark) this else this.border(1.dp, Outline, shape)

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, icon: ImageVector? = null, trailingIcon: ImageVector? = null,
                  onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(onClick, modifier.heightIn(min = 56.dp).pressScale(src), enabled = enabled, shape = RoundedCornerShape(14.dp),
        interactionSource = src, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (IsDark) 0.dp else 2.dp, pressedElevation = 0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber,
            disabledContainerColor = LineC.copy(alpha = 0.08f), disabledContentColor = Steel)) {
        if (icon != null) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (trailingIcon != null) { Spacer(Modifier.width(10.dp)); Icon(trailingIcon, null, Modifier.size(22.dp)) }
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    OutlinedButton(onClick, modifier.heightIn(min = 50.dp).pressScale(src), shape = RoundedCornerShape(14.dp),
        interactionSource = src, border = BorderStroke(1.5.dp, if (IsDark) LineDim.copy(alpha = 0.6f) else Outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LineC,
            containerColor = if (IsDark) Color.Transparent else Panel)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit) =
    TextButton(onClick) { Text(text, color = Steel, fontSize = 14.sp, fontWeight = FontWeight.Medium) }

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
                Text("Visitor Log", color = LineC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (attendedBy != null)
                    Text("Attended by $attendedBy", color = AmberText, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
        AnimatedVisibility(step >= 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            StepTrack(step.coerceAtLeast(0), labels)
        }
    }
}

/** Animated step track: the amber line grows, finished steps get a tick and the current one pulses. */
@Composable
private fun StepTrack(step: Int, labels: List<String>) = Column {
    Spacer(Modifier.height(10.dp))
    val last = (labels.size - 1).coerceAtLeast(1)
    val progress by animateFloatAsState(step.toFloat() / last, tween(450, easing = FastOutSlowInEasing), label = "rail")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.10f, 0.32f,
        infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "halo")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 12.dp)) {
        // Columns shrink below 64 dp so all steps still fit on a narrow phone.
        val colW = minOf(64.dp, maxWidth / labels.size.coerceAtLeast(1))
        Box(Modifier.padding(start = colW / 2, end = colW / 2, top = 11.dp)) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(LineDim.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(progress).height(3.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Amber.copy(alpha = 0.7f), Amber))))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { i, l ->
                val done = i < step
                val fill by animateColorAsState(if (i <= step) Amber else Navy, tween(300), label = "dot")
                val ring by animateColorAsState(if (i <= step) Amber else LineDim.copy(alpha = 0.5f), tween(300), label = "ring")
                Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (i == step) Box(Modifier.size(24.dp).clip(CircleShape).background(Amber.copy(alpha = pulse)))
                        Box(Modifier.size(if (done) 16.dp else 12.dp).clip(CircleShape).background(fill)
                            .border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
                            if (done) Icon(Icons.Default.Check, null, tint = OnAmber, modifier = Modifier.size(11.dp))
                        }
                    }
                    Text(l, fontSize = if (colW < 60.dp) 11.sp else 12.sp,
                        fontWeight = if (i == step) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (i == step) LineC else Steel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

data class TileItem(val label: String, val icon: ImageVector, val selected: Boolean, val badge: String? = null)

/** Selectable "block" tile with LED, like the interests screen of the kiosk */
@Composable
fun Tile(item: TileItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (item.selected) PanelOn else Panel, tween(220), label = "tileBg")
    val border by animateColorAsState(if (item.selected) Amber else if (IsDark) LineC.copy(alpha = 0.14f) else Outline,
        tween(220), label = "tileBorder")
    val iconBg by animateColorAsState(if (item.selected) Amber else LineC.copy(alpha = 0.07f), tween(220), label = "tileIcon")
    Box(
        modifier.heightIn(min = 124.dp).pressScale(src).clip(CardShape).background(bg)
            .border(if (item.selected) 2.dp else 1.dp, border, CardShape)
            .clickable(src, indication = ripple()) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
            }.padding(16.dp)
    ) {
        androidx.compose.animation.AnimatedVisibility(item.selected, Modifier.align(Alignment.TopEnd),
            enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Amber), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = OnAmber, modifier = Modifier.size(15.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(item.icon, null, tint = if (item.selected) OnAmber else LineC, modifier = Modifier.size(26.dp))
            }
            Text(item.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = LineC, lineHeight = 21.sp)
            item.badge?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (item.selected) AmberText else Steel) }
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
fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    FilterChip(selected, { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
        shape = RoundedCornerShape(50),
        leadingIcon = if (selected) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Panel, labelColor = LineC,
            selectedContainerColor = Amber, selectedLabelColor = OnAmber, selectedLeadingIconColor = OnAmber),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected,
            borderColor = if (IsDark) LineDim.copy(alpha = 0.35f) else Outline, selectedBorderColor = Amber))
}

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
                DropdownMenuItem({ Text(o, color = if (o == selected) AmberText else LineC) }, { onSelect(o); open = false })
            }
        }
    }
}

@Composable
fun darkFieldColors(filled: Boolean = true) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LineC, unfocusedTextColor = LineC, cursorColor = Amber,
    focusedBorderColor = Amber, unfocusedBorderColor = if (IsDark) LineC.copy(alpha = 0.22f) else Outline,
    focusedContainerColor = if (filled) Panel else Color.Transparent, unfocusedContainerColor = if (filled) Panel else Color.Transparent,
    focusedPlaceholderColor = Steel, unfocusedPlaceholderColor = Steel,
    focusedLabelColor = AmberText, unfocusedLabelColor = Steel,
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
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = LineC, letterSpacing = (-0.3).sp,
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
    Text(text.uppercase(), fontSize = 12.sp, color = Steel, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))

/** Rounded card that holds a list of setting rows. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().cardBorder().clip(CardShape).background(Panel), content = content)

@Composable
fun GroupDivider() = HorizontalDivider(Modifier.padding(start = 58.dp), color = if (IsDark) LineC.copy(alpha = 0.07f) else Outline)

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null,
               trailing: @Composable () -> Unit = {}) =
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Amber.copy(alpha = if (IsDark) 0.14f else 0.16f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AmberText, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = LineC)
            subtitle?.let { Text(it, fontSize = 13.sp, color = Steel, lineHeight = 18.sp) }
        }
        trailing()
    }

@Composable
fun Chevron() = Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Steel)

@Composable
fun SettingSwitch(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val toggle = { v: Boolean -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(v) }
    SettingRow(icon, title, subtitle, onClick = { toggle(!checked) }) {
        Spacer(Modifier.width(8.dp))
        Switch(checked, toggle, colors = SwitchDefaults.colors(
            checkedThumbColor = OnAmber, checkedTrackColor = Amber, checkedBorderColor = Amber,
            uncheckedThumbColor = Steel, uncheckedTrackColor = if (IsDark) NavyDeep else LineC.copy(alpha = 0.06f),
            uncheckedBorderColor = Steel.copy(alpha = 0.6f)))
    }
}

fun initials(name: String) =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** Round badge with the person's initials, on a soft amber gradient by default. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp, container: Color = Amber, content: Color = OnAmber) {
    val brush = if (container == Amber) Brush.linearGradient(listOf(Color(0xFFF5C46B), Amber, Color(0xFFD9891F)))
        else SolidColor(container)
    Box(Modifier.size(size).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
        Text(initials(name), color = content, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.38f).sp)
    }
}

/** Small rounded label, e.g. "OEM" or "Mail sent" on a visitor card. */
@Composable
fun Pill(text: String, color: Color = Amber, textColor: Color = if (color == Amber) AmberText else color) =
    Text(text, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = if (IsDark) 0.16f else 0.13f))
            .padding(horizontal = 10.dp, vertical = 4.dp))

/** The logo artwork is dark on transparent/white, so it always sits on a white plate. */
@Composable
fun NarenLogo(modifier: Modifier = Modifier, compact: Boolean = false) =
    Image(painterResource(R.drawable.naren_logo), "Naren - Empowering quality and trust",
        modifier.then(if (!IsDark) Modifier.border(1.dp, Outline, RoundedCornerShape(if (compact) 8.dp else 16.dp)) else Modifier)
            .clip(RoundedCornerShape(if (compact) 8.dp else 16.dp)).background(Color.White)
            .padding(horizontal = if (compact) 8.dp else 18.dp, vertical = if (compact) 4.dp else 14.dp),
        contentScale = ContentScale.Fit)

/** Small number tile (Welcome screen): big value and label. */
@Composable
fun StatChip(label: String, value: Int, modifier: Modifier = Modifier, highlight: Boolean = false,
             onClick: (() -> Unit)? = null) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val shown by animateIntAsState(value, tween(600, easing = FastOutSlowInEasing), label = "count")
    Column(modifier.pressScale(src).cardBorder().clip(CardShape)
        .background(if (highlight) Amber.copy(alpha = if (IsDark) 0.16f else 0.12f) else Panel)
        .then(if (onClick != null) Modifier.clickable(src, ripple(), onClick = onClick) else Modifier)
        .padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(shown.toString(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = if (highlight) AmberText else LineC)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Steel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Bottom sheet for forms (edit visitor, edit profile, manual sign-in): title, scrolling fields, sticky Cancel / Save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(title: String, onDismiss: () -> Unit, onSave: () -> Unit, icon: ImageVector? = null,
              saveLabel: String = "Save", saveEnabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { then() } }
    ModalBottomSheet(onDismiss, Modifier.statusBarsPadding(), sheetState = state, containerColor = NavyDeep, contentColor = LineC,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Amber.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center) { Icon(icon, null, tint = AmberText, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, color = LineC)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Cancel", Modifier.weight(1f)) { close(onDismiss) }
                PrimaryButton(saveLabel, saveEnabled, Modifier.weight(1f)) { close(onSave) }
            }
        }
    }
}

// ---------------------------------------------------------------- Developer branding

const val DEVELOPER_BRAND = "AriyAI Tech"

/** "AriyAI Tech™": brand in bold accent, ™ set as a small superscript (™, not ®: the mark is not registered). */
@Composable
private fun brandCredit(prefix: String, brandSize: androidx.compose.ui.unit.TextUnit) =
    androidx.compose.ui.text.buildAnnotatedString {
        append(prefix)
        withStyle(androidx.compose.ui.text.SpanStyle(color = AmberText, fontWeight = FontWeight.Bold, fontSize = brandSize)) {
            append(DEVELOPER_BRAND)
        }
        withStyle(androidx.compose.ui.text.SpanStyle(color = AmberText, fontSize = brandSize * 0.6f,
            baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript)) { append("™") }
    }

/** Slim footer shown at the bottom of every page: "© 2026 · Developed & Powered by AriyAI Tech™". */
@Composable
fun BrandFooter(modifier: Modifier = Modifier) {
    val year = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    Column(modifier.fillMaxWidth().background(NavyDeep.copy(alpha = if (IsDark) 0.45f else 0.06f))) {
        HorizontalDivider(color = if (IsDark) LineC.copy(alpha = 0.08f) else Outline)
        Text(brandCredit("© $year  ·  Developed & Powered by ", 11.sp), fontSize = 11.sp, color = Steel,
            letterSpacing = 0.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** Fuller credit block for the About page. */
@Composable
fun BrandCreditBlock(modifier: Modifier = Modifier) {
    val year = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("DESIGNED, DEVELOPED & POWERED BY", fontSize = 10.sp, letterSpacing = 1.5.sp, color = Steel, fontWeight = FontWeight.SemiBold)
        Text(brandCredit("", 20.sp), fontSize = 20.sp, fontFamily = Display)
        Text("© $year $DEVELOPER_BRAND. All rights reserved.", fontSize = 12.sp, color = Steel)
        Text("$DEVELOPER_BRAND™ is a trademark of $DEVELOPER_BRAND.", fontSize = 11.sp, color = Steel.copy(alpha = 0.8f))
    }
}
