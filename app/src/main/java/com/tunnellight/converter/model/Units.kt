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
    val fromBase: (Double) -> Double,
    /**
     * When set, results displayed in this unit are rounded to the nearest multiple of this
     * increment (e.g. 0.5 to snap shoe sizes to half sizes). Null means show full precision.
     */
    val snap: Double? = null
) {
    /** Convert [value], expressed in this unit, into [target], snapped to [target]'s increment if any. */
    fun convert(value: Double, target: ConvUnit): Double {
        val result = target.fromBase(toBase(value))
        return target.snap?.let { Math.round(result / it) * it } ?: result
    }
}

/** A group of related units shown as a tile on the home screen. */
data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val units: List<ConvUnit>,
    /** Optional caveat shown at the top of the converter screen. */
    val note: String? = null,
    /** Set when the category's conversions depend on a number the user can adjust. */
    val param: CategoryParam? = null
)

/**
 * A number the user can change that the category's conversions depend on — currently only the
 * Pay category's hours worked per week. The converter screen shows it as an extra field above
 * the unit rows and swaps in [variantFor]'s category (new units, new note) as the value changes.
 */
data class CategoryParam(
    /** Label for the input, e.g. "Hours worked per week". */
    val label: String,
    /** Smaller caption under the label, in the same spot as a unit's symbol. */
    val caption: String,
    val defaultValue: Double,
    /** Values outside this range are ignored, so the last usable setting stays in effect. */
    val range: ClosedFloatingPointRange<Double>,
    /** Rebuilds the whole category for a new value of this parameter. */
    val variantFor: (Double) -> Category
)

/**
 * Shoe-size constants. Both shoe categories use foot length in millimeters as their base
 * (this is effectively the Mondopoint standard). A men's US 9 corresponds to roughly a
 * 270 mm foot, and each full size step is one barleycorn (1/3 inch). US/UK are affine in
 * foot length; EU (Paris point) uses EU ≈ 0.15·mm + 2. UK and EU are unisex by foot length,
 * so they share the same formula in both categories — only the US number differs.
 */
private const val SHOE_ANCHOR_MM = 270.0
private const val BARLEYCORN_MM = 25.4 / 3.0

/** Build a shoe-size unit that is affine in foot length: size 0 maps to [anchorSize] at the anchor foot. */
private fun shoeBarleycorn(name: String, symbol: String, anchorSize: Double) = ConvUnit(
    name = name,
    symbol = symbol,
    toBase = { SHOE_ANCHOR_MM + (it - anchorSize) * BARLEYCORN_MM },
    fromBase = { anchorSize + (it - SHOE_ANCHOR_MM) / BARLEYCORN_MM },
    snap = 0.5
)

/** EU (Paris-point) shoe size, unisex: EU ≈ 0.15·(foot mm) + 2. */
private fun shoeEu() = ConvUnit(
    name = "EU", symbol = "EU",
    toBase = { (it - 2.0) / 0.15 },
    fromBase = { 0.15 * it + 2.0 },
    snap = 0.5
)

/**
 * Women's tops are sized by bust circumference, but the ASOS chart is non-linear: increments
 * grow from ~2 cm per size at the small end to ~3.5 cm in the Curve range. So rather than a
 * single slope we interpolate over the published ASOS table (keyed by UK size). US = UK − 4
 * and EU = UK + 28. Men's dress-shirt sizes are just collar (neck) circumference in different
 * units, so they need no table — the base is neck cm, and EU shirt size = neck cm.
 */
private val ASOS_UK_SIZES = listOf(4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 26.0)
private val ASOS_BUST_CM = listOf(76.0, 80.0, 85.0, 89.0, 93.0, 97.0, 102.0, 108.0, 116.0, 123.0, 130.0, 137.0)

/**
 * Piecewise-linear lookup of [x] against monotonically increasing [xs], returning the matching
 * [ys] value. Inputs outside the table are linearly extrapolated using the end segments.
 */
private fun piecewise(x: Double, xs: List<Double>, ys: List<Double>): Double {
    val last = xs.size - 1
    val i = when {
        x <= xs[0] -> 0
        x >= xs[last] -> last - 1
        else -> xs.indexOfLast { it <= x }.coerceAtMost(last - 1)
    }
    val t = (x - xs[i]) / (xs[i + 1] - xs[i])
    return ys[i] + t * (ys[i + 1] - ys[i])
}

/**
 * Build a women's top size unit. [offsetFromUk] is how the system's number differs from the UK
 * size (US = −4, EU = +28); conversions go size → UK → bust (cm) and back via the ASOS table.
 */
private fun womensTop(name: String, symbol: String, offsetFromUk: Double) = ConvUnit(
    name = name,
    symbol = symbol,
    toBase = { piecewise(it - offsetFromUk, ASOS_UK_SIZES, ASOS_BUST_CM) },
    fromBase = { piecewise(it, ASOS_BUST_CM, ASOS_UK_SIZES) + offsetFromUk },
    snap = 2.0
)

