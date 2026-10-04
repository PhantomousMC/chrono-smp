package com.chronosmp.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeAmountTest {
    @Test
    fun parsesBareValuesAsMinutesAndSupportsUnitSuffixes() {
        assertEquals(60L, TimeAmount.parseSeconds("1"))
        assertEquals(1L, TimeAmount.parseSeconds("1s"))
        assertEquals(60L, TimeAmount.parseSeconds("1m"))
        assertEquals(3_600L, TimeAmount.parseSeconds("1h"))
        assertEquals(86_400L, TimeAmount.parseSeconds("1d"))
        assertEquals(7_200L, TimeAmount.parseSeconds(" 2H "))
    }

    @Test
    fun permitsZeroButRejectsNegativeInvalidAndOverflowingAmounts() {
        assertEquals(0L, TimeAmount.parseSeconds("0"))
        assertNull(TimeAmount.parseSeconds("-1"))
        assertNull(TimeAmount.parseSeconds("1w"))
        assertNull(TimeAmount.parseSeconds("1.5h"))
        assertNull(TimeAmount.parseSeconds("9223372036854775807d"))
    }
}