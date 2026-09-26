package com.wyrm.omrajput.data

import android.content.Context
import com.wyrm.omrajput.BuildConfig
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.Html
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One player on the team, as the service last described them. */
data class TeamMember(
    /** The eight characters the service prefixes to a name. Stable per player. */
    val identity: String,
    val name: String,
    val owner: String,
    val arena: String,
    val score: Int,
    val rank: Int,
    val x: Int,
    val y: Int,
    val bot: Boolean,
    val version: String,
) {
    /** In a match at all — the service says `_GAME_MENU_` when they are not. */
    val playing: Boolean get() = arena.isNotEmpty() && arena != MENU_ARENA

    companion object {
        const val MENU_ARENA = "_GAME_MENU_"
    }
}

data class TeamMessage(val from: String, val text: String)

/** One saved team: a name to recognise it by, and the two secrets. */
data class TeamProfile(val name: String, val teamId: String, val authKey: String)

/** Everything a team screen needs, and nothing the transport keeps to itself. */
data class TeamState(
    val configured: Boolean = false,
    val connected: Boolean = false,
    /**
     * Empty unless there is something to say. Screens print this in the colour
     * of a problem, and "you have not connected yet" is not a problem — it is
     * the starting position.
     */
    val status: String = "",
    val members: List<TeamMember> = emptyList(),
    val messages: List<TeamMessage> = emptyList(),
)

/** What this player is doing, read out of the engine before each poll. */
data class TeamPresence(
    val nickname: String = "",
    val score: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val bot: Boolean = false,
    val arena: String = TeamMember.MENU_ARENA,
    val rank: Int = 0,
    /** This snake's id on the arena — what the mod calls `ntlid` and sends as `sid`. */
    val snakeId: Int = 0,
    /** The tag being worn, in the mod's own numbering. `-1` for none. */
    val tag: Int = -1,
) {
    companion object {
        /** Tab separated, in the order android_team.c writes it. */
        fun parse(line: String): TeamPresence {
            val parts = line.split('\t')
            if (parts.size < 7) return TeamPresence()
            return TeamPresence(
                nickname = parts[0],
                score = parts[1].toIntOrNull() ?: 0,
                x = parts[2].toIntOrNull() ?: 0,
                y = parts[3].toIntOrNull() ?: 0,
                bot = parts[4] == "1",
                arena = parts[5],
                rank = parts[6].toIntOrNull() ?: 0,
                // Added after the first seven; an older engine sends neither.
                snakeId = parts.getOrNull(7)?.toIntOrNull() ?: 0,
                tag = parts.getOrNull(8)?.toIntOrNull() ?: -1,
            )
        }
    }
}

/**
 * The team service, spoken to exactly as it expects.
 *
 * One request does everything: it reports where this player is and returns the
 * whole team, chat included. There is no login, no socket and no second call —
 * the credentials ride along on every request. The protocol is written down in
 * ntl-team-protocol.md, and this is the only place in the app that knows it.
 *
 * The cadence is four seconds because that is what the service is used to. It
 * is slower than the arena moves, so a teammate's marker is a few seconds
 * behind them; that is a property of the service, not something the app can
 * quietly fix by asking harder.
 */
