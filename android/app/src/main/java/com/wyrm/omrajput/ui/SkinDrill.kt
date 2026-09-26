package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SkinDrillScaffold(
    title: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    trailing: String? = null,
    trailingEnabled: Boolean = true,
    onTrailing: (() -> Unit)? = null,
    wear: Boolean = true,
    onWear: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Wyrm.Paper.copy(alpha = 0.94f))
                .padding(top = insetTop)
                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                Text(
                    text = "‹ Skin",
                    fontFamily = Wyrm.Body,
                    fontSize = 16.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
                Text(
                    text = title,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (trailing != null && onTrailing != null) {
                    Text(
                        text = trailing,
                        fontFamily = Wyrm.Body,
                        fontSize = 15.5.sp,
                        color = if (trailingEnabled) Wyrm.Link else Wyrm.TabIdle,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable(enabled = trailingEnabled, onClick = onTrailing)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Wyrm.Rule),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
            SpacerBottom(if (wear) 12.dp else 24.dp)
        }
        if (wear) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Wyrm.Paper.copy(alpha = 0.94f)),
            ) {
                WearButton(onClick = onWear)
                Box(Modifier.height(insetBottom.coerceAtLeast(12.dp) + 8.dp))
            }
        }
    }
}

@Composable
internal fun SkinSectionLabel(text: String, top: Dp = 22.dp) {
    Text(
        text = text.uppercase(),
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        letterSpacing = 0.92.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = top, bottom = 8.dp),
    )
}

@Composable
internal fun SkinCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = wyrmRounded(14.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape),
        content = content,
    )
}

@Composable
private fun SpacerBottom(height: Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(height))
}
