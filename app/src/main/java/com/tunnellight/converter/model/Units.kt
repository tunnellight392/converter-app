package com.tunnellight.converter.model

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale
import kotlin.math.abs

/**
 * A single unit of measure. Conversions go through a per-category "base" unit:
 * any value is first mapped to the base via [toBase], then to the target via [fromBase].
 * Using lambdas (rather than a single multiplier) lets us support affine conversions
 * such as temperature, where Fahrenheit/Celsius/Kelvin differ by an offset, not just a factor.
 */
data class ConvUnit(
    val name: String,
    val symbol: String,
    val toBase: (Double) -> Double,
    val fromBase: (Double) -> Double
) {
    /** Convert [value], expressed in this unit, into [target]. */
    fun convert(value: Double, target: ConvUnit): Double = target.fromBase(toBase(value))
}

/** A group of related units shown as a tile on the home screen. */
data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val units: List<ConvUnit>,
    /** Optional caveat shown at the top of the converter screen. */
    val note: String? = null
)

/** Build a unit whose conversion to the base is a simple multiply by [factor]. */
private fun linear(name: String, symbol: String, factor: Double) = ConvUnit(
    name = name,
    symbol = symbol,
    toBase = { it * factor },
    fromBase = { it / factor }
)

object UnitsRepository {

