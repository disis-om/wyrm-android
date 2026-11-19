package com.wyrm.omrajput.ui

import androidx.compose.ui.graphics.ImageBitmap

/** Small adapter between AIR skin names and the decoded Wyrm extras atlas. */
internal data class AirArtImage(
    val image: ImageBitmap,
    val pivotX: Float,
    val pivotY: Float,
    val width: Float,
)

/** Installed by WyrmExtras.load after its atlas has been decoded. */
internal object AirArt {
    @Volatile
    var image: ((String) -> AirArtImage?)? = null

    fun get(name: String): AirArtImage? = image?.invoke(name)
}
