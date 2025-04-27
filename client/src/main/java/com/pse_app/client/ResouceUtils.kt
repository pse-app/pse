@file:Suppress("MatchingDeclarationName")
@file:KoverIgnore("UI code")
package com.pse_app.client

import android.content.Context
import com.pse_app.client.annotations.KoverIgnore
import com.pse_app.client.model.exceptions.BadResponseException
import com.pse_app.client.model.exceptions.BalanceNotZeroException
import com.pse_app.client.model.exceptions.InternalException
import com.pse_app.client.model.exceptions.LoginRejectedException
import com.pse_app.client.model.exceptions.ModelException
import com.pse_app.client.model.exceptions.NetworkException
import com.pse_app.client.model.exceptions.NoActiveUserException
import com.pse_app.client.model.exceptions.NotFoundException
import com.pse_app.client.model.exceptions.ReferenceInvalidException
import com.pse_app.client.model.exceptions.ServerErrorException
import com.pse_app.client.model.exceptions.SessionMissingException
import com.pse_app.client.model.exceptions.SessionRejectedException
import com.pse_app.client.model.exceptions.TimeoutException
import com.pse_app.client.model.exceptions.UnexpectedServerException
import com.pse_app.client.ui.view_model.SimpleMessageException
import java.text.MessageFormat

sealed interface StringResource {
    data class RawString(val value: String): StringResource
    data class Localized(val id: StringId): StringResource
    data class Concatenated(val args: List<StringResource>): StringResource
    data class Formatted(val format: StringResource, val args: List<StringResource>): StringResource

    /**
     * Returns a [StringResource] that does formatting using [MessageFormat] syntax.
     */
    fun format(vararg formatArgs: StringResource): StringResource
        = Formatted(this, formatArgs.asList())

    operator fun plus(resource: StringResource): StringResource
        = Concatenated(listOf(this, resource))
}
fun StringResource(value: String): StringResource = StringResource.RawString(value)
fun StringResource(id: StringId): StringResource = StringResource.Localized(id)

typealias StringId = Int
typealias LocalizedStrings = R.string

@Suppress("SpreadOperator")
fun StringResource.getValue(context: Context): String {
    return when (this) {
        is StringResource.RawString -> value
        is StringResource.Localized -> context.getString(id)
        is StringResource.Concatenated -> args.joinToString { it.getValue(context) }
        is StringResource.Formatted -> MessageFormat.format(
            format.getValue(context),
            *args.stream().map { it.getValue(context) }.toArray()
        )
    }
}

@Suppress("CyclomaticComplexMethod") // Method just maps exceptions to error messages
private fun modelExceptionToString(ex: ModelException): StringResource {
    return StringResource(
         when (ex) {
             is UnexpectedServerException -> LocalizedStrings.server_error
             is InternalException -> LocalizedStrings.internal_error
             is LoginRejectedException -> LocalizedStrings.login_rejected_by_server
             is NetworkException -> ex.message?.let { message ->
                 return StringResource("{0}: {1}").format(
                     StringResource(LocalizedStrings.network_error),
                     StringResource.RawString(message)
                 )
             } ?: LocalizedStrings.network_error
             is NoActiveUserException -> LocalizedStrings.missing_active_user
             is ReferenceInvalidException -> LocalizedStrings.invalid_reference
             is SessionRejectedException -> LocalizedStrings.refresh_rejected_by_server
             is SessionMissingException -> LocalizedStrings.missing_authentication
             is BadResponseException -> LocalizedStrings.bad_response
             is BalanceNotZeroException -> {
                 if (ex.isKick) LocalizedStrings.kick_restriction else LocalizedStrings.leave_restriction
             }
             is NotFoundException -> LocalizedStrings.status_not_found
             is ServerErrorException -> LocalizedStrings.server_error
             is TimeoutException -> LocalizedStrings.timeout_error
         }
    )
}

/**
 * Pretty-prints [ModelException]s using their localized messages and makes a
 * best-effort attempt at giving a reasonable representation for external exception classes.
 */
fun prettyPrintException(error: Throwable): StringResource {
    return when (error) {
        is ModelException -> modelExceptionToString(error)
        is SimpleMessageException -> error.messageResource
        else -> StringResource(
            error.localizedMessage ?: error.message ?: error.cause?.message ?: error.toString()
        )
    }
}
