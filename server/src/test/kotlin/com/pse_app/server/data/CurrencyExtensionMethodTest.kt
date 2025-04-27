package com.pse_app.server.data

import org.junit.jupiter.api.Test
import java.util.Currency
import kotlin.test.assertEquals

object CurrencyExtensionMethodTest {
    
    @Test
    fun effective_fraction_digits_works_on_multiple_currencies() {
        assertEquals(2, Currency.getInstance("XXX").effectiveFractionDigits, "Wrong effective fraction digits for currency XXX")
        assertEquals(2, Currency.getInstance("USD").effectiveFractionDigits, "Wrong effective fraction digits for currency USD")
        assertEquals(2, Currency.getInstance("EUR").effectiveFractionDigits, "Wrong effective fraction digits for currency EUR")
        assertEquals(0, Currency.getInstance("JPY").effectiveFractionDigits, "Wrong effective fraction digits for currency JPY")
        assertEquals(3, Currency.getInstance("TND").effectiveFractionDigits, "Wrong effective fraction digits for currency TND")
    }
}
