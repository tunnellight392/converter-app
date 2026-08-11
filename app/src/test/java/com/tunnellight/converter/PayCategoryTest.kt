package com.tunnellight.converter

import com.tunnellight.converter.model.UnitsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Pay category converts against an adjustable work week, so check both ends of the rebuild. */
class PayCategoryTest {

    private val pay = requireNotNull(UnitsRepository.categoryById("pay"))
    private val hourly = pay.units.first { it.name == "Hourly" }
    private val yearly = pay.units.first { it.name == "Yearly" }

    @Test
    fun defaultsToA40HourWeek() {
        assertEquals(2080.0, hourly.convert(1.0, yearly), 1e-9)
        assertEquals("Conversions assume a 40-hour work week (2080 hours per year).", pay.note)
    }

    @Test
    fun rebuildsForAShorterWeek() {
        val part = requireNotNull(pay.param).variantFor(37.5)
        val partHourly = part.units.first { it.name == "Hourly" }
        val partYearly = part.units.first { it.name == "Yearly" }

        assertEquals(1950.0, partHourly.convert(1.0, partYearly), 1e-9)
        assertEquals(20.0, partYearly.convert(39000.0, partHourly), 1e-9)
        assertEquals("Conversions assume a 37.5-hour work week (1950 hours per year).", part.note)
    }

    @Test
    fun monthlyIsUnaffectedByTheWorkWeek() {
        val monthly = pay.units.first { it.name == "Monthly" }
        assertEquals(12000.0, monthly.convert(1000.0, yearly), 1e-9)
    }
}
