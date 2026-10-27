package com.example.visitorcapture

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Drill-down for one product: tap a machine to expand, tick any number of makes / models / retrofit kits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogScreen(
    product: String, selected: Set<String>,
    onToggle: (String) -> Unit, onSetMany: (List<String>, Boolean) -> Unit, onDone: () -> Unit
) {
    val machines = Catalog.machinesFor(product)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(product, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = Display)
            Text("Tap a machine, then tick every make or model you deal with.", color = Steel)
            machines.forEach { m ->
                val all = Catalog.paths(product, m)
                val n = all.count { it in selected }
                val isOpen = open == m.name
                Column(
                    Modifier.fillMaxWidth().clip(Shape6)
                        .background(if (n > 0) PanelOn else Panel)
                        .border(1.5.dp, if (n > 0) Amber else LineC.copy(alpha = 0.18f), Shape6)
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable { open = if (isOpen) null else m.name }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(m.name, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = Display)
                        if (n > 0) {
                            Text("$n", color = NavyDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Amber).padding(horizontal = 9.dp, vertical = 2.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Steel)
                    }
                    if (isOpen) Column(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        m.sections.forEach { s ->
                            s.title?.let { Text(it.uppercase(), fontSize = 11.sp, color = Steel, letterSpacing = 1.sp) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                s.items.forEach { item ->
                                    val p = Catalog.path(product, m, s, item)
                                    OptionChip(item, p in selected) { onToggle(p) }
                                }
                            }
                        }
                        LinkButton(if (n == all.size) "Clear all for ${m.name}" else "Select everything for ${m.name}") {
                            onSetMany(all, n != all.size)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(NavyDeep).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${selected.count { it.startsWith("$product > ") }} selected", color = Amber, fontWeight = FontWeight.SemiBold)
            PrimaryButton("Done") { onDone() }
        }
    }
}
