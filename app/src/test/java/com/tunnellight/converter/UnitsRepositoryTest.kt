package com.tunnellight.converter

import com.tunnellight.converter.model.UnitsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Formatting and search are pure functions the two screens lean on, so pin their behaviour. */
class UnitsRepositoryTest {

    @Test
    fun formatsToAFixedNumberOfDecimals() {
        assertEquals("8666.67", UnitsRepository.format(8666.666666, decimals = 2))
        assertEquals("104000.00", UnitsRepository.format(104000.0, decimals = 2))
        assertEquals("0.00", UnitsRepository.format(0.0, decimals = 2))
        assertEquals("13", UnitsRepository.format(12.5, decimals = 0))
    }

    @Test
    fun formatsAdaptivelyWithoutDecimals() {
        assertEquals("0", UnitsRepository.format(0.0))
        assertEquals("2080", UnitsRepository.format(2080.0))
        assertEquals("1.5", UnitsRepository.format(1.5))
    }

    @Test
    fun keepsScientificNotationForExtremeMagnitudes() {
        // A fixed scale would print a misleading "0.00" here, so the fallback wins either way.
        assertTrue(UnitsRepository.format(1e-9, decimals = 2).contains("E-"))
        assertTrue(UnitsRepository.format(1e15, decimals = 2).contains("E+"))
    }

    @Test
    fun searchMatchesCategoryNames() {
        val matches = UnitsRepository.search("temp")
        assertEquals(1, matches.size)
        assertEquals("temperature", matches[0].category.id)
        assertNull("a category-name hit needs no unit caption", matches[0].matchedUnit)
    }

    @Test
    fun searchFallsBackToUnitNamesAndSymbols() {
        val bySymbol = UnitsRepository.search("psi").single()
        assertEquals("pressure", bySymbol.category.id)
        assertEquals("PSI", bySymbol.matchedUnit?.name)

        val byName = UnitsRepository.search("knot").single()
        assertEquals("speed", byName.category.id)
        assertEquals("Knot", byName.matchedUnit?.name)
    }

    @Test
    fun blankSearchReturnsEverythingAndNonsenseReturnsNothing() {
        assertEquals(UnitsRepository.categories.size, UnitsRepository.search("   ").size)
        assertTrue(UnitsRepository.search("zzzz").isEmpty())
    }
}
