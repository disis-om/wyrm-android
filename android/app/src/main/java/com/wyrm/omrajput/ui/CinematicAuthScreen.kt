package com.wyrm.omrajput.ui

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Wyrm iOS's cinematic sign-in (WyrmCinematicAuth.swift), frame for frame.
 *
 * One paper page, one curving W. On the landing the W sits over a field of
 * dots; choosing an action lifts it to the top and shrinks it, and every
 * stage — username, password, confirmation — arrives by materialising out of
 * a blur. While the account is created or entered the W drops to the middle
 * and grows, a shimmer reads "Creating your account…" beneath it, and when
 * the whole account has synced the W swells once and dissolves into Home.
 * Every spring is the iOS one: response 0.58, damping 0.86.
 */

enum class AuthStage { LANDING, CREATE_USERNAME, CREATE_PASSWORD, CREATE_CONFIRMATION, LOGIN_USERNAME, LOGIN_PASSWORD, CREATING, SIGNING_IN, SUCCESS }

enum class UsernameAvailability { IDLE, CHECKING, AVAILABLE, TAKEN, UNAVAILABLE }

private enum class ConfirmationState { IDLE, CHECKING, MATCHED, MISMATCHED }

private enum class AuthField { USERNAME, PASSWORD, CONFIRMATION }

private val usernamePattern = Regex("^[A-Za-z0-9_]{3,20}$")

