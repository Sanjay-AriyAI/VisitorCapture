package com.example.visitorcapture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

/** Top progress rail: Scan - Details - Customer - Products - Done. A negative [step] hides the track (login screen). */
@Composable
fun ProgressRail(step: Int, labels: List<String>, attendedBy: String?, trailing: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(start = 32.dp, end = 32.dp, top = 10.dp)) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(LineDim.copy(alpha = 0.35f)))
                Box(Modifier.fillMaxWidth(step.toFloat() / last).height(2.dp).background(Amber))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEachIndexed { i, l ->
                    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            if (i == step) Box(Modifier.size(22.dp).clip(CircleShape).background(Amber.copy(alpha = 0.18f)))
                            Box(Modifier.size(12.dp).clip(CircleShape)
                                .background(if (i <= step) Amber else Navy)
                                .border(2.dp, if (i <= step) Amber else LineDim.copy(alpha = 0.6f), CircleShape))
                        }
                        Text(l, fontSize = 12.sp, color = if (i == step) LineC else Steel)
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

@Composable
fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LineC, unfocusedTextColor = LineC, cursorColor = Amber,
    focusedBorderColor = Amber, unfocusedBorderColor = LineC.copy(alpha = 0.25f),
    focusedContainerColor = Panel, unfocusedContainerColor = Panel,
    focusedPlaceholderColor = Steel, unfocusedPlaceholderColor = Steel)

/** The logo artwork is dark on transparent/white, so it always sits on a white plate. */
@Composable
fun NarenLogo(modifier: Modifier = Modifier, compact: Boolean = false) =
    Image(painterResource(R.drawable.naren_logo), "Naren - Empowering quality and trust",
        modifier.clip(RoundedCornerShape(if (compact) 4.dp else 10.dp)).background(Color.White)
            .padding(horizontal = if (compact) 8.dp else 18.dp, vertical = if (compact) 4.dp else 14.dp),
        contentScale = ContentScale.Fit)
