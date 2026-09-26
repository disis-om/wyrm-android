package com.wyrm.omrajput.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR

/**
 * The shared form pieces for onboarding and profile editing.
 *
 * Both screens write the same record through the same endpoint, so they use the
 * same controls; anything that differs between them is a caption, not a widget.
 */

/**
 * How much room to leave under a screen that has something to type in.
 *
 * The keyboard is drawn over the navigation bar, not above it, so a screen that
 * pads for the bar *and* then pads for the keyboard leaves a whole navigation
 * bar of empty black between the two — which is why every message field used to
 * float well clear of the keys instead of sitting on them. Only the taller of
 * the two is ever real, so only the taller of the two is used.
 */
@Composable
fun keyboardRoom(insetBottom: Dp): Dp {
    val keyboard = androidx.compose.foundation.layout.WindowInsets.ime
        .asPaddingValues()
        .calculateBottomPadding()
    return if (keyboard > insetBottom) keyboard else insetBottom
}

/** A labelled text field, with room underneath for a rule or an error. */
@Composable
fun WyrmField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    hint: String = "",
    error: String = "",
    prefix: String = "",
    singleLine: Boolean = true,
    minHeight: Int = 56,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    /** A password: shown as dots, and kept out of the keyboard's suggestions. */
    masked: Boolean = false,
    /** True only for account credentials that Android may offer to save. */
    passwordCredential: Boolean = masked,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WyrmLabel(label)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight.dp)
                .clip(wyrmRounded(Wyrm.CornerSmall))
                .background(Color.Black.copy(alpha = 0.30f))
                .background(wellFill())
                .border(
                    width = 1.dp,
                    brush = if (error.isNotEmpty()) SolidColor(Wyrm.Blood) else wellEdge(),
                    shape = wyrmRounded(Wyrm.CornerSmall),
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (prefix.isNotEmpty()) {
                    Text(
                        text = prefix,
                        fontFamily = Wyrm.Body,
                        fontSize = 15.sp,
                        color = Wyrm.Faint,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = TextStyle(
                        fontFamily = Wyrm.Body,
                        fontSize = 15.sp,
                        color = Wyrm.White,
                    ),
                    cursorBrush = SolidColor(Wyrm.Green),
                    visualTransformation = if (masked) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (masked && passwordCredential) KeyboardType.Password else keyboardType,
                        imeAction = imeAction,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                fontFamily = Wyrm.Body,
                                fontSize = 15.sp,
                                color = Wyrm.Faint,
                            )
                        }
                        inner()
                    },
                )
            }
        }
        if (error.isNotEmpty() || hint.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = error.ifEmpty { hint },
                fontFamily = Wyrm.Body,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = if (error.isNotEmpty()) Wyrm.Blood else Wyrm.Faint,
            )
        }
    }
}

/**
 * A search field, wherever something is searched.
 *
 * The same control on the arena picker and on the board, so searching reads as
 * one thing rather than two that happen to look alike.
 */