@Composable
fun CinematicAuthScreen(
    insetTop: Dp,
    insetBottom: Dp,
    checkUsername: suspend (String) -> UsernameAvailability,
    /** Returns null on success, or the message to show. */
    signUp: suspend (username: String, password: String) -> String?,
    logIn: suspend (username: String, password: String) -> String?,
    /** Fetches every account-scoped surface before Home is shown. */
    bootstrap: suspend () -> Unit,
    onComplete: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    var stage by remember { mutableStateOf(AuthStage.LANDING) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var availability by remember { mutableStateOf(UsernameAvailability.IDLE) }
    var confirmationState by remember { mutableStateOf(ConfirmationState.IDLE) }
    var logoDissolved by remember { mutableStateOf(false) }
    var focusTarget by remember { mutableStateOf<AuthField?>(null) }
    var focusPulse by remember { mutableStateOf(0) }

    val usernameValid = usernamePattern.matches(username)
    val passwordValid = password.length in 8..200

    val keyboardHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val keyboardUp = keyboardHeight > 0.dp

    fun enter(next: AuthStage, focus: AuthField?) {
        errorMessage = ""
        focusManager.clearFocus()
        if (focus == null) keyboardController?.hide()
        stage = next
        focusTarget = focus
        focusPulse++
    }

    // Username availability: 420 ms after the last keystroke, only while the
    // username stage is up and the name is well-formed.
    LaunchedEffect(username, stage) {
        if (stage != AuthStage.CREATE_USERNAME || !usernamePattern.matches(username)) {
            availability = UsernameAvailability.IDLE
            return@LaunchedEffect
        }
        availability = UsernameAvailability.CHECKING
        delay(420)
        availability = checkUsername(username)
    }

    // Password confirmation: compared 280 ms after the second entry is at
    // least as long as the first.
    LaunchedEffect(password, confirmation, stage) {
        if (stage != AuthStage.CREATE_CONFIRMATION || password.isEmpty() || confirmation.length < password.length) {
            confirmationState = ConfirmationState.IDLE
            return@LaunchedEffect
        }
        confirmationState = ConfirmationState.CHECKING
        delay(280)
        confirmationState = if (confirmation == password) ConfirmationState.MATCHED else ConfirmationState.MISMATCHED
    }

    val actionEnabled = when (stage) {
        AuthStage.CREATE_USERNAME -> usernameValid && availability == UsernameAvailability.AVAILABLE
        AuthStage.LOGIN_USERNAME -> username.isNotEmpty()
        AuthStage.CREATE_PASSWORD -> passwordValid
        AuthStage.LOGIN_PASSWORD -> password.isNotEmpty() && password.length <= 200
        AuthStage.CREATE_CONFIRMATION -> passwordValid && confirmationState == ConfirmationState.MATCHED
        else -> false
    }
    val showsKeyboardAction = when (stage) {
        AuthStage.CREATE_USERNAME -> usernameValid
        AuthStage.LOGIN_USERNAME -> username.isNotEmpty()
        AuthStage.CREATE_PASSWORD, AuthStage.LOGIN_PASSWORD -> password.isNotEmpty()
        AuthStage.CREATE_CONFIRMATION -> confirmationState == ConfirmationState.MATCHED
        else -> false
    }
    val showsBack = stage in setOf(
        AuthStage.CREATE_USERNAME, AuthStage.CREATE_PASSWORD, AuthStage.CREATE_CONFIRMATION,
        AuthStage.LOGIN_USERNAME, AuthStage.LOGIN_PASSWORD,
    )
    val actionTitle = when (stage) {
        AuthStage.CREATE_USERNAME, AuthStage.LOGIN_USERNAME -> "Continue"
        AuthStage.CREATE_PASSWORD -> "Next"
        AuthStage.LOGIN_PASSWORD -> "Login"
        AuthStage.CREATE_CONFIRMATION -> "Create account"
        else -> "Continue"
    }

    var authJob by remember { mutableStateOf<Job?>(null) }
    fun authenticate(create: Boolean) {
        if (authJob?.isActive == true) return
        focusManager.clearFocus()
        keyboardController?.hide()
        errorMessage = ""
        stage = if (create) AuthStage.CREATING else AuthStage.SIGNING_IN
        authJob = scope.launch {
            val failure = if (create) signUp(username, password) else logIn(username, password)
            if (failure != null) {
                val usernameFailure = create && failure.contains("username", ignoreCase = true)
                enter(
                    if (usernameFailure) AuthStage.CREATE_USERNAME else if (create) AuthStage.CREATE_CONFIRMATION else AuthStage.LOGIN_PASSWORD,
                    if (usernameFailure) AuthField.USERNAME else if (create) AuthField.CONFIRMATION else AuthField.PASSWORD,
                )
                errorMessage = failure
                return@launch
            }
            // The W stays on screen until every account surface has a fresh
            // snapshot, so Home never draws an empty or previous account.
            bootstrap()
            stage = AuthStage.SUCCESS
            delay(700)
            logoDissolved = true
            delay(520)
            onComplete()
        }
    }

    fun advance() {
        if (!actionEnabled) return
        when (stage) {
            AuthStage.CREATE_USERNAME -> enter(AuthStage.CREATE_PASSWORD, AuthField.PASSWORD)
            AuthStage.LOGIN_USERNAME -> enter(AuthStage.LOGIN_PASSWORD, AuthField.PASSWORD)
            AuthStage.CREATE_PASSWORD -> enter(AuthStage.CREATE_CONFIRMATION, AuthField.CONFIRMATION)
            AuthStage.LOGIN_PASSWORD -> authenticate(create = false)
            AuthStage.CREATE_CONFIRMATION -> authenticate(create = true)
            else -> Unit
        }
    }

    fun goBack() {
        when (stage) {
            AuthStage.CREATE_USERNAME, AuthStage.LOGIN_USERNAME -> enter(AuthStage.LANDING, null)
            AuthStage.CREATE_PASSWORD -> enter(AuthStage.CREATE_USERNAME, AuthField.USERNAME)
            AuthStage.CREATE_CONFIRMATION -> enter(AuthStage.CREATE_PASSWORD, AuthField.PASSWORD)
            AuthStage.LOGIN_PASSWORD -> enter(AuthStage.LOGIN_USERNAME, AuthField.USERNAME)
            else -> Unit
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        val height = maxHeight
        val width = maxWidth
        val safeTop = max(insetTop.value, 18f).dp
        val safeBottom = max(insetBottom.value, 12f).dp
        val dotFieldHeight = min(280f, max(232f, height.value * 0.31f)).dp

        // Landing: the dot field behind the W.
        AnimatedVisibility(
            visible = stage == AuthStage.LANDING,
            enter = fadeIn(iosSpring<Float>(0.58f, 0.86f)),
            exit = fadeOut(iosSpring<Float>(0.58f, 0.86f)),
        ) {
            WyrmDotField(Modifier.fillMaxWidth().height(dotFieldHeight))
        }

        // The stage itself.
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (fadeIn(iosSpring<Float>(0.58f, 0.86f)) + scaleIn(iosSpring<Float>(0.58f, 0.86f), initialScale = 0.985f)) togetherWith
                    (fadeOut(iosSpring<Float>(0.58f, 0.86f)) + scaleOut(iosSpring<Float>(0.58f, 0.86f), targetScale = 0.985f))
            },
            contentKey = { it },
            modifier = Modifier.fillMaxSize(),
            label = "auth-stage",
        ) { shown ->
            Box(Modifier.fillMaxSize().then(blurFade())) {
                when (shown) {
                    AuthStage.LANDING -> Landing(
                        dotFieldHeight = dotFieldHeight,
                        safeBottom = safeBottom,
                        onCreate = { enter(AuthStage.CREATE_USERNAME, AuthField.USERNAME) },
                        onLogin = { enter(AuthStage.LOGIN_USERNAME, AuthField.USERNAME) },
                        onPrivacy = onPrivacy,
                    )
                    AuthStage.CREATE_USERNAME, AuthStage.LOGIN_USERNAME, AuthStage.CREATE_PASSWORD,
                    AuthStage.LOGIN_PASSWORD, AuthStage.CREATE_CONFIRMATION -> CredentialStage(
                        stage = shown,
                        keyboardHeight = keyboardHeight,
                        username = username,
                        onUsername = { value ->
                            username = value.filter { it.isLetterOrDigit() || it == '_' }.take(20)
                            errorMessage = ""
                        },
                        password = password,
                        onPassword = { password = it },
                        confirmation = confirmation,
                        onConfirmation = { confirmation = it },
                        availability = availability,
                        usernameValid = usernameValid,
                        passwordValid = passwordValid,
                        confirmationState = confirmationState,
                        errorMessage = errorMessage,
                        actionEnabled = actionEnabled,
                        onSubmit = ::advance,
                        focusTarget = focusTarget,
                        focusPulse = focusPulse,
                    )
                    AuthStage.CREATING, AuthStage.SIGNING_IN -> WorkingStatus(
                        title = if (shown == AuthStage.CREATING) "Creating your account…" else "Entering Wyrm…",
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = height * 0.45f + 92.dp - 9.dp),
                    )
                    AuthStage.SUCCESS -> Unit
                }
            }
        }

        // The W: one mark that travels between stages.
        val logoSize by animateDpAsState(
            targetValue = when (stage) {
                AuthStage.LANDING -> 108.dp
                AuthStage.CREATING, AuthStage.SIGNING_IN -> 118.dp
                AuthStage.SUCCESS -> 132.dp
                else -> 74.dp
            },
            animationSpec = iosSpring<Dp>(0.58f, 0.86f),
            label = "logo-size",
        )
        val logoY by animateDpAsState(
            targetValue = when (stage) {
                AuthStage.LANDING -> min(188f, safeTop.value + 132f).dp
                AuthStage.CREATING, AuthStage.SIGNING_IN, AuthStage.SUCCESS -> height * 0.45f
                else -> safeTop + 72.dp
            },
            animationSpec = iosSpring<Dp>(0.58f, 0.86f),
            label = "logo-y",
        )
        val logoScale by animateFloatAsState(
            targetValue = if (stage == AuthStage.SUCCESS) 1.08f else 1f,
            animationSpec = iosSpring<Float>(0.58f, 0.86f),
            label = "logo-scale",
        )
        val dissolve by animateFloatAsState(
            targetValue = if (logoDissolved) 1f else 0f,
            animationSpec = tween(520, easing = FastOutSlowInEasing),
            label = "logo-dissolve",
        )
        WyrmBrandMark(
            size = logoSize,
            modifier = Modifier
                .offset { IntOffset(((width - logoSize) / 2).roundToPx(), (logoY - logoSize / 2).roundToPx()) },
            layer = {
                scaleX = logoScale
                scaleY = logoScale
                alpha = 1f - dissolve
                val radius = 18.dp.toPx() * dissolve
                renderEffect = if (radius > 0.5f && Build.VERSION.SDK_INT >= 31) BlurEffect(radius, radius, TileMode.Decal) else null
            },
        )

        // Back.
        AnimatedVisibility(
            visible = showsBack,
            enter = fadeIn(iosSpring<Float>(0.58f, 0.86f)) + scaleIn(iosSpring<Float>(0.58f, 0.86f), initialScale = 0.985f),
            exit = fadeOut(iosSpring<Float>(0.58f, 0.86f)) + scaleOut(iosSpring<Float>(0.58f, 0.86f), targetScale = 0.985f),
            modifier = Modifier.offset(x = 42.dp - 23.dp, y = safeTop + 24.dp - 23.dp),
        ) {
            Box(
                modifier = Modifier
                    .then(blurFade())
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.88f))
                    .border(1.dp, Wyrm.Rule, CircleShape)
                    .clickable(onClick = ::goBack),
                contentAlignment = Alignment.Center,
            ) {
                IosIcon(IosGlyph.CHEVRON_LEFT, Wyrm.Ink, size = 18.dp, weight = 2.4f)
            }
        }

        // The keyboard's own action: rides the keyboard, blurs in when usable.
        val actionBottom = if (keyboardUp) keyboardHeight + 9.dp else safeBottom + 10.dp
        AnimatedVisibility(
            visible = showsKeyboardAction,
            enter = fadeIn(iosSpring<Float>(0.58f, 0.86f)) + scaleIn(iosSpring<Float>(0.58f, 0.86f), initialScale = 0.985f),
            exit = fadeOut(iosSpring<Float>(0.58f, 0.86f)) + scaleOut(iosSpring<Float>(0.58f, 0.86f), targetScale = 0.985f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = actionBottom),
        ) {
            KeyboardAction(title = actionTitle, enabled = actionEnabled, modifier = Modifier.then(blurFade()), onClick = ::advance)
        }
    }
}

