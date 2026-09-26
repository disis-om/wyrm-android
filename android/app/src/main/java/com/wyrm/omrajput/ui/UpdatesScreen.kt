package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.UpdateState

/**
 * Check for updates.
 *
 * The updater itself is untouched — the same signed manifest, the same
 * verified download. This is only the face of it, and it says exactly one
 * thing at a time: what version you are on, what the updater is doing, and the
 * single action that makes sense right now.
 */
@Composable
fun UpdatesScreen(
    state: UpdateState,
    installedVersion: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstalledNotes: () -> Unit,
    onOpenAvailableNotes: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = insetTop)
                .height(52.dp)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = "‹ Back",
                fontFamily = Wyrm.Body,
                fontSize = 16.sp,
                color = Wyrm.Link,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
            Text(
                text = "Updates",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Wyrm.Ink,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = insetBottom),
        ) {
                Spacer(Modifier.height(22.dp))

                Text("INSTALLED", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Wyrm.Quiet)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = installedVersion,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    color = Wyrm.Ink,
                )

                Spacer(Modifier.height(Wyrm.GapLarge))
                WyrmRule()
                Spacer(Modifier.height(Wyrm.Gap))

                Text(
                    text = state.title.ifEmpty { "Nothing checked yet" },
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (state.failed) Wyrm.Badge else Wyrm.Ink,
                )
                if (state.detail.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.detail,
                        fontFamily = Wyrm.Body,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = Wyrm.Quiet,
                    )
                }
                if (state.version.isNotEmpty() && state.available) {
                    Spacer(Modifier.height(10.dp))
                    WyrmLabel("Available: ${state.version}")
                }

                if (state.busy) {
                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(wyrmRounded(999.dp))
                            .background(Wyrm.Line),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((state.progress / 100f).coerceIn(0f, 1f))
                                .height(3.dp)
                                .clip(wyrmRounded(999.dp))
                                .background(Wyrm.Ink)
                        )
                    }
                }

                Spacer(Modifier.height(Wyrm.GapLarge))

                WyrmPrimaryAction(
                    label = when {
                        state.busy -> "Working…"
                        state.available -> "Download and install"
                        else -> "Check now"
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !state.busy,
                    onClick = if (state.available) onInstall else onCheck,
                )

                if (state.available) {
                    Spacer(Modifier.height(12.dp))
                    OutlineAction(label = "Check again", onClick = onCheck)
                }

                Spacer(Modifier.height(Wyrm.GapLarge))
                WyrmRule()
                Spacer(Modifier.height(Wyrm.Gap))
                WyrmLabel("RELEASE NOTES")
                Spacer(Modifier.height(12.dp))
                OutlineAction(
                    label = "What's in $installedVersion",
                    onClick = onOpenInstalledNotes,
                )
                if (state.available && state.version.isNotBlank() && state.version != installedVersion) {
                    Spacer(Modifier.height(10.dp))
                    OutlineAction(
                        label = "What's new in ${state.version}",
                        onClick = onOpenAvailableNotes,
                    )
                }

                Spacer(Modifier.height(Wyrm.GapLarge))
                Text(
                    text = "Updates are signed and verified before anything is installed. " +
                        "Your settings and skins are backed up first.",
                    fontFamily = Wyrm.Body,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = Wyrm.Quiet,
                )
                Spacer(Modifier.height(Wyrm.GapLarge))
        }
    }
}
