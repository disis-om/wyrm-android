package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardShape = wyrmRounded(14.dp)

@Composable
fun GuestSignUpScreen(
    busy: Boolean,
    error: String,
    insetTop: Dp,
    insetBottom: Dp,
    onSubmit: (displayName: String, username: String, password: String) -> Unit,
    onLogIn: () -> Unit,
    onBack: () -> Unit,
) {
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val nameValid = displayName.trim().length >= 2
    val usernameValid = username.length in 3..20 && username.all { it.isLetterOrDigit() || it == '_' }
    val passwordValid = password.length >= 8
    val matches = confirm.isNotEmpty() && confirm == password
    val ready = nameValid && usernameValid && passwordValid && matches && !busy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        PaperBackHeader(title = "New account", insetTop = insetTop, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = keyboardRoom(insetBottom) + 16.dp),
        ) {
            Text(
                text = "No Google.",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = Wyrm.Ink,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp),
            )
            Text(
                text = "A username is how you sign back in. There is no email, so there is no password reset.",
                fontFamily = Wyrm.Body,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = Wyrm.Mute,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 7.dp),
            )
            Column(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp)
                    .clip(CardShape)
                    .background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, CardShape),
            ) {
                GuestField("Display name", displayName) { displayName = it.take(40) }
                Hair()
                GuestField("Username", username, prefix = "@") {
                    username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(20)
                }
                Hair()
                GuestField("Password", password, masked = true) { password = it.take(200) }
                Hair()
                GuestField("Password again", confirm, masked = true) { confirm = it.take(200) }
                if (confirm.isNotEmpty() && !matches) {
                    ErrorDot("These do not match.")
                }
                if (error.isNotEmpty()) ErrorDot(error)
            }
            Box(modifier = Modifier.padding(16.dp)) {
                PaperPrimaryButton(
                    label = if (busy) "Creating…" else "Create account",
                    enabled = ready,
                    onClick = { onSubmit(displayName.trim(), username, password) },
                )
            }
            Text(
                text = "Already have an account? Sign in",
                fontFamily = Wyrm.Body,
                fontSize = 14.5.sp,
                color = Wyrm.Link,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onLogIn)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
fun GuestLogInScreen(
    busy: Boolean,
    error: String,
    insetTop: Dp,
    insetBottom: Dp,
    onSubmit: (username: String, password: String) -> Unit,
    onGoogle: () -> Unit,
    onBack: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val ready = username.isNotBlank() && password.isNotEmpty() && !busy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        PaperBackHeader(title = "Sign in", insetTop = insetTop, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = keyboardRoom(insetBottom) + 16.dp),
        ) {
            Text(
                text = "Welcome back.",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = Wyrm.Ink,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp),
            )
            Text(
                text = "Guest accounts sign in with the username they chose. If you signed up with Google, use that button instead.",
                fontFamily = Wyrm.Body,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = Wyrm.Mute,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 7.dp),
            )
            Column(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp)
                    .clip(CardShape)
                    .background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, CardShape),
            ) {
                GuestField("Username", username, prefix = "@") {
                    username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(20)
                }
                Hair()
                GuestField(
                    label = "Password",
                    value = password,
                    masked = !showPassword,
                    trailing = if (showPassword) "Hide" else "Show",
                    onTrailing = { showPassword = !showPassword },
                    onValueChange = { password = it.take(200) },
                )
                if (error.isNotEmpty()) ErrorDot(error)
            }
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                PaperPrimaryButton(
                    label = if (busy) "Signing in…" else "Sign in",
                    enabled = ready,
                    onClick = { onSubmit(username, password) },
                )
            }
            Row(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(Wyrm.Rule))
                Text(
                    text = "or",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Box(Modifier.weight(1f).height(1.dp).background(Wyrm.Rule))
            }
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp)) {
                GoogleOutlineButton(
                    label = "Continue with Google",
                    enabled = !busy,
                    onClick = onGoogle,
                )
            }
            Text(
                text = "No account yet? Any of these makes one. Nothing to choose.",
                fontFamily = Wyrm.Body,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = Wyrm.Quiet,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp),
            )
        }
    }
}

@Composable
private fun PaperBackHeader(title: String, insetTop: Dp, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = insetTop)
            .height(52.dp)
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = "‹ Back",
            fontFamily = Wyrm.Body,
            fontSize = 16.sp,
            color = Wyrm.Link,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onBack)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Text(
            text = title,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = Wyrm.Ink,
            modifier = Modifier.align(Alignment.Center),
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
}

@Composable
private fun GuestField(
    label: String,
    value: String,
    prefix: String = "",
    masked: Boolean = false,
    trailing: String? = null,
    onTrailing: (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = wyrmRounded(10.dp)
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet, modifier = Modifier.weight(1f))
            if (trailing != null && onTrailing != null) {
                Text(
                    text = trailing,
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Link,
                    modifier = Modifier.clickable(onClick = onTrailing).padding(4.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .padding(top = 7.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(shape)
                .background(Wyrm.Card)
                .border(1.dp, if (focused) Wyrm.Ink else Wyrm.Rule, shape)
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
                textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 16.sp, color = Wyrm.Ink),
                cursorBrush = SolidColor(Wyrm.Ink),
                visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Hair() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
}

@Composable
private fun ErrorDot(text: String) {
    Row(
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(end = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Wyrm.Badge),
        )
        Text(text = text, fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Badge)
    }
}
