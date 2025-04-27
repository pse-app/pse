package com.pse_app.client.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

@Suppress("MagicNumber")
fun getNameColor(userId: String): Color {
    val random = Random(userId.hashCode())
    val col = Color.hsl(
        360 * random.nextFloat(),
        (25 + 70 * random.nextFloat())/100,
        (50 + 10 * random.nextFloat())/100
    )
    return col;
}