/** SwiftUI's `wyrmBlurFade`: content materialises out of a 15 pt blur. */
@Composable
private fun AnimatedVisibilityScope.blurFade(): Modifier {
    val blur by transition.animateFloat(
        transitionSpec = { iosSpring<Float>(0.58f, 0.86f) },
        label = "blur-fade",
    ) { state -> if (state == EnterExitState.Visible) 0f else 15f }
    return Modifier.graphicsLayer {
        val radius = blur.dp.toPx()
        renderEffect = if (radius > 0.5f && Build.VERSION.SDK_INT >= 31) BlurEffect(radius, radius, TileMode.Decal) else null
    }
}

@Composable
private fun Landing(
    dotFieldHeight: Dp,
    safeBottom: Dp,
    onCreate: () -> Unit,
    onLogin: () -> Unit,
    onPrivacy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = safeBottom + 12.dp),
    ) {
        Spacer(Modifier.height(dotFieldHeight))
        Text(
            text = "WELCOME TO WYRM",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            letterSpacing = 1.1.sp,
            color = Wyrm.Quiet,
        )
        Text(
            text = "The arena\nand people in it.",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 31.sp,
            letterSpacing = (-0.7).sp,
            lineHeight = 41.sp,
            color = Wyrm.Ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "A High Performance Engine — plus assist mode, team play, global chat and a live leaderboard.",
            fontFamily = Wyrm.Body,
            fontSize = 15.5.sp,
            lineHeight = 26.sp,
            color = Wyrm.Mute,
            modifier = Modifier.padding(top = 11.dp),
        )
        Spacer(Modifier.weight(1f).heightIn(min = 18.dp))
        IosPrimaryAction(title = "Create Wyrm account", glyph = IosGlyph.ARROW_RIGHT, onClick = onCreate)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onLogin),
            contentAlignment = Alignment.Center,
        ) {
            Text("Login", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = Wyrm.Link)
        }
        Text(
            text = "Privacy",
            fontFamily = Wyrm.Body,
            fontSize = 12.5.sp,
            color = Wyrm.Quiet.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPrivacy),
        )
    }
}

