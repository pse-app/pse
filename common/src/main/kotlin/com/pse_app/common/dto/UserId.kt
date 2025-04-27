package com.pse_app.common.dto

import kotlinx.serialization.Serializable

/**
 * Models a user id as retrieved from the Identity Provider.
 * @param id The user id.
 */
@Serializable
@JvmInline
value class UserId(val id: String) {
    override fun toString() = id
}
