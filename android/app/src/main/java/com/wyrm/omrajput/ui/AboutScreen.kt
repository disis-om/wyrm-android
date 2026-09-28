package com.wyrm.omrajput.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Settings › About Wyrm (OM, 2026-09-28), as Wyrm iOS's `WyrmAboutPage`.
 *
 * The one page that is not paper: a night canvas, whatever the theme, told
 * as a story in chapters down a lit thread — where Wyrm began, what it grew
 * into, who builds it — then the community, a coffee and a way to write.
 * Chapters rise into place as they scroll in. Change both apps together.
 */

internal object AboutInfo {
    const val UPI_ID = "ommanav@fam"
    const val PAYEE = "OM Rajput"
    const val EMAIL = "ommanav.mail@gmail.com"
    const val WEBSITE = "https://www.omrajput.in"
    /** Not published yet: the button says so until a link is set here. */
    val DISCORD_INVITE: String? = null

    val upiPay: Uri
        get() = Uri.Builder().scheme("upi").authority("pay")
            .appendQueryParameter("pa", UPI_ID)
            .appendQueryParameter("pn", PAYEE)
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", "Coffee for Wyrm")
            .build()

    class Product(val name: String, val detail: String, @DrawableRes val icon: Int)

    val ecosystem = listOf(
        Product("Wyrm Android", "Where it all started", LucideR.drawable.lucide_ic_smartphone),
        Product("Wyrm iOS", "The same Wyrm on iPhone", LucideR.drawable.lucide_ic_apple),
        Product("Wyrm Desktop", "For the big screen", LucideR.drawable.lucide_ic_monitor),
        Product("Wyrm Windows", "Native on Windows", LucideR.drawable.lucide_ic_laptop),
        Product("Wyrm Linux", "Native on Linux", LucideR.drawable.lucide_ic_terminal),
        Product("NTL VANCED", "Browser extension, powered by Wyrm", LucideR.drawable.lucide_ic_puzzle),
        Product("Slither for Android", "The original app, modded into Wyrm", LucideR.drawable.lucide_ic_hexagon),
        Product("Slither for iOS", "The original app, modded into Wyrm", LucideR.drawable.lucide_ic_gamepad_2),
    )
}

/**
 * The page's colours, from the player's theme (OM, 2026-09-28: the page must
 * follow the chosen theme). Gold is the page's accent, deeper on light themes
 * so it reads on paper; text on gold is always dark. `WyrmNight` on iOS.
 */
internal object Night {
    val Sky: Color get() = Wyrm.Paper
    val Card: Color get() = Wyrm.Card
    val Rule: Color get() = Wyrm.Rule
    val Ink: Color get() = Wyrm.Ink
    val Mute: Color get() = Wyrm.Mute
    val Quiet: Color get() = Wyrm.Quiet
    val Well: Color get() = Wyrm.Well
    val Gold: Color get() = if (Wyrm.Paper.luminance() < 0.45f) Color(0xFFE3BA6B) else Color(0xFFB07A1E)
    val OnGold = Color(0xFF1C1A16)
    val Discord = Color(0xFF5865F2)
}

