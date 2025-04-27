package com.pse_app.client.model

import com.pse_app.client.model.repositories.data.GroupData
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.UserData
import com.pse_app.client.model.repositories.data.UserId
import java.util.UUID
import kotlin.random.Random

fun getRandomString(length: Int, random: Random) : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random(random) }
        .joinToString("")
}

fun getRandomUid(random: Random): UserId {
    return UserId(getRandomString(32, random))
}

fun getRandomGid(random: Random): GroupId {
    return GroupId(UUID.nameUUIDFromBytes(getRandomString(32, random).toByteArray()).toString())
}

fun getRandomUserData(uid: UserId, random: Random): UserData {
    return UserData(uid, getRandomString(10, random), getRandomString(20, random))
}

fun getRandomData(gid: GroupId, random: Random): GroupData {
    return GroupData(
        gid,
        getRandomString(5, random),
        getRandomString(5, random),
        listOf(),
        mapOf(),
        null
    )
}
