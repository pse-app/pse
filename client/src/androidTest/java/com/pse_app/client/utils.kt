package com.pse_app.client

import kotlin.random.Random

fun getRandomString(length: Int, random: Random) : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random(random) }
        .joinToString("")
}