@Composable
private fun CredentialStage(
    stage: AuthStage,
    keyboardHeight: Dp,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    confirmation: String,
    onConfirmation: (String) -> Unit,
    availability: UsernameAvailability,
    usernameValid: Boolean,
    passwordValid: Boolean,
    confirmationState: ConfirmationState,
    errorMessage: String,
    actionEnabled: Boolean,
    onSubmit: () -> Unit,
    focusTarget: AuthField?,
    focusPulse: Int,
) {
    val lift by animateDpAsState(
        targetValue = if (keyboardHeight > 0.dp) min(116f, max(78f, keyboardHeight.value * 0.28f)).dp else 0.dp,
        animationSpec = iosSpring<Dp>(0.58f, 0.86f),
        label = "credential-lift",
    )
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(stage, focusPulse) {
        val wanted = when (stage) {
            AuthStage.CREATE_USERNAME, AuthStage.LOGIN_USERNAME -> AuthField.USERNAME
            AuthStage.CREATE_PASSWORD, AuthStage.LOGIN_PASSWORD -> AuthField.PASSWORD
            AuthStage.CREATE_CONFIRMATION -> AuthField.CONFIRMATION
            else -> null
        }
        if (wanted != null && wanted == focusTarget) {
            delay(340)
            runCatching { requester.requestFocus() }
            keyboard?.show()
        }
    }

    val kicker = when (stage) {
        AuthStage.CREATE_USERNAME -> "YOUR WYRM ID"
        AuthStage.LOGIN_USERNAME -> "WELCOME BACK"
        AuthStage.CREATE_PASSWORD -> "SECURE YOUR ACCOUNT"
        AuthStage.LOGIN_PASSWORD -> "YOUR PASSWORD"
        AuthStage.CREATE_CONFIRMATION -> "ONE MORE TIME"
        else -> ""
    }
    val instruction = when (stage) {
        AuthStage.CREATE_USERNAME -> "Choose a username for your Wyrm account. It links your global leaderboard data, follows, follow-backs and messages."
        AuthStage.LOGIN_USERNAME -> "Enter the username connected to your Wyrm profile."
        AuthStage.CREATE_PASSWORD -> "Use at least 8 characters. This account has no email recovery, so keep the password somewhere safe."
        AuthStage.LOGIN_PASSWORD -> "Enter the password for @$username."
        AuthStage.CREATE_CONFIRMATION -> "Re-enter your password to make sure it is correct."
        else -> ""
    }
    val validation: String? = when {
        stage == AuthStage.CREATE_USERNAME && username.isNotEmpty() && !usernameValid -> "3–20 letters, numbers or underscores"
        stage == AuthStage.CREATE_USERNAME && availability == UsernameAvailability.TAKEN -> "That username is already taken"
        stage == AuthStage.CREATE_USERNAME && availability == UsernameAvailability.UNAVAILABLE -> "Could not check availability. Edit the username to retry."
        stage == AuthStage.CREATE_PASSWORD && password.isNotEmpty() && !passwordValid -> {
            val left = max(0, 8 - password.length)
            "$left more character${if (password.length == 7) "" else "s"}"
        }
        else -> null
    }
    val submit = KeyboardActions(onAny = { if (actionEnabled) onSubmit() })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = -lift)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f).heightIn(min = 150.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(kicker, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 1.35.sp, color = Wyrm.Quiet)
            when (stage) {
                AuthStage.CREATE_USERNAME, AuthStage.LOGIN_USERNAME -> ComposerShell {
                    Text("@", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Wyrm.Quiet)
                    Spacer(Modifier.width(5.dp))
                    AuthField(
                        value = username,
                        onValue = onUsername,
                        placeholder = "username",
                        fontSize = 21,
                        requester = requester,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                        actions = submit,
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedVisibility(
                        visible = stage == AuthStage.CREATE_USERNAME && usernameValid,
                        enter = fadeIn(iosSpring<Float>(0.58f, 0.86f)) + scaleIn(iosSpring<Float>(0.58f, 0.86f), initialScale = 0.985f),
                        exit = fadeOut(iosSpring<Float>(0.58f, 0.86f)) + scaleOut(iosSpring<Float>(0.58f, 0.86f), targetScale = 0.985f),
                    ) {
                        Box(Modifier.then(blurFade()).size(27.dp), contentAlignment = Alignment.Center) {
                            AvailabilityIndicator(availability)
                        }
                    }
                }
                AuthStage.CREATE_PASSWORD, AuthStage.LOGIN_PASSWORD -> ComposerShell {
                    AuthField(
                        value = password,
                        onValue = onPassword,
                        placeholder = "Password",
                        fontSize = 19,
                        requester = requester,
                        keyboardType = KeyboardType.Password,
                        imeAction = if (stage == AuthStage.CREATE_PASSWORD) ImeAction.Next else ImeAction.Go,
                        actions = submit,
                        secure = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                AuthStage.CREATE_CONFIRMATION -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ComposerShell {
                        AuthField(
                            value = confirmation,
                            onValue = onConfirmation,
                            placeholder = "Password again",
                            fontSize = 19,
                            requester = requester,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Go,
                            actions = submit,
                            secure = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Box(Modifier.padding(start = 5.dp).heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart) {
                        AnimatedContent(
                            targetState = confirmationState,
                            transitionSpec = { fadeIn(iosSpring<Float>(0.58f, 0.86f)) togetherWith fadeOut(iosSpring<Float>(0.58f, 0.86f)) },
                            label = "confirmation",
                        ) { state ->
                            Box(Modifier.then(blurFade())) {
                                when (state) {
                                    ConfirmationState.CHECKING -> IosSpinner(size = 17.dp)
                                    ConfirmationState.MATCHED -> MatchLabel("Password matched, continue!", IosGlyph.CHECKMARK, Wyrm.Live)
                                    ConfirmationState.MISMATCHED -> MatchLabel("Password didn't match, recheck!", IosGlyph.XMARK, Color(0xFFFF3B30))
                                    ConfirmationState.IDLE -> Spacer(Modifier.size(1.dp))
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
            Text(
                text = instruction,
                fontFamily = Wyrm.Body,
                fontSize = 12.5.sp,
                lineHeight = 21.sp,
                color = Wyrm.Mute,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 330.dp),
            )
            val message = if (errorMessage.isNotEmpty()) errorMessage else validation
            AnimatedContent(
                targetState = message,
                transitionSpec = { fadeIn(iosSpring<Float>(0.58f, 0.86f)) togetherWith fadeOut(iosSpring<Float>(0.58f, 0.86f)) },
                label = "auth-message",
            ) { text ->
                Box(Modifier.then(blurFade())) {
                    if (text != null) {
                        val isError = text == errorMessage && errorMessage.isNotEmpty()
                        Text(
                            text = text,
                            fontFamily = Wyrm.Body,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (isError) 12.sp else 11.5.sp,
                            color = if (isError) Color(0xFFFF3B30) else Wyrm.Quiet,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f).heightIn(min = 155.dp))
    }
}

/** The white composer every credential sits in: 60 tall, 17 continuous corners. */
@Composable
private fun ComposerShell(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = wyrmRounded(17.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .iosShadow(Wyrm.Ink.copy(alpha = 0.045f), radius = 22.dp, y = 9.dp, corner = 17.dp)
            .height(60.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.94f))
            .border(1.dp, Wyrm.Rule, shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun AuthField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    fontSize: Int,
    requester: FocusRequester,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    actions: KeyboardActions,
    modifier: Modifier = Modifier,
    secure: Boolean = false,
) {
    val style = TextStyle(fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = fontSize.sp, color = Wyrm.Ink)
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Wyrm.Link),
        visualTransformation = if (secure) PasswordVisualTransformation('•') else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = actions,
        modifier = modifier.focusRequester(requester),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = Color(0x4D3C3C43)))
                inner()
            }
        },
    )
}

@Composable
private fun AvailabilityIndicator(state: UsernameAvailability) {
    when (state) {
        UsernameAvailability.CHECKING -> IosSpinner(size = 17.dp)
        UsernameAvailability.AVAILABLE -> Box(
            Modifier.size(25.dp).clip(CircleShape).background(Wyrm.Live),
            contentAlignment = Alignment.Center,
        ) { IosIcon(IosGlyph.CHECKMARK, Wyrm.OnInk, size = 15.dp, weight = 3.4f) }
        UsernameAvailability.TAKEN -> Box(
            Modifier.size(25.dp).clip(CircleShape).background(Color(0xFFFF3B30).copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center,
        ) { IosIcon(IosGlyph.XMARK, Color.White, size = 13.dp, weight = 3.6f) }
        UsernameAvailability.UNAVAILABLE -> IosIcon(IosGlyph.ARROW_CLOCKWISE, Wyrm.Quiet, size = 15.dp, weight = 2.4f)
        UsernameAvailability.IDLE -> Unit
    }
}

@Composable
private fun MatchLabel(text: String, glyph: IosGlyph, colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(colour), contentAlignment = Alignment.Center) {
            IosIcon(glyph, Color.White, size = 11.dp, weight = 3.6f)
        }
        Text(text, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = colour)
    }
}

/** `WyrmAuthKeyboardAction`: 54 tall, 15 corners, title left and an arrow right. */
@Composable
private fun KeyboardAction(title: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = wyrmRounded(15.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .iosShadow(Wyrm.Ink.copy(alpha = if (enabled) 0.18f else 0f), radius = 18.dp, y = 8.dp, corner = 15.dp)
            .height(54.dp)
            .clip(shape)
            .background(if (enabled) Wyrm.Ink else Wyrm.Ink.copy(alpha = 0.34f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.OnInk, modifier = Modifier.weight(1f))
        IosIcon(IosGlyph.ARROW_RIGHT, Wyrm.OnInk, size = 18.dp, weight = 2.4f)
    }
}

/** `WyrmPrimaryAction`: title left, glyph right, 52 tall, 13 corners, ink. */
@Composable
fun IosPrimaryAction(title: String, glyph: IosGlyph? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { val s = if (pressed) 0.975f else 1f; scaleX = s; scaleY = s }
            .height(52.dp)
            .clip(wyrmRounded(13.dp))
            .background(if (enabled) Wyrm.Ink else Wyrm.Ink.copy(alpha = 0.35f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.OnInk, modifier = Modifier.weight(1f))
        if (glyph != null) IosIcon(glyph, Wyrm.OnInk, size = 18.dp, weight = 2.4f)
    }
}

/** `WyrmAuthWorkingStatus`: quiet caps with an ink shimmer sweeping through. */
@Composable
fun WorkingStatus(title: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val sweep by transition.animateFloat(
        initialValue = -1.4f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1350, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-offset",
    )
    var widthPx by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    val base = TextStyle(fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.55.sp)
    Box(modifier.onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }) {
        Text(title, style = base.copy(color = Wyrm.Quiet.copy(alpha = 0.58f)))
        val band = max(with(density) { 72.dp.toPx() }, widthPx * 0.58f)
        val start = sweep * widthPx
        Text(
            title,
            style = base.copy(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Wyrm.Ink.copy(alpha = 0.96f), Color.Transparent),
                    startX = start,
                    endX = start + band,
                    tileMode = TileMode.Clamp,
                ),
            ),
        )
    }
}

/**
 * `WyrmBrandMark`: the curving W, stroked with an ink gradient, round ends.
 *
 * It lays out at [size] but draws on a canvas 1.6× larger: the round caps
 * reach past the mark's own box, and a blur or fade given through [layer]
 * renders into its layer's bounds — drawn at [size] the W was cut off at
 * both sides while it moved and dissolved.
 */
@Composable
fun WyrmBrandMark(size: Dp, modifier: Modifier = Modifier, layer: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {}) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
    Canvas(Modifier.requiredSize(size * 1.6f).graphicsLayer(layer)) {
        val w = size.toPx()
        val h = w * 0.78f
        val left = this.size.width / 2f - w / 2f
        val top = this.size.height / 2f - h / 2f
        val path = Path().apply {
            moveTo(left + w * 0.06f, top + h * 0.10f)
            cubicTo(left + w * 0.13f, top + h * 0.92f, left + w * 0.30f, top + h * 0.96f, left + w * 0.36f, top + h * 0.38f)
            cubicTo(left + w * 0.42f, top + h * 0.94f, left + w * 0.58f, top + h * 0.94f, left + w * 0.64f, top + h * 0.38f)
            cubicTo(left + w * 0.70f, top + h * 0.96f, left + w * 0.87f, top + h * 0.92f, left + w * 0.94f, top + h * 0.10f)
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(Wyrm.Ink.copy(alpha = 0.76f), Wyrm.Ink),
                start = Offset(left, top),
                end = Offset(left + w, top + h),
            ),
            style = Stroke(width = w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    }
}

@Composable
private fun WyrmDotField(modifier: Modifier) {
    Canvas(modifier) {
        val spacing = 19.dp.toPx()
        val r = 1.2.dp.toPx()
        val colour = Wyrm.Ink.copy(alpha = 0.10f)
        var y = spacing / 2f
        while (y < size.height) {
            var x = spacing / 2f
            while (x < size.width) {
                drawCircle(colour, r, Offset(x, y))
                x += spacing
            }
            y += spacing
        }
    }
}

/** UIActivityIndicatorView: eight spokes, the lit one stepping round. */
@Composable
fun IosSpinner(size: Dp = 20.dp, colour: Color = Wyrm.Quiet) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "spinner-step",
    )
    Canvas(Modifier.size(size)) {
        val head = turn.toInt() % 8
        val radius = this.size.minDimension / 2f
        val stroke = radius * 0.2f
        for (i in 0 until 8) {
            val age = (head - i + 8) % 8
            val alpha = 1f - age / 8f * 0.78f
            rotate(i * 45f) {
                drawLine(
                    color = colour.copy(alpha = colour.alpha * alpha),
                    start = Offset(center.x, center.y - radius * 0.46f),
                    end = Offset(center.x, center.y - radius + stroke / 2f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** SwiftUI `.shadow(color:radius:y:)` behind a rounded rectangle. */
fun Modifier.iosShadow(color: Color, radius: Dp, y: Dp, corner: Dp): Modifier = drawBehind {
    if (color.alpha <= 0f) return@drawBehind
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(radius.toPx().coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        }
        val r = corner.toPx()
        canvas.nativeCanvas.drawRoundRect(0f, y.toPx(), size.width, size.height + y.toPx(), r, r, paint)
    }
}

/** `WyrmSessionTransition`: the W and a shimmer line, for sync and sign-out. */
@Composable
fun WyrmSessionTransition(title: String) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, iosSpring(0.56f, 0.86f)) }
    Box(Modifier.fillMaxSize().background(Wyrm.Paper), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            WyrmBrandMark(
                size = 118.dp,
                layer = {
                    val p = appear.value
                    val s = 0.86f + 0.14f * p
                    scaleX = s; scaleY = s; alpha = p
                    val r = 14.dp.toPx() * (1f - p)
                    renderEffect = if (r > 0.5f && Build.VERSION.SDK_INT >= 31) BlurEffect(r, r, TileMode.Decal) else null
                },
            )
            WorkingStatus(
                title = title,
                modifier = Modifier.graphicsLayer {
                    val p = appear.value
                    alpha = p
                    val r = 8.dp.toPx() * (1f - p)
                    renderEffect = if (r > 0.5f && Build.VERSION.SDK_INT >= 31) BlurEffect(r, r, TileMode.Decal) else null
                },
            )
        }
    }
}

/** `WyrmDesignLaunch`: the W, WYRM in wide caps, a spinner. */
@Composable
fun WyrmDesignLaunch() {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxSize().background(Wyrm.Paper), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WyrmBrandMark(
                size = 108.dp,
                layer = {
                    val s = 0.86f + 0.14f * appear.value
                    scaleX = s; scaleY = s; alpha = appear.value
                },
            )
            Spacer(Modifier.height(18.dp))
            Text("WYRM", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 3.sp, color = Wyrm.Quiet)
            Spacer(Modifier.height(18.dp + 16.dp))
            IosSpinner(size = 20.dp, colour = Wyrm.Ink)
        }
    }
}
