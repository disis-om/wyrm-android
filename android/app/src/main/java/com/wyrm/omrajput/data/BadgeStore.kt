package com.wyrm.omrajput.data

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/*
 * A profile's badges, cached per player (stale-while-revalidate). Moved out
 * of SupportCenter.kt unchanged (2026-10-11) so Wyrm Desktop compiles the
 * same file; SupportCenter.kt holds Android-only crash code.
 */

object BadgeStore {
    var repository: WyrmRepository? = null
    var cache: SocialCache? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val books = mutableStateMapOf<String, BadgeBook>()
    private val loading = mutableSetOf<String>()

    fun load(id: String) {
        val repo = repository ?: return
        if (id.isBlank()) return
        if (books[id] == null) cache?.badges(id)?.let { books[id] = it }
        if (!loading.add(id)) return
        scope.launch {
            try {
                val fresh = repo.badges(id)
                if (books[id] != fresh) books[id] = fresh
                cache?.saveBadges(id, fresh)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
            } finally {
                loading.remove(id)
            }
        }
    }

    fun reset() {
        books.clear()
        loading.clear()
    }
}