@Composable
fun WyrmSearchField(
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    // A search bar is the one field a phone always draws as a capsule.
    WyrmWell(modifier = modifier.fillMaxWidth().height(50.dp), corner = Wyrm.Pill) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "⌕", fontFamily = Wyrm.Body, fontSize = 16.sp, color = Wyrm.Faint)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.White),
                cursorBrush = SolidColor(Wyrm.Green),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = Wyrm.Body,
                            fontSize = 14.sp,
                            color = Wyrm.Faint,
                        )
                    }
                    inner()
                },
            )
            if (value.isNotEmpty()) {
                Text(
                    text = "✕",
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Grey,
                    modifier = Modifier
                        .clickable { onValueChange("") }
                        .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/**
 * Phone entry: pick the country, then type the number.
 *
 * The dialling code is chosen, never typed, so nobody has to know their own
 * prefix or guess whether it wants a leading zero.
 */
@Composable
fun WyrmPhoneField(
    country: Country,
    onCountryChange: (Country) -> Unit,
    number: String,
    onNumberChange: (String) -> Unit,
    error: String = "",
    hint: String = "",
) {
    var picking by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (picking) 180f else 0f,
        label = "country selector chevron",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        WyrmLabel("Phone")
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .width(126.dp)
                    .height(56.dp)
                    .clip(wyrmRounded(Wyrm.CornerSmall))
                    .background(Color.Black.copy(alpha = 0.30f))
                    .background(wellFill())
                    .border(1.dp, wellEdge(), wyrmRounded(Wyrm.CornerSmall))
                    .clickable(role = Role.Button) { picking = !picking }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = country.flag, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = country.dial,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Wyrm.White,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(wyrmRounded(8.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_chevron_down),
                        contentDescription = if (picking) "Close country list" else "Open country list",
                        tint = Wyrm.SoftWhite,
                        modifier = Modifier.size(15.dp).rotate(chevronRotation),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(wyrmRounded(Wyrm.CornerSmall))
                    .background(Color.Black.copy(alpha = 0.30f))
                    .background(wellFill())
                    .border(
                        width = 1.dp,
                        brush = if (error.isNotEmpty()) SolidColor(Wyrm.Blood) else wellEdge(),
                        shape = wyrmRounded(Wyrm.CornerSmall),
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = number,
                    // Digits only: the country half already carries the prefix,
                    // and a second one typed here would be sent twice.
                    onValueChange = { typed -> onNumberChange(typed.filter { it.isDigit() }.take(15)) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = Wyrm.Body,
                        fontSize = 15.sp,
                        color = Wyrm.White,
                    ),
                    cursorBrush = SolidColor(Wyrm.Green),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    decorationBox = { inner ->
                        if (number.isEmpty()) {
                            Text(
                                text = "Phone number",
                                fontFamily = Wyrm.Body,
                                fontSize = 15.sp,
                                color = Wyrm.Faint,
                            )
                        }
                        inner()
                    },
                )
            }
        }

        if (picking) {
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(wyrmRounded(Wyrm.Corner))
                    .background(Wyrm.Black.copy(alpha = 0.44f))
                    .background(glassFill())
                    .border(1.dp, glassEdge(), wyrmRounded(Wyrm.Corner)),
            ) {
                items(COUNTRIES) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCountryChange(option)
                                picking = false
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = option.flag, fontSize = 15.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = option.name,
                            fontFamily = Wyrm.Body,
                            fontSize = 13.sp,
                            color = if (option.iso == country.iso) Wyrm.White else Wyrm.SoftWhite,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = option.dial,
                            fontFamily = Wyrm.Body,
                            fontSize = 12.sp,
                            color = Wyrm.Grey,
                        )
                    }
                    WyrmRule()
                }
            }
        }

        if (error.isNotEmpty() || hint.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = error.ifEmpty { hint },
                fontFamily = Wyrm.Body,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = if (error.isNotEmpty()) Wyrm.Blood else Wyrm.Faint,
            )
        }
    }
}

/** The same, for chat. */
fun readableChatError(code: String): String = when (code) {
    "MESSAGE_RATE_LIMITED" -> "Slow down a moment."
    "MUTUAL_FOLLOW_REQUIRED" -> "You both have to follow each other before this thread opens."
    "PROFILE_INCOMPLETE" -> "Set a username on your profile before you can chat."
    "INVALID_MESSAGE" -> "That message can't be sent."
    "" -> "That didn't send. Check your connection."
    else -> code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

/** The same, for the things that can go wrong with a photograph. */
fun readablePhotoError(code: String): String = when (code) {
    "IMAGE_TOO_LARGE" -> "That image is too large. Try a smaller one."
    "UNSUPPORTED_IMAGE" -> "Only PNG and JPG images can be used."
    "EMPTY_IMAGE" -> "That image came through empty. Try another."
    "" -> "The photo couldn't be uploaded. Check your connection and try again."
    else -> code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

/** Turns a server error code into something a player can act on. */
fun readableProfileError(code: String): String = when (code) {
    "USERNAME_TAKEN" -> "That IGN is already taken. Try another."
    "INVALID_PHONE" -> "That phone number doesn't look right for the country you picked."
    "INVALID_PROFILE" -> "IGN must be 3–20 letters, numbers or underscores, and your name at least 2 characters."
    "" -> "Something went wrong. Try again."
    else -> code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