@Composable
fun AboutScreen(
    appVersion: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    fun show(text: String) {
        note = text
        scope.launch {
            delay(2_200)
            if (note == text) note = ""
        }
    }

    // A night page: light status-bar icons while it is open, whatever the theme.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }

    Box(Modifier.fillMaxSize().background(Night.Sky)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = insetTop + 44.dp, bottom = insetBottom + 40.dp),
        ) {
            item { Hero() }
            item {
                Chapter("I", "It began as a mod",
                    "Wyrm started as a simple slither.io mod for Android. A few tweaks for my own games, nothing more.")
            }
            item {
                Chapter("II", "Then it grew a name",
                    "I kept building, one feature after another, until the mod was no longer a mod. It became Wyrm: my own brand, a small one, inside the slither community.")
            }
            item {
                Chapter("III", "Then it became an ecosystem",
                    "Wyrm now runs on phones and computers, lives inside the browser, and even the original slither apps on Android and iOS were modded into it.")
            }
            AboutInfo.ecosystem.chunked(2).forEachIndexed { row, pair ->
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 72.dp, end = 22.dp, bottom = 10.dp).height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        pair.forEachIndexed { column, product ->
                            ProductCard(product, highlight = row == 0 && column == 0, delayMs = column * 80,
                                modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            item { Spacer(Modifier.height(26.dp)) }
            item {
                Chapter("IV", "Built by one person",
                    "I'm OM Rajput, and Wyrm is a one-person project. The backend, the apps, the engine work, the design and every release: I build and run all of it myself.",
                    last = true)
            }
            item { Roles() }
            item { Community(onJoin = { AboutInfo.DISCORD_INVITE?.let { open(context, it) } ?: show("The Discord invite is coming soon") }) }
            item {
                Coffee(
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", AboutInfo.UPI_ID))
                        show("UPI ID copied")
                    },
                    onPay = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, AboutInfo.upiPay))
                        } catch (_: ActivityNotFoundException) {
                            show("No UPI app found. Scan the code instead.")
                        }
                    },
                )
            }
            item {
                Contact(
                    onEmail = { open(context, "mailto:${AboutInfo.EMAIL}", Intent.ACTION_SENDTO) },
                    onWebsite = { open(context, AboutInfo.WEBSITE) },
                )
            }
            item { Footer(appVersion) }
        }

        // Header: the way back, over a fade of the night.
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Night.Sky, Night.Sky, Night.Sky.copy(alpha = 0f))))
                .padding(top = insetTop)
                .height(44.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "‹ Settings",
                fontFamily = Wyrm.Body,
                fontSize = 16.sp,
                color = Night.Gold,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }

        AnimatedVisibility(
            visible = note.isNotEmpty(),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = insetTop + 58.dp),
        ) {
            Text(
                note,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = Night.Sky,
                modifier = Modifier.clip(CircleShape).background(Night.Ink).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

private fun open(context: Context, uri: String, action: String = Intent.ACTION_VIEW) {
    try {
        context.startActivity(Intent(action, Uri.parse(uri)))
    } catch (_: ActivityNotFoundException) {
    }
}

/** Rises and fades in the first time it is composed, which in a lazy list is when it scrolls in. */
@Composable
private fun Modifier.rise(delayMs: Int = 0): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessVeryLow))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 26.dp.toPx()
    }
}

@Composable
private fun Hero() {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(240.dp).rise(50), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Night.Gold.copy(alpha = 0.35f), Color.Transparent)),
                ),
            )
            Image(
                painter = painterResource(R.drawable.about_wyrm_mark),
                contentDescription = "Wyrm",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .shadow(24.dp, RoundedCornerShape(26.dp), ambientColor = Night.Gold, spotColor = Night.Gold)
                    .clip(RoundedCornerShape(26.dp)),
            )
        }
        Text("THE STORY OF", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            letterSpacing = 3.sp, color = Night.Gold, modifier = Modifier.rise(150))
        Text("Wyrm", fontFamily = Wyrm.Display, fontSize = 58.sp, color = Night.Ink,
            modifier = Modifier.padding(top = 2.dp).rise(220))
        Text(
            "One developer. One snake game.\nA whole ecosystem.",
            fontFamily = Wyrm.Body, fontSize = 15.sp, lineHeight = 22.sp, color = Night.Mute,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp).rise(300),
        )
        Icon(
            painterResource(LucideR.drawable.lucide_ic_chevron_down), contentDescription = null, tint = Night.Quiet,
            modifier = Modifier.padding(top = 28.dp).size(16.dp).rise(500),
        )
    }
}

/** One chapter on the thread: a numeral on a lit node, the title, the text. */
@Composable
private fun Chapter(numeral: String, title: String, text: String, last: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(IntrinsicSize.Min).rise(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(34.dp)
                    .shadow(10.dp, CircleShape, ambientColor = Night.Gold, spotColor = Night.Gold)
                    .clip(CircleShape)
                    .background(Night.Gold),
                contentAlignment = Alignment.Center,
            ) { Text(numeral, fontFamily = Wyrm.Display, fontSize = 15.sp, color = Night.OnGold) }
            Box(
                Modifier
                    .width(1.5.dp)
                    .weight(1f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Night.Gold.copy(alpha = 0.6f), Night.Gold.copy(alpha = if (last) 0f else 0.15f)),
                        ),
                    ),
            )
        }
        Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 34.dp)) {
            Text("CHAPTER $numeral", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                letterSpacing = 2.sp, color = Night.Quiet)
            Spacer(Modifier.height(8.dp))
            Text(title, fontFamily = Wyrm.Display, fontSize = 27.sp, lineHeight = 32.sp, color = Night.Ink)
            Spacer(Modifier.height(8.dp))
            Text(text, fontFamily = Wyrm.Body, fontSize = 15.sp, lineHeight = 23.sp, color = Night.Mute)
        }
    }
}

@Composable
private fun ProductCard(product: AboutInfo.Product, highlight: Boolean, delayMs: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .rise(delayMs)
            .heightIn(min = 118.dp)
            .clip(shape)
            .background(Night.Card)
            .border(1.dp, if (highlight) Night.Gold.copy(alpha = 0.5f) else Night.Rule, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Night.Well),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(product.icon), contentDescription = null,
                tint = if (highlight) Night.Gold else Night.Ink, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(product.name, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Night.Ink)
            Spacer(Modifier.height(2.dp))
            Text(product.detail, fontFamily = Wyrm.Body, fontSize = 11.5.sp, lineHeight = 15.sp, color = Night.Quiet)
        }
    }
}

