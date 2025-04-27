package com.pse_app.client.ui.view

import android.content.res.Configuration
import android.icu.number.LocalizedNumberFormatter
import android.icu.number.NumberFormatter
import android.icu.text.DecimalFormat
import android.icu.text.NumberFormat
import android.icu.util.Currency
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import com.pse_app.client.R
import com.pse_app.client.ui.theme.greenColor
import com.pse_app.common.util.BigDec
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.max


private const val INPUT_LOG_TAG = "MonetaryInputField"

/** OutlinedTextField for numeric values.
 * Because this does input validation on its own, we do *not*
 * recompose on input changes but track the input string inside
 * the component.
 * */
@Composable
@Suppress("TooGenericExceptionCaught") // Failure in this function is recoverable and should be handled with an alternative result
fun MonetaryOutlinedTextField(
    modifier: Modifier = Modifier,
    initialValue: BigDec? = null,
    onValueChange: (BigDec) -> Unit,
    nonNegative: Boolean = false,
    label: @Composable() (() -> Unit)? = null,
    currency: java.util.Currency?,
) {
    val curr = currency?.let(Currency::fromJavaCurrency)

    val config = LocalConfiguration.current

    val formatter = rememberSaveable {
        val locale = getLocale(config)
        NumberFormat.getNumberInstance(locale) as? DecimalFormat ?: DecimalFormat()
    }

    val symbols = formatter.decimalFormatSymbols

    val groupingSeparator = symbols.groupingSeparatorString
    // We only support single character symbols for the input, since
    // multi-character symbols require intermediate invalid input.
    val decSeparator = symbols.decimalSeparator
    val plusSign = symbols.plusSign
    val minusSign = symbols.minusSign

    val fractionalDigits = curr?.defaultFractionDigits

    val validPattern = rememberSaveable {
        val (sep, plus, minus) = listOf(decSeparator, plusSign, minusSign).map {
            Regex.escape(it.toString())
        }

        // An optional sign
        val signPat = "(?:$plus|$minus)?"

        // An integer part, consisting of a sequence of digits without grouping symbols.
        // grouping symbols are only displayed by the visual transformation.
        val intPat = "\\d*"

        // A fractional part, consisting of up to the number of fractional digits supported
        // by the currency. A trailing separator is allowed.
        // The locale separator is accepted during input and displayed by visual transformation.
        val maximumFracDigits = fractionalDigits?.toString() ?: ""
        val fracPat = "($sep\\d{0,$maximumFracDigits})?"

        Regex("^(?<SIGN>$signPat)(?<INT>$intPat)(?<FRAC>$fracPat)$")
    }

    val initialBigDecimalValue = initialValue?.value
    val initialStringValue = if (initialBigDecimalValue != null && fractionalDigits != null)
        formatter.format(initialBigDecimalValue.setScale(fractionalDigits, RoundingMode.HALF_UP))
            .replace(groupingSeparator, "")
    else
        ""

    var stringValue: String by rememberSaveable { mutableStateOf(initialStringValue) }

    fun matchValid(value: String): List<MatchGroup>? =
        validPattern.matchEntire(value)?.groups?.let { groups ->
            listOf(
                groups["SIGN"],
                groups["INT"],
                groups["FRAC"],
            ).map { it ?: return null }
        }

    fun onStringValueChange(value: String) {
        if (nonNegative && value.contains(minusSign)) {
            Log.d(INPUT_LOG_TAG, "Rejecting negative '$value'")
            return
        }
        val (sign, int, frac) = matchValid(value)?.map(MatchGroup::value) ?: run {
            Log.d(INPUT_LOG_TAG, "Rejecting '$value' due to mismatch against '$validPattern'")
            return
        }

        stringValue = value
        onValueChange(
            BigDec(
                buildString {
                    append(sign.replace(plusSign, '+').replace(minusSign, '-'))
                    append('0')
                    append(int)
                    // This uses a hardcoded `.` character since this is only passed to the
                    // locale-independent BigDec constructor. Anything that is displayed to the
                    // user should use `decSeparator`
                    append('.')
                    append(frac.removePrefix(decSeparator.toString()))
                }
            )
        )
    }

    val labelStyle = SpanStyle(color = OutlinedTextFieldDefaults.colors().disabledLabelColor)

    fun visualTransformation(text: AnnotatedString) = try {
        val (sign, int, frac) = matchValid(text.text)!!.map {
            text.subSequence(it.range.first, it.range.last + 1)
        }

        val originalToTransformed = mutableListOf<Int>()
        val transformedToOriginal = mutableListOf<Int>()

        // Build the string that will be in the UI while also remembering which
        // offsets of the transformed string correspond to which offsets in the
        // original string, and vice versa.
        val formatted = buildAnnotatedString {
            fun AnnotatedString.annotatedChar(index: Int): AnnotatedString =
                subSequence(index, index + 1)

            fun smartAppend(text: AnnotatedString) {
                val o2t = originalToTransformed.size
                val t2o = transformedToOriginal.size
                text.indices.forEach { index ->
                    transformedToOriginal.add(o2t + index)
                    originalToTransformed.add(t2o + index)
                }
                append(text)
            }

            fun phantomAppend(char: Char) {
                transformedToOriginal.add(originalToTransformed.size)
                withStyle(labelStyle) {
                    append(char)
                }
            }

            smartAppend(sign)

            if (int.isEmpty()) {
                phantomAppend('0')
            } else {
                // format the integer to the locale's number format with grouping
                // and track where we added extra characters
                val formattedInt = synchronized(formatter) {
                    formatter.minimumIntegerDigits = int.length
                    formatter.maximumIntegerDigits = int.length
                    formatter.format(BigInteger(int.text))
                }
                formattedInt.forEach { char ->
                    if (char.isDigit()) {
                        smartAppend(text.annotatedChar(originalToTransformed.size))
                    } else {
                        phantomAppend(char)
                    }
                }
            }

            if (frac.isEmpty())
                phantomAppend(decSeparator)

            smartAppend(frac)

            // Unless frac was empty, it includes a decimal separator of length 1
            val addedFracDigits = max(frac.length - 1, 0)

            // add zeros until we hit the standard for the currency
            if (fractionalDigits != null) repeat(fractionalDigits - addedFracDigits) {
                phantomAppend('0')
            }
        }

        check(originalToTransformed.size == text.length)
        check(transformedToOriginal.size == formatted.length)

        originalToTransformed.add(originalToTransformed.lastOrNull()?.plus(1) ?: 0)
        transformedToOriginal.add(text.length)

        TransformedText(
            formatted,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = originalToTransformed[offset]
                override fun transformedToOriginal(offset: Int) = transformedToOriginal[offset]
            }
        )
    } catch (ex: Exception) {
        Log.e(INPUT_LOG_TAG, "Visual Transformation Failed", ex)
        defaultValuePlaceHolderTransformation("0", labelStyle.color).filter(text)
    }

    OutlinedTextField(
        modifier = modifier,
        value = stringValue,
        onValueChange = ::onStringValueChange,
        label = label,
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        singleLine = true,
        visualTransformation = ::visualTransformation,
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
    )
}

