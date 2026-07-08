package com.secondbrain.app.util

import org.junit.Assert.*
import org.junit.Test

class AmountParseTest {

    @Test
    fun angkaPolos() {
        assertEquals(30000.0, AmountParse.parse("30000")!!, 0.0)
    }

    @Test
    fun titikRibuan() {
        assertEquals(30000.0, AmountParse.parse("30.000")!!, 0.0)
        assertEquals(1234567.0, AmountParse.parse("1.234.567")!!, 0.0)
    }

    @Test
    fun komaDesimal() {
        assertEquals(30.5, AmountParse.parse("30,5")!!, 0.0)
    }

    @Test
    fun titikRibuanKomaDesimal() {
        assertEquals(30000.5, AmountParse.parse("30.000,50")!!, 0.0)
    }

    @Test
    fun gayaAmerika_komaRibuanTitikDesimal() {
        assertEquals(30000.5, AmountParse.parse("30,000.50")!!, 0.0)
    }

    @Test
    fun titikDesimal_grupBukanTigaDigit() {
        assertEquals(30.5, AmountParse.parse("30.5")!!, 0.0)
    }

    @Test
    fun prefixRupiahDanSpasi() {
        assertEquals(30000.0, AmountParse.parse("Rp 30.000")!!, 0.0)
        assertEquals(30000.0, AmountParse.parse("rp30.000")!!, 0.0)
    }

    @Test
    fun invalid_null() {
        assertNull(AmountParse.parse(""))
        assertNull(AmountParse.parse("   "))
        assertNull(AmountParse.parse("abc"))
        assertNull(AmountParse.parse("12abc"))
        assertNull(AmountParse.parse("-500")) // nominal negatif ditolak; arah dari jenis keluar/masuk
    }

    @Test
    fun format_bulatTanpaDesimal() {
        assertEquals("30000", AmountParse.format(30000.0))
        assertEquals("30.5", AmountParse.format(30.5))
    }

    @Test
    fun roundTrip_formatLaluParse() {
        for (v in listOf(0.0, 1.0, 30.5, 30000.0, 1234567.0)) {
            assertEquals(v, AmountParse.parse(AmountParse.format(v))!!, 0.0)
        }
    }
}
