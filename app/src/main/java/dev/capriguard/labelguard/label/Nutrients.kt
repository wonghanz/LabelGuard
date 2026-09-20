package dev.capriguard.labelguard.label

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Every number in this file is a figure a labelling authority publishes, not a
 * judgement made here. The distinction matters: the app's whole claim to trust is
 * that it applies published comparison bases to what you read off a packet, and
 * never invents a threshold of its own. Where two authorities publish different
 * numbers for similar-sounding things, both are kept and named — see
 * [Band.lowAtMost] against [ClaimCatalogue], where salt sits on two lines at once.
 *
 * Sources, stated so a reader can check a specific row rather than the app:
 *  - comparison thresholds: the UK FSA / public-health front-of-pack "low /
 *    medium / high" cut-offs, expressed per 100 g or per 100 ml
 *  - claim conditions: Regulation (EC) 1924/2006 Annexes, as carried into UK law
 *  - rounding: Regulation (EU) 1169/2011 Annex XIV, as amended by 2018/273
 *  - energy factors: the same regulation's Annex XV
 *  - reference intakes: the eight figures printed on packs as "% RI"
 */

/* ------------------------------------------------------------------ units */

enum class SizeUnit(val short: String, val perAmount: String) {
    GRAM("g", "per 100 g"),
    MILLILITRE("ml", "per 100 ml"),
}

/** Which basis the packet's own table is printed on. */
enum class PrintedBasis(val label: String) {
    PER_100("Typical values, per 100 g or 100 ml"),
    PER_SERVING("Per serving, and no per-100 column"),
}

/* --------------------------------------------------------------- nutrients */

/**
 * A row of the nutrition table. [kJPerGram] is the Annex XV conversion factor, used
 * only for the internal consistency cross-check and for per-energy shares; it is
 * deliberately absent on the energy and salt rows, which have no such factor.
 *
 * [wideStepsAllowed] marks the rows for which Annex XIV permits 0.2 g steps instead
 * of 0.1 g below 1.5 g per 100 g. The quantum itself is read off how many decimals
 * the pack printed, because that is evidence of which one it used.
 */
data class Nutrient(
    val key: String,
    val label: String,
    val unit: String,
    val kJPerGram: Double? = null,
    val wideStepsAllowed: Boolean = false,
    val zeroCeiling: Double? = null,
)

object Nutrients {
    val ENERGY_KJ = Nutrient("energyKj", "Energy", "kJ")
    val ENERGY_KCAL = Nutrient("energyKcal", "Energy", "kcal")
    val FAT = Nutrient("fat", "Fat", "g", kJPerGram = 37.0, wideStepsAllowed = true, zeroCeiling = 0.5)
    val SATURATES = Nutrient("saturates", "of which saturates", "g", kJPerGram = 37.0, wideStepsAllowed = true, zeroCeiling = 0.1)
    val CARBS = Nutrient("carbohydrate", "Carbohydrate", "g", kJPerGram = 17.0, wideStepsAllowed = true)
    val SUGARS = Nutrient("sugars", "of which sugars", "g", kJPerGram = 17.0, wideStepsAllowed = true, zeroCeiling = 0.5)
    val FIBRE = Nutrient("fibre", "Fibre", "g", kJPerGram = 8.0, wideStepsAllowed = true)
    val PROTEIN = Nutrient("protein", "Protein", "g", kJPerGram = 17.0, wideStepsAllowed = true, zeroCeiling = 0.5)
    val SALT = Nutrient("salt", "Salt", "g", wideStepsAllowed = true, zeroCeiling = 0.0125)
    val SODIUM = Nutrient("sodium", "Sodium", "g", zeroCeiling = 0.005)

    /** Typed on the form, in the order a European pack lists them. */
    val formOrder = listOf(ENERGY_KJ, ENERGY_KCAL, FAT, SATURATES, CARBS, SUGARS, FIBRE, PROTEIN, SALT, SODIUM)

    /** Rows that carry a published comparison band. */
    val banded = listOf(FAT, SATURATES, SUGARS, SALT, FIBRE)

    /**
     * Everything read off the table, in pack order. Carbohydrate is here without a
     * band, because it has no published cut-off but the energy cross-check cannot be
     * done without it.
     */
    val tableRows = listOf(FAT, SATURATES, CARBS, SUGARS, FIBRE, PROTEIN, SALT)
}

object Conversions {
    /** Exact, by definition of the calorie. Packs round to whole kJ and kcal. */
    const val KJ_PER_KCAL = 4.184

    /**
     * Annex XIII states sodium conditions in sodium; the salt equivalent is
     * defined as sodium multiplied by 2.5.
     */
    const val SALT_PER_SODIUM = 2.5
}

/* ------------------------------------------------------ comparison bands */

/**
 * Front-of-pack comparison cut-offs, defined per 100 g or per 100 ml and nowhere
 * else. That is the whole reason this app exists: a packet that shows you a
 * 30 g serving cannot be band-checked until the figure is moved onto 100 g, and
 * moving it is arithmetic that changes the answer.
 *
 * [moreIsBetter] flips only the words used — never a verdict. On fibre a high
 * figure is reported as "high fibre"; on the others as "high".
 */
data class Band(val lowAtMost: Double, val highAbove: Double, val moreIsBetter: Boolean = false)

object Bands {
    val foods = mapOf(
        "fat" to Band(3.0, 17.5),
        "saturates" to Band(1.5, 5.0),
        "sugars" to Band(5.0, 22.5),
        "salt" to Band(0.3, 1.5),
        "fibre" to Band(3.0, 6.0, moreIsBetter = true),
    )

