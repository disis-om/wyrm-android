package com.wyrm.omrajput.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object FriendPresence {
    /** playerId -> activity, for the friends the server told about. */
    val friends = mutableStateMapOf<String, FriendActivity>()

    /** "Show my activity to friends" as the account has it. */
    var sharing by mutableStateOf(true)

    /** "Arena 4817" for an address the directory knows, else the address. Set by the overlay. */
    var arenaName: (String) -> String = { it }

    private val lock = Mutex()
    private var lastAt = 0L

    fun of(playerId: String?): FriendActivity? = playerId?.let { friends[it] }

    /** At most every 15 s unless [force]: the lists poll while they are open. */
    suspend fun refresh(repository: WyrmRepository, force: Boolean = false) {
        if (!repository.hasSession) return
        lock.withLock {
            val now = System.currentTimeMillis()
            if (!force && now - lastAt < 15_000L) return
            lastAt = now
            val (shared, rows) = runCatching { repository.friendsPresence() }.getOrElse { return }
            sharing = shared
            val ids = rows.map { it.playerId }.toSet()
            friends.keys.filter { it !in ids }.forEach { friends.remove(it) }
            rows.forEach { friends[it.playerId] = it }
        }
    }

    fun reset() {
        friends.clear()
        sharing = true
        lastAt = 0L
    }
}
