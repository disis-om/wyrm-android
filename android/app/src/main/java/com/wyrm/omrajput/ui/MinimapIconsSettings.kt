package com.wyrm.omrajput.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.i18n.Text
import com.wyrm.omrajput.data.MinimapIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings › Display › Minimap icons (OM, 2026-11-07): the icon NTL
 * teammates see for you (NTL's own icons) and the one Wyrm friends see
 * (Wyrm's own). Your own place on the map keeps its green arrow.
 */
@Composable
internal fun MinimapIconsSection() {
    val context = LocalContext.current
    val ready by produceState(false) { value = withContext(Dispatchers.IO) { WyrmExtras.load(context) } }
    var picking by remember { mutableStateOf<String?>(null) }
    SettingsSectionLabel("Minimap icons")
    SettingsCard {
        IconRow("NTL team", "What NTL teammates see for you on their map.",
            if (ready) WyrmExtras.ntlIcon(MinimapIcons.ntl) else null, first = true) { picking = "ntl" }
        IconRow("Wyrm friends", "What Wyrm friends see for you on their map. Friends are people you follow who follow you back.",
            if (ready) WyrmExtras.wyrmIcon(MinimapIcons.wyrm) else null, first = false) { picking = "wyrm" }
    }
    SettingsCaption("Your own place keeps the green arrow. Friends and teammates in your arena show with the icon they chose.")
    when (picking) {
        "ntl" -> IconPicker("NTL team icon", WyrmExtras.ntlChoices, MinimapIcons.ntl,
            image = { WyrmExtras.ntlIcon(it) }, onPick = { MinimapIcons.applyNtl(it); picking = null },
            onDismiss = { picking = null })
        "wyrm" -> IconPicker("Wyrm friends icon", (0 until WyrmExtras.wyrmCount).toList(), MinimapIcons.wyrm,
            image = { WyrmExtras.wyrmIcon(it) }, onPick = { MinimapIcons.applyWyrm(it); picking = null },
            onDismiss = { picking = null })
    }
}

@Composable
private fun IconRow(title: String, detail: String, icon: ImageBitmap?, first: Boolean, onOpen: () -> Unit) {
    val palette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    Column {
        if (!first) SettingsHairline()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = palette.ink)
                Text(detail, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = palette.quiet)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).background(palette.ink.copy(alpha = .08f)),
                contentAlignment = Alignment.Center) {
                if (icon != null) Image(icon, title, Modifier.size(28.dp), filterQuality = FilterQuality.Low)
            }
            Spacer(Modifier.width(6.dp))
            Text("›", fontFamily = Wyrm.Body, fontSize = 17.sp, color = palette.chevron)
        }
    }
}

@Composable
private fun IconPicker(
    title: String, choices: List<Int>, chosen: Int, image: (Int) -> ImageBitmap?,
    onPick: (Int) -> Unit, onDismiss: () -> Unit,
) {
    val palette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    CentredDialog(onDismiss = onDismiss) {
        Column {
            Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = palette.ink)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(GridCells.Adaptive(46.dp), Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(choices, key = { it }) { value ->
                    val selected = value == chosen
                    Box(
                        Modifier.padding(3.dp).size(42.dp).clip(CircleShape)
                            .background(if (selected) palette.ink.copy(alpha = .12f) else palette.ink.copy(alpha = .04f))
                            .then(if (selected) Modifier.border(2.dp, palette.ink, CircleShape) else Modifier)
                            .clickable { onPick(value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        image(value)?.let { Image(it, null, Modifier.size(28.dp), filterQuality = FilterQuality.Low) }
                    }
                }
            }
        }
    }
}
