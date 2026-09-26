package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OnboardingChoices(
    val displayName: String,
    val username: String,
    val joystick: Boolean,
    val rightHand: Boolean,
    val preset: Int,
    val assistOn: Boolean,
)

private val CardShape = wyrmRounded(14.dp)
private val FieldShape = wyrmRounded(10.dp)
private val CtaShape = wyrmRounded(13.dp)

private data class SkinPick(
    val index: Int,
    val name: String,
    val note: String,
    val a: Color,
    val b: Color,
)

private val SkinPicks = listOf(
    SkinPick(0, "Ivory", "Default", Color(0xFFFCFBFA), Color(0xFFE2DDD2)),
    SkinPick(1, "Moss", "Hides in the grass", Color(0xFF9FB77C), Color(0xFF7A9459)),
    SkinPick(2, "Clay", "Easy to spot", Color(0xFFD39A7B), Color(0xFFB4765A)),
    SkinPick(3, "Ink", "Hard to read at speed", Color(0xFF5A564E), Color(0xFF38352F)),
)

/**
 * Spec round 2 — six-step onboarding after Google (or a username-less login).
 * Phone is not asked. Push is not asked. Team is not switched on here.
 */
@Composable
fun OnboardingScreen(
    suggestedName: String,
    busy: Boolean,
    error: String,
    insetTop: Dp,
    insetBottom: Dp,
    onFinish: (OnboardingChoices) -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var displayName by remember(suggestedName) { mutableStateOf(suggestedName) }
    var username by remember {
        mutableStateOf(suggestedName.lowercase().filter { it.isLetterOrDigit() }.take(20))
    }
    var joystick by remember { mutableStateOf(true) }
    var rightHand by remember { mutableStateOf(true) }
    var preset by remember { mutableIntStateOf(0) }
    var assistOn by remember { mutableStateOf(true) }

    LaunchedEffect(error) {
        if (error.isNotEmpty() && step != 1) step = 1
    }

    val usernameValid = username.length in 3..20 && username.all { it.isLetterOrDigit() || it == '_' }
    val nameValid = displayName.trim().length >= 2
    val handleReady = usernameValid && nameValid && !busy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        OnboardingChrome(
            step = step,
            insetTop = insetTop,
            onBack = { if (step > 0) step -= 1 },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                0 -> RulesStep()
                1 -> HandleStep(
                    displayName = displayName,
                    username = username,
                    error = error,
                    onName = { displayName = it.take(40) },
                    onUsername = { typed ->
                        username = typed.filter { it.isLetterOrDigit() || it == '_' }.take(20)
                    },
                    suggestedName = suggestedName,
                )
                2 -> SteeringStep(
                    joystick = joystick,
                    rightHand = rightHand,
                    onJoystick = { joystick = it },
                    onRightHand = { rightHand = it },
                )
                3 -> SkinStep(preset = preset, onPreset = { preset = it })
                4 -> ModesStep(assistOn = assistOn, onAssist = { assistOn = it })
                else -> ReadyStep(
                    username = username,
                    joystick = joystick,
                    rightHand = rightHand,
                    preset = preset,
                    assistOn = assistOn,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Wyrm.TabBar)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = insetBottom + 12.dp),
        ) {
            val label = when {
                busy && step == 5 -> "Saving…"
                step == 5 -> "Enter arena"
                else -> "Continue"
            }
            val enabled = when (step) {
                1 -> handleReady
                5 -> !busy
                else -> true
            }
            OnboardingCta(label = label, enabled = enabled) {
                if (step < 5) {
                    step += 1
                } else {
                    onFinish(
                        OnboardingChoices(
                            displayName = displayName.trim(),
                            username = username,
                            joystick = joystick,
                            rightHand = rightHand,
                            preset = preset,
                            assistOn = assistOn,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingChrome(step: Int, insetTop: Dp, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = insetTop)
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(enabled = step > 0, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            if (step > 0) {
                Text(text = "‹", fontFamily = Wyrm.Body, fontSize = 22.sp, color = Wyrm.Ink)
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            repeat(6) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(if (index <= step) Wyrm.Ink else Color(0x2137352F)),
                )
            }
        }
        Spacer(Modifier.width(32.dp))
    }
}

@Composable
private fun StepHead(eyebrow: String, title: String, body: String = "") {
    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp)) {
        Text(
            text = eyebrow.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            letterSpacing = 1.1.sp,
            color = Wyrm.Quiet,
        )
        Text(
            text = title,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 27.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.5).sp,
            color = Wyrm.Ink,
            modifier = Modifier.padding(top = 7.dp),
        )
        if (body.isNotEmpty()) {
            Text(
                text = body,
                fontFamily = Wyrm.Body,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = Wyrm.Mute,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun RulesStep() {
    StepHead("Step one of six", "Three rules, then you are in.")
    DottedHero(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .height(160.dp)
            .clip(wyrmRounded(16.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(16.dp)),
    )
    val rules = listOf(
        "Eat the orbs" to "Every orb is length, and length is score.",
        "Long snakes turn wide" to "The bigger you get, the more room a turn eats.",
        "Cut them off" to "Anything that hits your body is out, and drops its length as orbs.",
    )
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .clip(CardShape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, CardShape),
    ) {
        rules.forEachIndexed { index, (title, body) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(wyrmRounded(7.dp))
                        .background(Wyrm.Well),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1}",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Wyrm.Mute,
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(text = title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Wyrm.Ink)
                    Text(
                        text = body,
                        fontFamily = Wyrm.Body,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = Wyrm.Quiet,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
    Text(
        text = "Assist is a button you can keep on the screen while you learn. Switch it from Settings › On-screen buttons.",
        fontFamily = Wyrm.Body,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
    )
}

@Composable
private fun HandleStep(
    displayName: String,
    username: String,
    error: String,
    onName: (String) -> Unit,
    onUsername: (String) -> Unit,
    suggestedName: String,
) {
    StepHead("Step two of six", "What should the arena call you?")
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 20.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, CardShape)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WyrmAvatar(
            url = "",
            avatarKey = "mono-ink",
            initial = displayName.ifBlank { suggestedName },
            size = 44.dp,
            corner = 13.dp,
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "Photo and name came from Google",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = Wyrm.Ink,
            )
            Text(
                text = "You can change both later, twice a month.",
                fontFamily = Wyrm.Body,
                fontSize = 12.5.sp,
                color = Wyrm.Quiet,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .clip(CardShape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, CardShape),
    ) {
        PaperInput(label = "Display name", value = displayName, onValueChange = onName)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
        PaperInput(
            label = "Username",
            value = username,
            onValueChange = onUsername,
            prefix = "@",
            mono = true,
        )
        if (error.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Wyrm.Badge))
                Text(
                    text = error,
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Badge,
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
        }
    }
    Text(
        text = "The handle is how people find and invite you. It changes twice a month later; your in-game name changes as often as you like.",
        fontFamily = Wyrm.Body,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
    )
}

@Composable
private fun SteeringStep(
    joystick: Boolean,
    rightHand: Boolean,
    onJoystick: (Boolean) -> Unit,
    onRightHand: (Boolean) -> Unit,
) {
    StepHead(
        "Step three of six",
        "How do you steer?",
        "Both work. Joystick is steadier in a crowd.",
    )
    Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
        SteerCard(
            title = "Joystick",
            note = "Thumb stays in one corner",
            selected = joystick,
            modifier = Modifier.weight(1f),
            onClick = { onJoystick(true) },
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0x2E37352F), CircleShape),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Wyrm.Ink),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        SteerCard(
            title = "Swipe",
            note = "Drag anywhere on screen",
            selected = !joystick,
            modifier = Modifier.weight(1f),
            onClick = { onJoystick(false) },
        ) {
            Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x3837352F)),
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Wyrm.Ink),
                )
            }
        }
    }
    if (joystick) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 18.dp)
                .clip(CardShape)
                .background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, CardShape)
                .padding(13.dp),
        ) {
            Text(
                text = "Which side is your thumb on?",
                fontFamily = Wyrm.Body,
                fontSize = 12.5.sp,
                color = Wyrm.Quiet,
            )
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(wyrmRounded(10.dp))
                    .background(Wyrm.Hover)
                    .padding(3.dp),
            ) {
                Segment("Left", !rightHand, Modifier.weight(1f)) { onRightHand(false) }
                Segment("Right", rightHand, Modifier.weight(1f)) { onRightHand(true) }
            }
        }
    }
    Text(
        text = "Dead zone, stick size and opacity live under Settings › Controls.",
        fontFamily = Wyrm.Body,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
    )
}