@Composable
private fun Roles() {
    Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 22.dp, bottom = 44.dp).rise()) {
        listOf("Backend", "Apps", "Engine", "Design", "Releases", "Support").chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { role ->
                    Text(
                        role,
                        fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Night.Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(CircleShape).background(Night.Card)
                            .border(1.dp, Night.Rule, CircleShape).padding(vertical = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("From the first line of the server to the last pixel of this page.",
            fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Night.Quiet)
    }
}

@Composable
private fun SectionKicker(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(22.dp).height(1.5.dp).background(Night.Gold))
        Spacer(Modifier.width(10.dp))
        Text(text, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
            letterSpacing = 2.4.sp, color = Night.Gold)
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 44.dp).rise(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun Community(onJoin: () -> Unit) = Section {
    SectionKicker("THE COMMUNITY")
    Text("Play with the people\nwho play Wyrm", fontFamily = Wyrm.Display, fontSize = 28.sp, lineHeight = 33.sp, color = Night.Ink)
    Text("Updates first, ideas heard, games together.", fontFamily = Wyrm.Body, fontSize = 14.sp, color = Night.Mute)
    val soon = AboutInfo.DISCORD_INVITE == null
    Row(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .height(56.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = Night.Discord, spotColor = Night.Discord)
            .clip(RoundedCornerShape(16.dp))
            .background(Night.Discord)
            .clickable(onClick = onJoin)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(LucideR.drawable.lucide_ic_messages_square), contentDescription = null, tint = Color.White,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Join the Wyrm Discord", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.5.sp,
            color = Color.White, modifier = Modifier.weight(1f))
        if (soon) {
            Text("SOON", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp,
                color = Color.White,
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 3.dp))
        } else {
            Text("›", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        }
    }
}

@Composable
private fun Coffee(onCopy: () -> Unit, onPay: () -> Unit) = Section {
    SectionKicker("BUY ME A COFFEE")
    Text("Keep the servers warm", fontFamily = Wyrm.Display, fontSize = 28.sp, lineHeight = 33.sp, color = Night.Ink)
    Text(
        "Wyrm is free. If it made your games better, a coffee keeps it going. Scan with any UPI app.",
        fontFamily = Wyrm.Body, fontSize = 14.sp, lineHeight = 20.sp, color = Night.Mute,
    )
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Night.Card)
            .border(1.dp, Night.Gold.copy(alpha = 0.35f), shape)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.about_upi_qr),
            contentDescription = "UPI QR code for ${AboutInfo.UPI_ID}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = 230.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(12.dp),
        )
        Row(
            Modifier
                .height(40.dp)
                .clip(CircleShape)
                .background(Night.Well)
                .clickable(onClick = onCopy)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(AboutInfo.UPI_ID, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Night.Ink)
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(LucideR.drawable.lucide_ic_copy), contentDescription = "Copy UPI ID", tint = Night.Gold,
                modifier = Modifier.size(14.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Night.Gold)
                .clickable(onClick = onPay),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(LucideR.drawable.lucide_ic_coffee), contentDescription = null, tint = Night.OnGold,
                modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pay with a UPI app", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = Night.OnGold)
        }
    }
}

@Composable
private fun Contact(onEmail: () -> Unit, onWebsite: () -> Unit) = Section {
    SectionKicker("SAY HELLO")
    Text("Write to me", fontFamily = Wyrm.Display, fontSize = 28.sp, color = Night.Ink)
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Night.Card).border(1.dp, Night.Rule, shape)) {
        ContactRow(LucideR.drawable.lucide_ic_mail, "EMAIL", AboutInfo.EMAIL, onEmail)
        Box(Modifier.padding(start = 58.dp).fillMaxWidth().height(1.dp).background(Night.Rule))
        ContactRow(LucideR.drawable.lucide_ic_globe, "WEBSITE", "omrajput.in", onWebsite)
    }
}

@Composable
private fun ContactRow(@DrawableRes icon: Int, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = Night.Gold, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Night.Quiet)
            Text(value, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Night.Ink, maxLines = 1)
        }
        Icon(painterResource(LucideR.drawable.lucide_ic_arrow_up_right), contentDescription = null, tint = Night.Quiet,
            modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun Footer(appVersion: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).rise(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.about_wyrm_mark),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).graphicsLayer { alpha = 0.8f },
        )
        Text("Made with care by OM Rajput", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Night.Mute)
        Text("Wyrm $appVersion", fontFamily = Wyrm.Body, fontSize = 11.sp, color = Night.Quiet)
    }
}
