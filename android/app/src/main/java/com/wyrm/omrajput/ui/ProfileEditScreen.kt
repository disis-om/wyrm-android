package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.RenameAllowance

/** The avatars the server will accept, matching avatarKeys in its config. */
val AVATAR_KEYS = listOf(
    "mono-ink", "pastel-mint", "pastel-sky",
    "pastel-peach", "pastel-lilac", "pastel-lemon",
)


/**
 * Spec page 22 — Profile › Edit.
 *
 * Limits next to the field. Remaining name/username changes come from
 * `/v1/me/renames`, not a mock. Save stays off until something actually changes.
 */
@Composable
fun ProfileEditScreen(
    profile: WyrmProfile,
    busy: Boolean,
    error: String,
    allowance: RenameAllowance? = null,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: (displayName: String, username: String, bio: String, avatarKey: String) -> Unit,
) {
    var displayName by remember(profile.id) { mutableStateOf(profile.displayName) }
    var username by remember(profile.id) { mutableStateOf(profile.username) }
    var bio by remember(profile.id) { mutableStateOf(profile.bio) }
    var avatarKey by remember(profile.id) { mutableStateOf(profile.avatarKey) }

    val usernameValid = username.length in 3..20 && username.all { it.isLetterOrDigit() || it == '_' }
    val nameValid = displayName.trim().length >= 2
    val nameChanged = displayName.trim() != profile.displayName
    val userChanged = username != profile.username
    val nameAllowed = !nameChanged || allowance == null || allowance.displayName > 0
    val userAllowed = !userChanged || allowance == null || allowance.username > 0
    val changed = nameChanged || userChanged || bio != profile.bio || avatarKey != profile.avatarKey
    val canSave = changed && usernameValid && nameValid && nameAllowed && userAllowed && !busy
    val bottom = keyboardRoom(insetBottom)

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
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                Text(
                    text = "Cancel",
                    fontFamily = Wyrm.Body,
                    fontSize = 16.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
                Text(
                    text = "Edit profile",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.align(Alignment.Center),
                )
                Text(
                    text = if (busy) "Saving…" else "Save",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.5.sp,
                    color = if (canSave) Wyrm.Link else Wyrm.TabIdle,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable(enabled = canSave) {
                            onSave(displayName.trim(), username, bio.trim(), avatarKey)
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsCard {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WyrmAvatar(
                        url = profile.avatarUrl,
                        avatarKey = avatarKey,
                        initial = displayName.ifEmpty { profile.displayName },
                        size = 60.dp,
                        corner = 17.dp,
                    )
                    Spacer(Modifier.padding(start = 14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperOutlineButton(
                            label = if (profile.avatarUrl.isEmpty()) "Choose photo" else "Change photo",
                            onClick = onPickPhoto,
                        )
                        if (profile.avatarUrl.isNotEmpty()) {
                            PaperDangerButtonSmall(label = "Remove", onClick = onRemovePhoto)
                        }
                    }
                }
            }
            SettingsCaption("PNG or JPG. Shown on your profile and the leaderboard.")

            if (profile.avatarUrl.isEmpty()) {
                SettingsSectionLabel("Or a seal")
                SettingsCard {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AVATAR_KEYS.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(wyrmRounded(10.dp))
                                    .background(avatarBrushFor(key))
                                    .border(
                                        width = if (key == avatarKey) 2.dp else 1.dp,
                                        color = if (key == avatarKey) Wyrm.Ink else Wyrm.Rule,
                                        shape = wyrmRounded(10.dp),
                                    )
                                    .clickable { avatarKey = key },
                            )
                        }
                    }
                }
            }

            SettingsSectionLabel("Identity")
            SettingsCard {
                EditField(
                    caption = "Display name",
                    limit = allowanceLine(allowance?.displayName, "Twice a month"),
                    value = displayName,
                    onValueChange = { displayName = it.take(40) },
                    first = true,
                )
                EditField(
                    caption = "Username",
                    limit = allowanceLine(allowance?.username, "Twice a month"),
                    value = username,
                    onValueChange = { typed ->
                        username = typed.filter { it.isLetterOrDigit() || it == '_' }.take(20)
                    },
                    first = false,
                    prefix = "@",
                    hint = "Your in-game name lives on Play and changes freely.",
                )
                EditField(
                    caption = "Bio",
                    limit = "${bio.length} / 150",
                    value = bio,
                    onValueChange = { bio = it.take(150) },
                    first = false,
                    singleLine = false,
                    placeholder = "Say something about yourself",
                )
            }
            if (error.isNotBlank()) {
                Text(
                    text = error,
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Badge,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                )
            }
            SettingsCaption("Save stays disabled until something actually changes.")
            Spacer(Modifier.height(24.dp + bottom.coerceAtLeast(8.dp)))
        }
    }
}

private fun allowanceLine(left: Int?, fallback: String): String = when (left) {
    null -> fallback
    1 -> "1 change left this month"
    else -> "$left changes left this month"
}

@Composable
private fun EditField(
    caption: String,
    limit: String,
    value: String,
    onValueChange: (String) -> Unit,
    first: Boolean,
    prefix: String = "",
    hint: String = "",
    placeholder: String = "",
    singleLine: Boolean = true,
) {
    Column {
        if (!first) SettingsHairline()
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = caption, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet, modifier = Modifier.weight(1f))
                Text(text = limit, fontFamily = Wyrm.Body, fontSize = 12.sp, color = Wyrm.Quiet)
            }
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .fillMaxWidth()
                    .heightIn(min = if (singleLine) 42.dp else 74.dp)
                    .clip(wyrmRounded(10.dp))
                    .border(1.dp, Wyrm.Rule, wyrmRounded(10.dp))
                    .padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 11.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (prefix.isNotBlank()) {
                        Text(text = prefix, fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Quiet)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = singleLine,
                        textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Ink),
                        cursorBrush = SolidColor(Wyrm.Ink),
                        modifier = Modifier.fillMaxWidth().then(if (singleLine) Modifier.height(42.dp) else Modifier),
                        decorationBox = { inner ->
                            if (value.isEmpty() && placeholder.isNotBlank()) {
                                Text(text = placeholder, fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.TabIdle)
                            }
                            inner()
                        },
                    )
                }
            }
            if (hint.isNotBlank()) {
                Text(
                    text = hint,
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PaperDangerButtonSmall(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(wyrmRounded(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.Badge)
    }
}