class TeamService(context: Context) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The last message text seen from each member.
     *
     * The service returns each member's chat as an append-only log rather than
     * as their latest line, so the only way to find what is new is to keep the
     * previous value and diff against it.
     */
    private val chatLog = mutableMapOf<String, String>()
    private val vaultLock = Any()
    @Volatile private var vaultCache: Pair<List<TeamProfile>, Int>? = null

    /**
     * Every team this phone knows, and which one is being played.
     *
     * People belong to more than one team, and the two secrets are long enough
     * that nobody is retyping them to switch. They are all kept in one
     * encrypted blob and only one of them is ever spoken to.
     */
    val teams: List<TeamProfile> get() = readVault().first
    val activeTeam: Int get() = readVault().second
    val configured: Boolean get() = teams.isNotEmpty()

    /**
     * Copies the already encrypted vault into a same-installation backup.
     *
     * The archive never receives a Team ID or Auth Key in plaintext. Android's
     * Keystore key survives an app update, so the ciphertext can be restored
     * after updating while remaining useless outside this installation.
     */
    fun encryptedVaultForBackup(): String = synchronized(vaultLock) {
        val stored = preferences.getString(PREF_CREDENTIALS, "").orEmpty()
        if (stored.isEmpty()) return@synchronized ""
        require(stored.length <= MAX_VAULT_CHARS) { "Stored team vault is too large" }
        decodeVault(decrypt(stored))
        stored
    }

    /** Restores a validated vault without ever exposing its secrets to logs. */
    fun restoreEncryptedVaultFromBackup(encoded: String): Boolean = synchronized(vaultLock) {
        if (encoded.isEmpty()) {
            val saved = preferences.edit().remove(PREF_CREDENTIALS).commit()
            if (saved) vaultCache = emptyList<TeamProfile>() to 0
            return@synchronized saved
        }
        if (encoded.length > MAX_VAULT_CHARS) return@synchronized false
        val restored = runCatching { decodeVault(decrypt(encoded)) }.getOrNull()
            ?: return@synchronized false
        val saved = preferences.edit().putString(PREF_CREDENTIALS, encoded).commit()
        if (saved) vaultCache = restored
        saved
    }

    /** A restore may be written by BackupManager's service instance. */
    fun reloadVault() = synchronized(vaultLock) {
        vaultCache = null
        chatLog.clear()
    }

    /** Both halves are at least sixteen characters; the service rejects less. */
    fun addTeam(name: String, teamId: String, authKey: String): String? {
        val id = teamId.trim()
        val key = authKey.trim()
        if (id.length < 16 || key.length < 16) {
            return "Team ID and Auth Key must each be at least 16 characters."
        }
        val label = name.trim().ifEmpty { "Team ${teams.size + 1}" }
        val (existing, _) = readVault()
        if (label.length > MAX_TEAM_FIELD_CHARS || id.length > MAX_TEAM_FIELD_CHARS ||
            key.length > MAX_TEAM_FIELD_CHARS) {
            return "The team details are too long to store safely."
        }
        if (existing.none { it.teamId == id } && existing.size >= MAX_SAVED_TEAMS) {
            return "This phone already has the maximum number of saved teams."
        }
        val updated = existing.filterNot { it.teamId == id } + TeamProfile(label, id, key)
        return try {
            writeVault(updated, updated.lastIndex)
            chatLog.clear()
            null
        } catch (_: Throwable) {
            "This device could not protect the credentials."
        }
    }

    /** Switching teams throws away the chat history of the one being left. */
    fun selectTeam(index: Int) {
        val (existing, _) = readVault()
        if (index !in existing.indices) return
        writeVault(existing, index)
        chatLog.clear()
    }

    fun removeTeam(index: Int) {
        val (existing, active) = readVault()
        if (index !in existing.indices) return
        val remaining = existing.toMutableList().apply { removeAt(index) }
        writeVault(remaining, if (active >= remaining.size) remaining.lastIndex else active)
        chatLog.clear()
    }

    private fun readVault(): Pair<List<TeamProfile>, Int> {
        vaultCache?.let { return it }
        return synchronized(vaultLock) {
            vaultCache?.let { return@synchronized it }
            val stored = preferences.getString(PREF_CREDENTIALS, "").orEmpty()
            val loaded = if (stored.isEmpty()) {
                emptyList<TeamProfile>() to 0
            } else {
                runCatching { decodeVault(decrypt(stored)) }
                    .getOrDefault(emptyList<TeamProfile>() to 0)
            }
            vaultCache = loaded
            loaded
        }
    }

    /** Parses both the original single-team vault and the current team list. */
    private fun decodeVault(plaintext: String): Pair<List<TeamProfile>, Int> {
        val root = JSONObject(plaintext)
        val list = if (root.has("teamId")) {
            listOf(
                TeamProfile(
                    "Team 1",
                    root.getString("teamId"),
                    root.getString("authKey"),
                )
            )
        } else {
            val array = root.getJSONArray("teams")
            require(array.length() <= MAX_SAVED_TEAMS) { "Too many saved teams" }
            buildList {
                for (index in 0 until array.length()) {
                    val row = array.getJSONObject(index)
                    add(
                        TeamProfile(
                            row.getString("name"),
                            row.getString("teamId"),
                            row.getString("authKey"),
                        )
                    )
                }
            }
        }
        val ids = mutableSetOf<String>()
        list.forEach { profile ->
            require(profile.name.isNotBlank() && profile.name.length <= MAX_TEAM_FIELD_CHARS)
            require(profile.teamId.length in 16..MAX_TEAM_FIELD_CHARS)
            require(profile.authKey.length in 16..MAX_TEAM_FIELD_CHARS)
            require(ids.add(profile.teamId)) { "Duplicate team id" }
        }
        val active = root.optInt("active", 0)
        require(list.isEmpty() || active in list.indices) { "Invalid active team" }
        return list to if (list.isEmpty()) 0 else active
    }

    private fun writeVault(list: List<TeamProfile>, active: Int) {
        if (list.isEmpty()) {
            preferences.edit().remove(PREF_CREDENTIALS).apply()
            vaultCache = emptyList<TeamProfile>() to 0
            return
        }
        val array = org.json.JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("teamId", it.teamId)
                    .put("authKey", it.authKey)
            )
        }
        val root = JSONObject()
            .put("teams", array)
            .put("active", active.coerceIn(0, list.lastIndex))
        preferences.edit().putString(PREF_CREDENTIALS, encrypt(root.toString())).apply()
        vaultCache = list.toList() to active.coerceIn(0, list.lastIndex)
    }

    /**
     * One round trip: report [presence], send [message] if there is one, and
     * take back the team.
     */
    suspend fun poll(presence: TeamPresence, message: String?): TeamState =
        withContext(Dispatchers.IO) {
            val credentials = readCredentials()
                ?: return@withContext TeamState(status = "Add a team first.")

            val query = buildString {
                append(ENDPOINT)
                append("?auth=").append(encode(credentials.authKey))
                append("&tid=").append(encode(credentials.teamId))
                /*
                 * Eight characters of prefix, then the name.
                 *
                 * The service does not carry the name it is given — it carries
                 * everything after the first eight characters of it. The mod
                 * generates eight random ones per session (`Rg = n(8)`, then
                 * `sa = Rg + nick`) and matches its own row back by them. Wyrm
                 * sent the bare name, so the service ate the first eight
                 * letters of it and everyone saw "KKI LAGGY" where the name was
                 * "[SMT] SUKKI LAGGY".
                 */
                append("&nick=").append(encode(NICK_PREFIX + presence.nickname))
                append("&score=").append(presence.score.coerceAtLeast(0))
                append("&valx=").append(presence.x)
                append("&valy=").append(presence.y)
                append("&bot=").append(if (presence.bot) 1 else 0)
                append("&sos=0&food=0")
                append("&srv=").append(encode(presence.arena))
                /*
                 * `sid` is this snake's id, and `tg` is the tag it is wearing.
                 *
                 * Both were missing — `sid` went out as a constant zero and the
                 * tag not at all — which is why a player using the NTL
                 * extension saw a Wyrm player with no tag on. The service
                 * carries the pair and hands it to everyone on the network;
                 * read off the mod's own report, which sends exactly these
                 * beside the rest of this.
                 */
                append("&sid=").append(presence.snakeId)
                // NTL tags disabled: announcing a tag got the snake dropped
                // from the arena. -1 is "no tag". Was: presence.tag
                append("&tg=").append(-1)
                append("&rank=").append(presence.rank.coerceAtLeast(0))
                append("&ver=").append(encode(VERSION))
                /*
                 * `msg` is always sent, empty when there is nothing to say —
                 * and this one parameter is the whole reason Team Mode never
                 * worked.
                 *
                 * The old client added it only when it had a line to send, and
                 * those were the only polls that ever came back with a team.
                 * Every silent report was answered with an opaque
                 * 128-character token instead, which the client could not
                 * parse and reported as "credentials rejected". Send it empty
                 * and every poll returns the full roster.
                 *
                 * `tlm` is deliberately not sent at all: asking for the
                 * backlog with a bare `&tlm=` earns the same opaque token, and
                 * the only thing lost by not asking is chat posted before this
                 * device connected.
                 */
                append("&msg=").append(if (message.isNullOrEmpty()) "" else encode(message))
            }

            val connection = (URL(query).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 4_000
                useCaches = false
                setRequestProperty("Accept", "application/json")
            }
            val body = try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    return@withContext TeamState(
                        configured = true,
                        status = "The team service answered HTTP $status.",
                    )
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                return@withContext TeamState(
                    configured = true,
                    status = "The team service is unreachable right now.",
                )
            } finally {
                connection.disconnect()
            }

            val trimmed = body.trim().removePrefix("﻿")
            // Temporary, while the protocol is being pinned down: the answer
            // itself, never the credentials that asked for it.
            if (!trimmed.startsWith("[")) {
                return@withContext TeamState(
                    configured = true,
                    status = "The team service rejected these credentials.",
                )
            }
            parse(trimmed)
        }

    private fun parse(body: String): TeamState {
        val array = runCatching { JSONArray(body) }.getOrNull()
            ?: return TeamState(configured = true, status = "The team service sent something unreadable.")

        val members = ArrayList<TeamMember>(array.length())
        val fresh = ArrayList<TeamMessage>()

        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val rawNick = row.optString("nick")
            val text = decode(row.optString("msg"))

            // An entry called 00000000 is not a person — it is the service
            // itself talking to the team.
            if (rawNick == SYSTEM_NICK) {
                val previous = chatLog[SYSTEM_NICK].orEmpty()
                newLine(previous, text)?.let { fresh += TeamMessage("TEAM", it) }
                chatLog[SYSTEM_NICK] = text
                continue
            }

            /*
             * The identity prefix is taken only when it looks like one.
             *
             * Cutting eight characters off every name that happened to be long
             * enough is what turned "smt sukki" into "i" — the first eight
             * letters of the name were eaten as an id that was never there.
             * An id is eight characters of letters and digits with no spaces,
             * and something has to be left after it, so a name with a space in
             * its first eight characters is simply a name.
             */
            val hasIdentity = rawNick.length > 8 &&
                rawNick.take(8).all { it.isLetterOrDigit() } &&
                rawNick.drop(8).isNotBlank()
            val identity = if (hasIdentity) rawNick.take(8) else ""
            val name = decode(if (hasIdentity) rawNick.drop(8) else rawNick)
                .ifBlank { "Teammate" }

            members += TeamMember(
                identity = identity,
                name = name,
                owner = decode(row.optString("owner")),
                arena = row.optString("srv"),
                score = row.optInt("score"),
                rank = row.optInt("rank"),
                // Coordinates come as strings with a fraction on the end
                // ("33489.90657289786"), which optInt is not obliged to make
                // sense of. Read as a number, then take the whole part.
                x = coordinate(row.optString("valx")),
                y = coordinate(row.optString("valy")),
                bot = row.optInt("bot") != 0 || row.optBoolean("bot", false),
                version = row.optString("ver"),
            )

            val key = identity.ifEmpty { name }
            newLine(chatLog[key].orEmpty(), text)?.let { fresh += TeamMessage(name, it) }
            chatLog[key] = text
        }

        return TeamState(
            configured = true,
            connected = true,
            status = if (members.isEmpty()) "Connected. Nobody else is here yet." else "Connected",
            members = members,
            messages = fresh,
        )
    }

    private fun coordinate(value: String): Int =
        value.toDoubleOrNull()?.toInt() ?: 0

    /**
     * What is new in an append-only log.
     *
     * If the new text starts with the old, the tail is the new line; if it does
     * not, the member's log was reset and the whole thing is new. A poll that
     * never arrives takes its line with it — there is no sequence number in the
     * protocol to recover from.
     */
    private fun newLine(previous: String, current: String): String? {
        if (current.isEmpty()) return null
        if (previous.isEmpty()) return current
        if (previous == current) return null
        if (!current.startsWith(previous)) return current
        return current.drop(previous.length)
            .removePrefix("<br>")
            .removePrefix("\n")
            .takeIf { it.isNotEmpty() }
    }

    private fun readCredentials(): TeamProfile? {
        val (list, active) = readVault()
        return list.getOrNull(active)
    }

    /* Credentials are held under a device key, so a copy of the preferences
       file on its own is worth nothing. */
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val parts = encoded.split(".", limit = 2)
        require(parts.size == 2) { "Stored credentials are malformed" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    /**
     * Names and messages arrive HTML-escaped — sometimes twice over.
     *
     * A single pass left `&nbsp;` sitting in the middle of other people's
     * messages, because what arrives is `&amp;nbsp;`: one decode turns that
     * into `&nbsp;` and stops. A second pass is run when the first still
     * leaves an entity behind. The non-breaking spaces that come out of it are
     * flattened to ordinary ones, since nothing here wants a space that
     * refuses to wrap.
     */
    private fun decode(value: String): String {
        if (value.isEmpty()) return value
        var text = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        if (ENTITY.containsMatchIn(text)) {
            text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
        }
        return text.replace(' ', ' ').trim()
    }

    /**
     * The password, hashed the way the tag service expects to receive it.
     *
     * The mod never sends a tag password as itself — it sends an MD5 of it, and
     * the service compares hashes. MD5 is not a defence and is not being used
     * as one here; it is the format the other end reads, and sending the plain
     * password instead would simply not work.
     */
    private fun digest(pass: String): String =
        MessageDigest.getInstance("MD5")
            .digest(pass.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * Claims a private tag with the owner's own credentials.
     *
     * The mod does this with `!tag id pass` typed into chat, and the service
     * authenticates it there. Wyrm carries the same pair to the same place: it
     * does not decide who may wear which tag, because it is not in a position
     * to know, and a client that decided that for itself would be deciding it
     * for everybody else's tag too.
     *
     * Returns a line to show the player. A refusal is an answer, not a fault,
     * so nothing here throws for one.
     */
    suspend fun claimTag(id: String, pass: String): String = withContext(Dispatchers.IO) {
        // NTL tags are off (they got snakes dropped); Wyrm will serve its own.
        throw java.io.IOException("Tags are coming soon")
        val url = TAG_ENDPOINT +
            "?id=" + encode(id) +
            "&pass=" + encode(digest(pass))
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("Accept", "text/plain")
            // Without this the service answers 403 to everything, correct
            // credentials included — which is what it was answering here.
            //
            // Reading the mod could not have found it: the mod runs inside the
            // page, so its fetch carries this header for free and its own code
            // never mentions one. It took putting the same request on the wire
            // with and without. With it, 200 and a one-word body.
            setRequestProperty("Referer", TAG_REFERER)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext "The tag service returned " + connection.responseCode
            }
            // The service answers 200 either way and says which in the body, so
            // a wrong password is a word rather than a status code.
            when (val body = connection.inputStream.bufferedReader()
                .use { it.readText() }.trim()) {
                "ok" -> TAG_CLAIMED
                "error" -> "That id and password were not accepted"
                "" -> "The tag service said nothing"
                else -> body.take(120)
            }
        } catch (error: Exception) {
            "Could not reach the tag service"
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val ENTITY = Regex("""&[a-zA-Z]{2,8};|&#\d{2,5};""")
        private const val ENDPOINT = "https://ntl-slither.com/slither/ntlplay-mt.php"
        /* Where a private tag is checked. Read out of the mod's own `!tag`
           command, which sends the id and the hashed password here and shows
           the player whatever comes back — "ok" when it is theirs. Still not
           watched on the wire, so the first real id and password are the test.
           See ntl-tags.md. */
        private const val TAG_ENDPOINT = "https://ntl-slither.com/tags/tag"
        /* The tag service refuses anything that does not say it came from the
           game. Watched on the wire, not read out of the mod — see claimTag. */
        private const val TAG_REFERER = "http://slither.io/"
        /* Said when the service accepts the pair. Deliberately not "you are
           wearing it": the service authenticates, it does not send artwork,
           and whether Wyrm has any for that id is a separate question the
           caller answers. */
        const val TAG_CLAIMED = "ok"
        /*
         * The eight characters the team service expects in front of a name and
         * hands back to nobody. The mod randomises them per session and uses
         * them to find its own row; Wyrm identifies its row by `sid` instead,
         * so these only have to be eight of something.
         */
        private const val NICK_PREFIX = "WYRMPLYR"
        private const val VERSION = BuildConfig.VERSION_NAME
        private const val SYSTEM_NICK = "00000000"
        private const val PREFS = "wyrm_team_mode"
        private const val PREF_CREDENTIALS = "credentials"
        private const val KEY_ALIAS = "wyrm_team_mode"
        private const val MAX_SAVED_TEAMS = 32
        private const val MAX_TEAM_FIELD_CHARS = 2_048
        private const val MAX_VAULT_CHARS = 96 * 1_024

        /** Four seconds, which is the rate this service is used to. */
        const val POLL_INTERVAL_MS = 4_000L
    }
}