/** Build a unit whose conversion to the base is a simple multiply by [factor], optionally [snap]ped. */
private fun linear(name: String, symbol: String, factor: Double, snap: Double? = null) = ConvUnit(
    name = name,
    symbol = symbol,
    toBase = { it * factor },
    fromBase = { it / factor },
    snap = snap
)

/**
 * Implementation of [UnitsRepository.format]. It lives at file scope so the category builders
 * below can use it while the repository object itself is still being initialised.
 */
private fun formatNumber(value: Double): String {
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

/** Work week the Pay category assumes until the user enters their own hours. */
private const val DEFAULT_HOURS_PER_WEEK = 40.0
private const val WEEKS_PER_YEAR = 52.0

/**
 * Build the Pay category for a given work week. Base unit is yearly pay: hourly ×
 * (hours per week × 52) = yearly, monthly × 12 = yearly. Only the hourly rate depends on the
 * work week, but the note quotes it too, so the whole category is rebuilt per value.
 */
private fun payCategory(hoursPerWeek: Double): Category {
    val hoursPerYear = hoursPerWeek * WEEKS_PER_YEAR
    return Category(
        id = "pay", name = "Pay", emoji = "💵", colorHex = "#16A34A",
        note = "Conversions assume a ${formatNumber(hoursPerWeek)}-hour work week " +
            "(${formatNumber(hoursPerYear)} hours per year).",
        units = listOf(
            linear("Hourly", "/hr", hoursPerYear),
            linear("Monthly", "/mo", 12.0),
            linear("Yearly", "/yr", 1.0)
        ),
        param = CategoryParam(
            label = "Hours worked per week",
            caption = "h/week",
            defaultValue = DEFAULT_HOURS_PER_WEEK,
            range = 1.0..168.0,
            variantFor = ::payCategory
        )
    )
}

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
        payCategory(DEFAULT_HOURS_PER_WEEK),
        Category(
            id = "shoe_men", name = "Men's Shoe Size", emoji = "👞", colorHex = "#92400E",
            note = "Approximate adult men's sizes anchored to common charts (US 9 ≈ 270 mm " +
                "foot). Round to the nearest half size; fit varies by brand.",
            // Base unit is foot length in mm. Men's UK runs 0.5 below US; EU is unisex.
            units = listOf(
                shoeBarleycorn("US", "US", anchorSize = 9.0),
                shoeBarleycorn("UK", "UK", anchorSize = 8.5),
                shoeEu(),
                linear("Foot length (cm)", "cm", 10.0),
                linear("Foot length (mm)", "mm", 1.0)
            )
        ),
        Category(
            id = "shoe_women", name = "Women's Shoe Size", emoji = "👠", colorHex = "#BE185D",
            note = "Approximate adult women's sizes anchored to common charts (US 10.5 ≈ " +
                "270 mm foot; US women run ~1.5 above US men). Round to the nearest half " +
                "size; fit varies by brand.",
            // Base unit is foot length in mm. Women's US runs 1.5 above men's; UK and EU are unisex.
            units = listOf(
                shoeBarleycorn("US", "US", anchorSize = 10.5),
                shoeBarleycorn("UK", "UK", anchorSize = 8.5),
                shoeEu(),
                linear("Foot length (cm)", "cm", 10.0),
                linear("Foot length (mm)", "mm", 1.0)
            )
        ),
        Category(
            id = "shirt_men", name = "Men's Shirt Size", emoji = "👔", colorHex = "#1E3A8A",
            note = "Dress-shirt sizes by collar (neck) circumference. EU size equals the " +
                "neck in cm. Rough letter sizes: S ≈ 14–14½ in, M ≈ 15–15½ in, L ≈ 16–16½ in, " +
                "XL ≈ 17–17½ in. Fit varies by brand.",
            // Base unit is neck circumference in cm. EU shirt size = neck cm.
            units = listOf(
                linear("Collar US/UK", "in", 2.54, snap = 0.5),
                linear("Collar EU", "cm", 1.0, snap = 0.5)
            )
        ),
        Category(
            id = "shirt_women", name = "Women's Shirt Size", emoji = "👚", colorHex = "#DB2777",
            note = "Tops/blouse sizes by bust circumference, interpolated from the ASOS chart " +
                "(UK = US + 4, EU = US + 32). E.g. UK 12 / US 8 ≈ 93 cm, UK 20 ≈ 116 cm bust. " +
                "Fit varies by brand.",
            // Base unit is bust circumference in cm; US/UK/EU come from the ASOS table.
            units = listOf(
                womensTop("US", "US", offsetFromUk = -4.0),
                womensTop("UK", "UK", offsetFromUk = 0.0),
                womensTop("EU", "EU", offsetFromUk = 28.0),
                linear("Bust", "in", 2.54),
                linear("Bust", "cm", 1.0)
            )
        )
    )

    fun categoryById(id: String): Category? = categories.firstOrNull { it.id == id }

    /**
     * Format a converted value for display: trims trailing zeros, limits precision to ~10
     * significant digits, and falls back to scientific notation for very large/small magnitudes.
     */
    fun format(value: Double): String = formatNumber(value)
}