@Composable
private fun SkinStep(preset: Int, onPreset: (Int) -> Unit) {
    StepHead(
        "Step four of six",
        "Pick a skin.",
        "Four to start. The rest live on Skin once you are in.",
    )
    DottedHero(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .height(140.dp)
            .clip(wyrmRounded(16.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(16.dp)),
    )
    val rows = SkinPicks.chunked(2)
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.padding(bottom = 10.dp)) {
                row.forEachIndexed { index, pick ->
                    if (index > 0) Spacer(Modifier.width(10.dp))
                    SkinCard(
                        pick = pick,
                        selected = preset == pick.index,
                        modifier = Modifier.weight(1f),
                        onClick = { onPreset(pick.index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModesStep(assistOn: Boolean, onAssist: (Boolean) -> Unit) {
    StepHead(
        "Step five of six",
        "What is switched on.",
        "Assist is an on-screen button. Team is set up later from Play.",
    )
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 20.dp)
            .clip(CardShape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, CardShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Assist mode", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = Wyrm.Ink)
                Text(
                    text = "Helper lines on an on-screen button. You can hide it later.",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            PaperSwitch(on = assistOn, onToggle = onAssist)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Team mode", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = Wyrm.Ink)
                Text(
                    text = "Needs an NTL id and key, from Play › Team mode.",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(text = "Off", fontFamily = Wyrm.Body, fontSize = 13.5.sp, color = Wyrm.Quiet)
        }
    }
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .clip(CardShape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, CardShape),
    ) {
        AlwaysOnRow("Global chat", "Muted words carry over from Settings.", "On", first = true)
        AlwaysOnRow("Leaderboard", "Your best round, updated live.", "Live", first = false)
    }
    Text(
        text = "Push is not asked here — the first time someone DMs you, Android asks once.",
        fontFamily = Wyrm.Body,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
    )
}

@Composable
private fun ReadyStep(
    username: String,
    joystick: Boolean,
    rightHand: Boolean,
    preset: Int,
    assistOn: Boolean,
) {
    DottedHero(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .fillMaxWidth()
            .height(180.dp)
            .clip(wyrmRounded(18.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(18.dp)),
    )
    StepHead("Step six of six", "That is everything.")
    val skin = SkinPicks.firstOrNull { it.index == preset }?.name ?: "Preset ${preset + 1}"
    val steering = if (joystick) {
        "Joystick, ${if (rightHand) "right" else "left"} thumb"
    } else {
        "Swipe"
    }
    val rows = listOf(
        "Handle" to "@$username",
        "Steering" to steering,
        "Skin" to skin,
        "Assist button" to if (assistOn) "On" else "Off",
        "Team mode" to "Off",
    )
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .clip(CardShape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, CardShape),
    ) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, fontFamily = Wyrm.Body, fontSize = 13.5.sp, color = Wyrm.Quiet, modifier = Modifier.width(118.dp))
                Text(text = value, fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Ink)
            }
        }
    }
    Text(
        text = "All of this lives under Settings, and the arena picks itself by ping.",
        fontFamily = Wyrm.Body,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
    )
}

@Composable
private fun PaperInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    prefix: String = "",
    mono: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(text = label, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet)
        Row(
            modifier = Modifier
                .padding(top = 7.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(FieldShape)
            .background(Wyrm.Card)
            .border(1.dp, if (focused) Wyrm.Ink else Wyrm.Rule, FieldShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prefix.isNotEmpty()) {
                Text(text = prefix, fontFamily = Wyrm.Body, fontSize = 16.sp, color = Color(0xFFA8A29A))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = if (mono) FontFamily.Monospace else Wyrm.Body,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                ),
                cursorBrush = SolidColor(Wyrm.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SteerCard(
    title: String,
    note: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = CardShape
    Column(
        modifier = modifier
            .scale(pressScale(pressed))
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, if (selected) Wyrm.Ink else Wyrm.Rule, shape)
            .then(
                if (selected) Modifier.border(3.dp, Color(0x1A37352F), shape) else Modifier,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            preview()
        }
        Text(text = title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = Wyrm.Ink, modifier = Modifier.padding(top = 8.dp))
        Text(text = note, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SkinCard(pick: SkinPick, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = wyrmRounded(13.dp)
    Column(
        modifier = modifier
            .scale(pressScale(pressed))
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, if (selected) Wyrm.Ink else Wyrm.Rule, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
    ) {
        Row {
            Box(Modifier.size(17.dp).clip(CircleShape).background(pick.a))
            Box(Modifier.padding(start = 4.dp, top = 1.dp).size(15.dp).clip(CircleShape).background(pick.b))
            Box(Modifier.padding(start = 4.dp, top = 2.dp).size(13.dp).clip(CircleShape).background(pick.a))
        }
        Text(text = pick.name, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = Wyrm.Ink, modifier = Modifier.padding(top = 9.dp))
        Text(text = pick.note, fontFamily = Wyrm.Body, fontSize = 12.sp, color = Wyrm.Quiet, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(wyrmRounded(8.dp))
            .background(if (selected) Wyrm.Card else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.5.sp,
            color = Wyrm.Ink,
        )
    }
}

@Composable
private fun AlwaysOnRow(title: String, note: String, value: String, first: Boolean) {
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Ink)
                Text(text = note, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet, modifier = Modifier.padding(top = 2.dp))
            }
            Text(text = value, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Wyrm.Live)
        }
    }
}

@Composable
private fun OnboardingCta(label: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(pressScale(pressed, enabled))
            .clip(CtaShape)
            .background(if (enabled) Wyrm.Ink else Wyrm.Ink.copy(alpha = 0.35f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.5.sp,
            color = Wyrm.OnInk,
        )
    }
}