    val categories: List<Category> = listOf(
        Category(
            id = "length", name = "Length", emoji = "📏", colorHex = "#3B82F6",
            units = listOf(
                linear("Meter", "m", 1.0),
                linear("Kilometer", "km", 1000.0),
                linear("Centimeter", "cm", 0.01),
                linear("Millimeter", "mm", 0.001),
                linear("Micrometer", "µm", 1e-6),
                linear("Mile", "mi", 1609.344),
                linear("Yard", "yd", 0.9144),
                linear("Foot", "ft", 0.3048),
                linear("Inch", "in", 0.0254),
                linear("Nautical mile", "nmi", 1852.0)
            )
        ),
        Category(
            id = "mass", name = "Mass", emoji = "⚖️", colorHex = "#10B981",
            units = listOf(
                linear("Kilogram", "kg", 1.0),
                linear("Gram", "g", 0.001),
                linear("Milligram", "mg", 1e-6),
                linear("Metric ton", "t", 1000.0),
                linear("Pound", "lb", 0.45359237),
                linear("Ounce", "oz", 0.028349523125),
                linear("Stone", "st", 6.35029318)
            )
        ),
        Category(
            id = "temperature", name = "Temperature", emoji = "🌡️", colorHex = "#EF4444",
            units = listOf(
                ConvUnit("Celsius", "°C", toBase = { it }, fromBase = { it }),
                ConvUnit("Fahrenheit", "°F",
                    toBase = { (it - 32.0) * 5.0 / 9.0 },
                    fromBase = { it * 9.0 / 5.0 + 32.0 }),
                ConvUnit("Kelvin", "K",
                    toBase = { it - 273.15 },
                    fromBase = { it + 273.15 })
            )
        ),
        Category(
            id = "area", name = "Area", emoji = "📐", colorHex = "#8B5CF6",
            units = listOf(
                linear("Square meter", "m²", 1.0),
                linear("Square kilometer", "km²", 1e6),
                linear("Square centimeter", "cm²", 1e-4),
                linear("Square mile", "mi²", 2589988.110336),
                linear("Square yard", "yd²", 0.83612736),
                linear("Square foot", "ft²", 0.09290304),
                linear("Square inch", "in²", 0.00064516),
                linear("Acre", "ac", 4046.8564224),
                linear("Hectare", "ha", 10000.0)
            )
        ),
        Category(
            id = "volume", name = "Volume", emoji = "🧪", colorHex = "#06B6D4",
            units = listOf(
                linear("Liter", "L", 1.0),
                linear("Milliliter", "mL", 0.001),
                linear("Cubic meter", "m³", 1000.0),
                linear("Cubic centimeter", "cm³", 0.001),
                linear("US gallon", "gal", 3.785411784),
                linear("US quart", "qt", 0.946352946),
                linear("US pint", "pt", 0.473176473),
                linear("US cup", "cup", 0.2365882365),
                linear("US fluid ounce", "fl oz", 0.0295735295625),
                linear("Imperial gallon", "imp gal", 4.54609)
            )
        ),
        Category(
            id = "speed", name = "Speed", emoji = "🏎️", colorHex = "#F59E0B",
            units = listOf(
                linear("Meter/second", "m/s", 1.0),
                linear("Kilometer/hour", "km/h", 1.0 / 3.6),
                linear("Mile/hour", "mph", 0.44704),
                linear("Foot/second", "ft/s", 0.3048),
                linear("Knot", "kn", 0.514444444444)
            )
        ),
        Category(
            id = "fuel", name = "Fuel Economy", emoji = "⛽", colorHex = "#84CC16",
            note = "Liters/100 km is an inverse measure: a lower number means a more efficient vehicle.",
            // Base unit is kilometers per liter (km/L). MPG (more is better) maps with a
            // simple factor, but L/100 km is inversely related, so it uses km/L = 100 / value.
            units = listOf(
                ConvUnit("Mile/gallon (US)", "mpg",
                    toBase = { it * (1.609344 / 3.785411784) },
                    fromBase = { it / (1.609344 / 3.785411784) }),
                ConvUnit("Mile/gallon (UK)", "mpg",
                    toBase = { it * (1.609344 / 4.54609) },
                    fromBase = { it / (1.609344 / 4.54609) }),
                ConvUnit("Kilometer/liter", "km/L",
                    toBase = { it }, fromBase = { it }),
                ConvUnit("Liter/100 km", "L/100km",
                    toBase = { 100.0 / it }, fromBase = { 100.0 / it })
            )
        ),
        Category(
            id = "ev", name = "EV Efficiency", emoji = "🔌", colorHex = "#7C3AED",
            note = "Base is energy used per distance (Wh/km). Consumption units (Wh/km, " +
                "kWh/100 km) rise as efficiency drops; range units (mi/kWh, km/kWh, MPGe) " +
                "are inverse, so a higher number means a more efficient vehicle. MPGe uses " +
                "the EPA equivalence of 33.7 kWh per US gallon of gasoline.",
            units = listOf(
                ConvUnit("Watt-hour/kilometer", "Wh/km",
                    toBase = { it }, fromBase = { it }),
                ConvUnit("Watt-hour/mile", "Wh/mi",
                    toBase = { it / 1.609344 }, fromBase = { it * 1.609344 }),
                ConvUnit("kWh/100 km", "kWh/100km",
                    toBase = { it * 10.0 }, fromBase = { it / 10.0 }),
                ConvUnit("kWh/100 mi", "kWh/100mi",
                    toBase = { it * (10.0 / 1.609344) }, fromBase = { it / (10.0 / 1.609344) }),
                ConvUnit("Kilometer/kWh", "km/kWh",
                    toBase = { 1000.0 / it }, fromBase = { 1000.0 / it }),
                ConvUnit("Mile/kWh", "mi/kWh",
                    toBase = { 1000.0 / (it * 1.609344) }, fromBase = { 1000.0 / (it * 1.609344) }),
                ConvUnit("MPGe", "MPGe",
                    toBase = { 33700.0 / (it * 1.609344) }, fromBase = { 33700.0 / (it * 1.609344) })
            )
        ),
        Category(
            id = "time", name = "Time", emoji = "⏱️", colorHex = "#6366F1",
            units = listOf(
                linear("Second", "s", 1.0),
                linear("Millisecond", "ms", 0.001),
                linear("Minute", "min", 60.0),
                linear("Hour", "h", 3600.0),
                linear("Day", "d", 86400.0),
                linear("Week", "wk", 604800.0),
                linear("Month (30 d)", "mo", 2592000.0),
                linear("Year (365 d)", "yr", 31536000.0)
            )
        ),
        Category(
            id = "storage", name = "Digital Storage", emoji = "💾", colorHex = "#64748B",
            units = listOf(
                linear("Bit", "b", 0.125),
                linear("Byte", "B", 1.0),
                linear("Kilobyte", "KB", 1024.0),
                linear("Megabyte", "MB", 1048576.0),
                linear("Gigabyte", "GB", 1073741824.0),
                linear("Terabyte", "TB", 1099511627776.0),
                linear("Petabyte", "PB", 1125899906842624.0)
            )
        ),
        Category(
            id = "energy", name = "Energy", emoji = "⚡", colorHex = "#EAB308",
            units = listOf(
                linear("Joule", "J", 1.0),
                linear("Kilojoule", "kJ", 1000.0),
                linear("Calorie", "cal", 4.184),
                linear("Kilocalorie", "kcal", 4184.0),
                linear("Watt-hour", "Wh", 3600.0),
                linear("Kilowatt-hour", "kWh", 3600000.0),
                linear("Foot-pound", "ft·lb", 1.3558179483314004)
            )
        ),
        Category(
            id = "pressure", name = "Pressure", emoji = "🌬️", colorHex = "#0EA5E9",
            units = listOf(
                linear("Pascal", "Pa", 1.0),
                linear("Kilopascal", "kPa", 1000.0),
                linear("Bar", "bar", 100000.0),
                linear("Millibar", "mbar", 100.0),
                linear("PSI", "psi", 6894.757293168),
                linear("Atmosphere", "atm", 101325.0),
                linear("Torr (mmHg)", "Torr", 133.32236842105263)
            )
        ),
        Category(
            id = "power", name = "Power", emoji = "🔋", colorHex = "#F97316",
            units = listOf(
                linear("Watt", "W", 1.0),
                linear("Milliwatt", "mW", 0.001),
                linear("Kilowatt", "kW", 1000.0),
                linear("Megawatt", "MW", 1e6),
                linear("Horsepower", "hp", 745.6998715822702)
            )
        ),
        Category(
            id = "angle", name = "Angle", emoji = "📐", colorHex = "#D946EF",
            units = listOf(
                linear("Degree", "°", 1.0),
                linear("Radian", "rad", 57.29577951308232),
                linear("Gradian", "grad", 0.9),
                linear("Arcminute", "′", 1.0 / 60.0),
                linear("Arcsecond", "″", 1.0 / 3600.0),
                linear("Turn", "turn", 360.0)
            )
        ),
        Category(
            id = "frequency", name = "Frequency", emoji = "📡", colorHex = "#14B8A6",
            units = listOf(
                linear("Hertz", "Hz", 1.0),
                linear("Kilohertz", "kHz", 1000.0),
                linear("Megahertz", "MHz", 1e6),
                linear("Gigahertz", "GHz", 1e9)
            )
        ),
        Category(
            id = "pay", name = "Pay", emoji = "💵", colorHex = "#16A34A",
            note = "Conversions assume a 40-hour work week (2080 hours per year).",
            // Base unit is yearly pay: hourly × 2080 = yearly, monthly × 12 = yearly.
            units = listOf(
                linear("Hourly", "/hr", 2080.0),
                linear("Monthly", "/mo", 12.0),
                linear("Yearly", "/yr", 1.0)
            )
        )
    )

    fun categoryById(id: String): Category? = categories.firstOrNull { it.id == id }

    /**
     * Format a converted value for display: trims trailing zeros, limits precision to ~10
     * significant digits, and falls back to scientific notation for very large/small magnitudes.
     */
    fun format(value: Double): String {
        if (!value.isFinite()) return ""
        if (value == 0.0) return "0"
        val magnitude = abs(value)
        return if (magnitude >= 1e-4 && magnitude < 1e12) {
            BigDecimal.valueOf(value)
                .round(MathContext(10))
                .stripTrailingZeros()
                .toPlainString()
        } else {
            String.format(Locale.US, "%.6E", value)
        }
    }
}
