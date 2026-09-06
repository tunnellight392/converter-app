package com.tunnellight.converter

import com.tunnellight.converter.model.ClockFormat
import com.tunnellight.converter.model.UnitsRepository
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Clock category is built around the device's own time zone, so the tests pin the default
 * zone first. Conversions are checked between Dubai and Kolkata, which keep a fixed 1.5 hours
 * between them all year and so are not at the mercy of whenever the suite happens to run.
 */
class ClockCategoryTest {

    private val originalZone = TimeZone.getDefault()

    @Before
    fun useAKnownZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    private fun clock() = requireNotNull(UnitsRepository.categoryById("clock"))

    /** Parse text that is expected to read as a time, so the assertions stay on Doubles. */
    private fun parsed(text: String) = requireNotNull(ClockFormat.parse(text))

    @Test
    fun leadsWithTheDeviceZoneAndDoesNotRepeatIt() {
        val clock = clock()
        assertEquals("Local time", clock.units.first().name)
        assertTrue(clock.units.first().symbol.startsWith("New York · UTC-0"))
        // The device is already showing New York time, so the city itself is left out.
        assertTrue(clock.units.drop(1).none { it.name == "New York" })
        assertTrue(clock.units.any { it.name == "Tokyo" })
    }

    @Test
    fun ordersTheOtherZonesWestToEast() {
        val names = clock().units.drop(1).map { it.name }
        assertTrue(names.indexOf("London") < names.indexOf("Kolkata"))
        assertTrue(names.indexOf("Kolkata") < names.indexOf("Tokyo"))
    }

    @Test
    fun convertsBetweenZones() {
        val clock = clock()
        val dubai = clock.units.first { it.name == "Dubai" }
        val kolkata = clock.units.first { it.name == "Kolkata" }

        assertEquals(13.5, dubai.convert(12.0, kolkata), 1e-9)
        assertEquals(12.0, kolkata.convert(13.5, dubai), 1e-9)
    }

    @Test
    fun carriesTimesPastMidnightIntoTheNextOrPreviousDay() {
        val clock = clock()
        val dubai = clock.units.first { it.name == "Dubai" }
        val kolkata = clock.units.first { it.name == "Kolkata" }

        assertEquals("01:00 +1d", ClockFormat.format(dubai.convert(23.5, kolkata)))
        assertEquals("23:00 -1d", ClockFormat.format(kolkata.convert(0.5, dubai)))
    }

    @Test
    fun opensOnATimeOfDay() {
        val opening = requireNotNull(clock().openingValue).invoke()
        assertTrue(opening >= 0.0 && opening < 24.0)
    }

    @Test
    fun formatsHoursAsTimesOfDay() {
        assertEquals("00:00", ClockFormat.format(0.0))
        assertEquals("14:30", ClockFormat.format(14.5))
        assertEquals("08:30 +1d", ClockFormat.format(32.5))
        assertEquals("23:00 -1d", ClockFormat.format(-1.0))
    }

    @Test
    fun readsTimesTypedAnyOfTheUsualWays() {
        assertEquals(9.0, parsed("9"), 1e-9)
        assertEquals(9.0, parsed("9:"), 1e-9)
        assertEquals(9.0 + 5.0 / 60, parsed("09:05"), 1e-9)
        assertEquals(9.0 + 5.5 / 60, parsed("9:05:30"), 1e-9)
        assertEquals(21.0, parsed("9:00 pm"), 1e-9)
        assertEquals(0.5, parsed("12:30 AM"), 1e-9)
        assertEquals(12.5, parsed("12:30 pm"), 1e-9)
        // The day marker the fields display has to read back as the value it was printed from.
        assertEquals(32.5, parsed("08:30 +1d"), 1e-9)
        assertEquals(-1.0, parsed("23:00 -1d"), 1e-9)
    }

    @Test
    fun refusesTextThatIsNotATime() {
        assertNull(ClockFormat.parse(""))
        assertNull(ClockFormat.parse("noon"))
        assertNull(ClockFormat.parse("24:00"))
        assertNull(ClockFormat.parse("9:75"))
        assertNull(ClockFormat.parse("13:00 pm"))
        assertNotNull(ClockFormat.parse("23:59"))
    }
}
