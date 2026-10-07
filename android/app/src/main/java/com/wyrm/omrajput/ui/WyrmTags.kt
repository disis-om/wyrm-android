package com.wyrm.omrajput.ui

/*
 * Wyrm's own tags (OM, 2026-10-07): stickers after NTL's 164 in the tag
 * table (tools/wyrm-tag-append.py). Their `ntlId` is WYRM_TAG_BASE + their
 * number, which is never an NTL number, and the number itself travels in the
 * skin block's corner. On the sheet each one is turned 90 degrees
 * anticlockwise so it hangs off the rope like a pendant; a picker turns it
 * back upright.
 */
const val WYRM_TAG_BASE = 100000

fun isWyrmTag(ntlId: Int): Boolean = ntlId >= WYRM_TAG_BASE

/** Whether the tag at this sheet index is one of Wyrm's own. */
fun isWyrmTagIndex(index: Int): Boolean = SkinCatalog.tags.getOrNull(index)?.let { isWyrmTag(it.ntlId) } == true

/** Sheet indices in picker order: Wyrm's own first, then NTL's. */
val TagPickerOrder: List<Int> by lazy {
    SkinCatalog.tags.filter { isWyrmTag(it.ntlId) }.map { it.id } +
        SkinCatalog.tags.filter { !isWyrmTag(it.ntlId) }.map { it.id }
}