@Preview
@Composable
fun MonetaryTextFieldPreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        MonetaryOutlinedTextField(
            modifier = Modifier.fillMaxWidth(.9f),
            onValueChange = {},
            currency = java.util.Currency.getInstance("EUR"),
            label = { Text("Amount") }
        )
    }
}

/**
 * Displays a balance.
 */
@Composable
fun BalanceText(
    balance: BigDec,
    currency: java.util.Currency?,
    absolute: Boolean,
    modifier: Modifier = Modifier,
    colored: Boolean = true,
    fontSize: TextUnit = TextUnit.Unspecified,
    contentAlignment: Alignment = Alignment.TopStart,
) {
    val color = if (!colored || balance == BigDec.ZERO) {
        Color.Unspecified
    } else if (balance.value > BigDec.ZERO.value) {
        greenColor()
    } else {
        Color.Red
    }

    val format = rememberFormatter(currency, absolute)

    TooltippedOverflowingTextBox(
        text = format(balance),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        contentAlignment = contentAlignment,
    )
}

private const val FORMATTER_SDK_THRESHOLD = 30

@Composable
private fun rememberFormatter(
    currency: java.util.Currency?,
    absolute: Boolean
): (BigDec) -> String {
    val config = LocalConfiguration.current
    return remember(config, currency, absolute) {
        val locale = getLocale(config)
        val curr = currency?.let(Currency::fromJavaCurrency) ?: Currency.getInstance(locale)

        if (Build.VERSION.SDK_INT < FORMATTER_SDK_THRESHOLD) {
            val fmt: DecimalFormat =
                NumberFormat.getCurrencyInstance(locale) as? DecimalFormat ?: DecimalFormat()
            fmt.currency = curr

            when {
                absolute -> fun(it: BigDec): String = fmt.format(it.value.abs())
                else -> fun(it: BigDec): String = fmt.format(it.value)
            }
        } else {
            val fmt: LocalizedNumberFormatter = NumberFormatter.with()
                .unit(curr)
                .locale(locale)
                .sign(
                    when {
                        absolute -> NumberFormatter.SignDisplay.NEVER
                        else -> NumberFormatter.SignDisplay.EXCEPT_ZERO
                    }
                )

            fun(it: BigDec) = fmt.format(it.value).toString()
        }
    }
}

private fun getLocale(config: Configuration): Locale =
    ConfigurationCompat.getLocales(config)[0]
        ?: LocaleListCompat.getDefault()[0]
        ?: Locale.getDefault()

/**
 * Get the string prefixing the total for settle up.
 */
@Composable
fun stringForTotal(amount: BigDec): String {
    return if (amount > BigDec(0)) {
        stringResource(R.string.you_receive)
    } else if (amount == BigDec(0)) {
        stringResource(R.string.nothing_to_settle)
    } else {
        stringResource(R.string.you_owe)
    }
}

/**
 * Get the string prefixing the proposed amount for settle up.
 */
@Composable
fun stringForProposed(amount: BigDec): String {
    return if (amount > BigDec(0)) {
        stringResource(R.string.you_receive)
    } else if (amount == BigDec(0)) {
        stringResource(R.string.nothing_to_settle)
    } else {
        stringResource(R.string.you_pay)
    }
}