    val drinks = mapOf(
        "fat" to Band(1.5, 9.0),
        "saturates" to Band(0.75, 4.5),
        "sugars" to Band(2.5, 11.0),
        "salt" to Band(0.3, 0.75),
        "fibre" to Band(1.5, 3.0, moreIsBetter = true),
    )

    fun of(unit: SizeUnit, key: String): Band? = (if (unit == SizeUnit.MILLILITRE) drinks else foods)[key]
}

enum class BandLevel { LOW, MIDDLE, HIGH, ON_THE_LINE }

/* ------------------------------------------------------- reference intakes */

/**
 * The "% of reference intake" figures printed on packs. These describe a
 * reference adult diet of 2,000 kcal chosen as a labelling convention; they are
 * not a target for anybody, and this app refuses to divide a packet into "your"
 * day. Fibre and carbohydrate have no reference intake, which is itself worth
 * knowing the next time a pack prints one.
 */
object RefIntakes {
    val energyKj = 8400.0
    val energyKcal = 2000.0
    val perAdult = mapOf(
        "fat" to 70.0,
        "saturates" to 20.0,
        "sugars" to 90.0,
        "protein" to 50.0,
        "salt" to 6.0,
    )
}

/* ------------------------------------------------------------- parsing */

fun parseAmount(raw: String): Double? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    // Packs across Europe print a decimal comma; a typed full stop is just as
    // likely. Accept both, reject anything that is not a plain positive number.
    val normalised = t.replace(" ", "").replace(" ", "").replace(',', '.')
    if (normalised.isEmpty()) return null
    if (!normalised.matches(Regex("""\d+(\.\d+)?|\.\d+|\d+\."""))) return null
    val value = normalised.toDoubleOrNull() ?: return null
    return if (value.isFinite()) value else null
}

/**
 * A printed figure plus the precision it was printed at. The second half matters as
 * much as the first: how many decimals a pack declared is evidence about which
 * rounding rule it used, and therefore about how much of the figure is real.
 */
data class Declared(val value: Double, val decimals: Int)

fun parseDeclared(raw: String): Declared? = parseAmount(raw)?.let { Declared(it, decimalsIn(raw)) }

fun decimalsIn(raw: String): Int {
    val t = raw.trim()
    val i = t.lastIndexOfAny(charArrayOf('.', ','))
    if (i < 0) return 0
    return t.substring(i + 1).count { it.isDigit() }.coerceAtMost(6)
}

/**
 * The interval a printed figure actually licenses, given Annex XIV.
 *
 * Three rules are in play and they do not all point the same way. Declared values
 * are rounded to the nearest 0.1 g, except that 0.2 g may be used
 * below 1.5 g per 100 g — and the pack does not tell you which it used, so the
 * wider allowance is the honest one. A value printed as 0 is a different rule
 * again: it means "at or below the contains-no threshold", not "none".
 */
data class Tolerance(val lower: Double, val upper: Double, val why: String)

/**
 * [wideTest] is the amount expressed per 100 g/ml, which is what Annex XIV measures
 * the "below 1.5 g" allowance against; it differs from [declared] whenever the pack
 * printed a per-serving column only.
 */
fun toleranceOf(n: Nutrient, declared: Declared, wideTest: Double = declared.value): Tolerance {
    if (declared.value == 0.0 && n.zeroCeiling != null) {
        return Tolerance(
            0.0,
            n.zeroCeiling,
            "printed 0, which Annex XIV allows anywhere up to ${fmt(n.zeroCeiling)} per 100",
        )
    }
    val half = when {
        declared.decimals == 0 -> 0.5
        declared.decimals == 1 && n.wideStepsAllowed && wideTest < 1.5 -> 0.1
        else -> 0.5 / Math.pow(10.0, declared.decimals.toDouble())
    }
    return Tolerance(
        (declared.value - half).coerceAtLeast(0.0),
        declared.value + half,
        "declared to ${places(declared.decimals)}, so the figure behind it is up to ${fmt(half)} away from what you read",
    )
}

private fun places(d: Int): String = when (d) {
    0 -> "no decimals"
    1 -> "one decimal"
    else -> "$d decimals"
}

/** Move an interval onto another basis, keeping the explanation attached. */
fun Tolerance.on(factor: Double): Tolerance =
    Tolerance(lower * factor, upper * factor, why)

/** Band membership that refuses to guess: an interval crossing a cut-off is unresolved. */
fun bandOf(t: Tolerance, band: Band): BandLevel {
    val low = band.lowAtMost
    val high = band.highAbove
    return when {
        t.upper <= low -> BandLevel.LOW
        t.lower > high -> BandLevel.HIGH
        t.lower > low && t.upper <= high -> BandLevel.MIDDLE
        else -> BandLevel.ON_THE_LINE
    }
}

fun bandWords(level: BandLevel, b: Band): String = when (level) {
    BandLevel.LOW -> if (b.moreIsBetter) "not a source of" else "low"
    BandLevel.MIDDLE -> if (b.moreIsBetter) "a source of" else "middle"
    BandLevel.HIGH -> "high"
    BandLevel.ON_THE_LINE -> "on the line"
}

/** Two decimals, or four below a tenth, with trailing zeros off. Figures are compared, so they align. */
fun fmt(v: Double): String {
    val scale = if (v != 0.0 && abs(v) < 0.1) 10_000.0 else 100.0
    val r = (v * scale).roundToInt() / scale
    return if (r == 0.0) "0" else {
        val s = r.toString()
        if (s.endsWith(".0")) s.dropLast(2) else s
    }
}

fun fmtSigned(v: Double): String = (if (v > 0) "+" else "") + fmt(v)
