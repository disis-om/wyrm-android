package com.wyrm.omrajput.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.wyrm.omrajput.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Wyrm iOS's shared screen pieces (WyrmDesignComponents.swift), one for one.
 * Sizes are the SwiftUI points as dp, tracking as sp, and every corner is
 * continuous, so a screen built from these reads the same on both phones.
 */

/** `WyrmScreenHeader`: uppercase kicker over a 28 bold title, optional trailing. */
@Composable
fun IosScreenHeader(kicker: String, title: String, trailing: (@Composable () -> Unit)? = null) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(wyrmScopedPalette.paper)
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(kicker.uppercase(), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
                letterSpacing = 1.15.sp, color = wyrmScopedPalette.quiet)
            Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 28.sp,
                letterSpacing = (-0.55).sp, color = wyrmScopedPalette.ink)
        }
        Spacer(Modifier.width(8.dp))
        trailing?.invoke()
    }
}

/** `WyrmSectionLabel`: 10.5 semibold, tracked, quiet. */
@Composable
fun IosSectionLabel(text: String) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Text(
        text = text.uppercase(),
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 0.9.sp,
        color = wyrmScopedPalette.quiet,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** Opaque paper card with clipped corners and a quiet rule; no elevation glow. */
@Composable
fun IosPaperCard(content: @Composable ColumnScope.() -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val shape = wyrmRounded(17.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            
            .clip(shape)
            .background(wyrmScopedPalette.card)
            .border(1.dp, wyrmScopedPalette.rule, shape),
        content = content,
    )
}

/** `WyrmListRow`: tinted glyph well, title, detail, value and a chevron. */
@Composable
fun IosListRow(
    title: String,
    detail: String = "",
    value: String = "",
    glyph: IosGlyph? = null,
    tint: Color = Wyrm.Mute,
    destructive: Boolean = false,
    showsChevron: Boolean = true,
    /** Something new behind this row (the badge trail, OM 2026-10-01): a red count. */
    badge: Int = 0,
    onClick: (() -> Unit)? = null,
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (detail.isEmpty()) 52.dp else 58.dp)
                .scale(pressScale(pressed, onClick != null))
                .clickable(interactionSource = interaction, indication = null, enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                Box(
                    Modifier.size(30.dp).clip(wyrmRounded(8.dp)).background(tint.copy(alpha = 0.09f)),
                    contentAlignment = Alignment.Center,
                ) { IosIcon(glyph, tint, size = 17.dp) }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontFamily = Wyrm.Body, fontSize = 15.5.sp,
                    color = if (destructive) Color(0xFFFF3B30) else wyrmScopedPalette.ink)
                if (detail.isNotEmpty()) {
                    Text(detail, fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = wyrmScopedPalette.quiet,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (value.isNotEmpty()) {
                Text(value, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = wyrmScopedPalette.quiet,
                    textAlign = TextAlign.End, maxLines = 2)
                Spacer(Modifier.width(12.dp))
            }
            if (badge > 0) {
                WyrmCountBadge(badge)
                Spacer(Modifier.width(10.dp))
            }
            if (showsChevron && onClick != null) IosIcon(IosGlyph.CHEVRON_RIGHT, wyrmScopedPalette.chevron, size = 12.dp, semibold = true)
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = if (glyph == null) 14.dp else 56.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(wyrmScopedPalette.rowRule),
        )
    }
}

/**
 * The badge trail (OM, 2026-10-01): the same red count on the tab, on the row
 * that leads to the thing, and on the thing itself, so a player can follow it.
 */
@Composable
internal fun WyrmCountBadge(count: Int, modifier: Modifier = Modifier) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    if (count <= 0) return
    Box(
        modifier
            .heightIn(min = 20.dp)
            .widthIn(min = 20.dp)
            .clip(WyrmCapsule)
            .background(wyrmScopedPalette.badge)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            fontFamily = Wyrm.Body,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = 11.5.sp,
            color = Wyrm.contentOn(wyrmScopedPalette.badge),
            maxLines = 1,
        )
    }
}

/** `WyrmLoadoutRow`: a 26 dp well, title, value and the "›" chevron. */
@Composable
fun IosLoadoutRow(
    title: String,
    value: String,
    first: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onOpen: () -> Unit,
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(wyrmScopedPalette.rowRule))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .scale(if (pressed) 0.975f else 1f)
                .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Text(title, fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = wyrmScopedPalette.ink, maxLines = 1,
                modifier = Modifier.weight(1f))
            if (value.isNotEmpty()) {
                Text(value, fontFamily = Wyrm.Body, fontSize = 14.sp, color = wyrmScopedPalette.quiet, maxLines = 1)
                Spacer(Modifier.width(6.dp))
            }
            Text("›", fontFamily = Wyrm.Body, fontSize = 17.sp, color = wyrmScopedPalette.chevron)
        }
    }
}

/** `WyrmLoadoutIcon`: a glyph in a 26 dp well. */
@Composable
fun IosLoadoutIcon(glyph: IosGlyph) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Box(Modifier.size(26.dp).clip(wyrmRounded(8.dp)).background(wyrmScopedPalette.well), contentAlignment = Alignment.Center) {
        IosIcon(glyph, wyrmScopedPalette.mute, size = 15.dp, semibold = true)
    }
}

/** `WyrmFoodWell`: three pellets in a well. */
@Composable
fun IosFoodWell() {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Canvas(Modifier.size(26.dp).clip(wyrmRounded(8.dp)).background(wyrmScopedPalette.well)) {
        val r = size.minDimension * 0.115f
        drawCircle(wyrmScopedPalette.live, r, Offset(size.width * 0.32f, size.height * 0.38f))
        drawCircle(wyrmScopedPalette.link, r * 0.82f, Offset(size.width * 0.68f, size.height * 0.34f))
        drawCircle(Color(0xFFD38B5D), r * 1.08f, Offset(size.width * 0.55f, size.height * 0.69f))
    }
}

/** `WyrmMetric`: tracked label over a 17 bold value. */
@Composable
fun RowScope.IosMetric(label: String, value: String) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Column(
        Modifier.weight(1f).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 9.5.sp,
            letterSpacing = 0.8.sp, color = wyrmScopedPalette.quiet)
        Text(value, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = wyrmScopedPalette.ink)
    }
}

/** `WyrmEmptyPanel`: a centred title and note. */
@Composable
fun IosEmptyPanel(title: String, note: String) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = wyrmScopedPalette.ink)
        Text(note, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = wyrmScopedPalette.quiet, textAlign = TextAlign.Center)
    }
}

/** `WyrmDetailChrome`: "‹ Back", a centred title, an optional action, a rule. */
@Composable
fun IosDetailChrome(
    title: String,
    insetTop: Dp,
    onBack: () -> Unit,
    actionTitle: String = "",
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Column(Modifier.background(wyrmScopedPalette.paper).padding(top = insetTop)) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 18.dp)) {
            Row(
                Modifier.align(Alignment.CenterStart).clickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                IosIcon(IosGlyph.CHEVRON_LEFT, wyrmScopedPalette.link, size = 16.dp, semibold = true)
                Text("Back", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = wyrmScopedPalette.link)
            }
            Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = wyrmScopedPalette.ink,
                modifier = Modifier.align(Alignment.Center))
            if (actionTitle.isNotEmpty()) {
                Text(actionTitle, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = wyrmScopedPalette.link, modifier = Modifier.align(Alignment.CenterEnd).clickable { onAction?.invoke() })
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(wyrmScopedPalette.rule))
        content()
    }
}
