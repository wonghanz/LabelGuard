package dev.capriguard.labelguard.label

import kotlin.math.abs

/**
 * The reading layer. One pass, no network: it takes what a packet says, puts every
 * figure onto the bases the comparison thresholds are defined on, and reports where
 * the packet's own front-of-pack text survives its own table.
 *
 * It never produces a score, a verdict about health, or a share of anything
 * "for you". The only percentages computed here are the ones the law already
 * prints on packs, against the reference adult diet the pack itself assumes.
 */

/**
 * One packet as typed: raw strings, so a half-entered row reads back exactly what
 * was written rather than a parsed guess.
 */
data class Packet(
    val values: Map<String, String> = emptyMap(),
    val unit: SizeUnit = SizeUnit.GRAM,
    val basis: PrintedBasis = PrintedBasis.PER_100,
    val claims: String = "",
) {
    fun with(key: String, raw: String): Packet = copy(values = values + (key to raw))
    fun raw(key: String): String = values[key].orEmpty()

    val isEmpty: Boolean get() = values.values.all { it.isBlank() } && claims.isBlank()
}

/** A quantity plus the interval its printed form legally licenses. */
data class Figure(val value: Double, val tol: Tolerance) {
    fun on(factor: Double): Figure = Figure(value * factor, tol.on(factor))

    /** True only when the two intervals cannot be reconciled to a single ordering. */
    fun separableFrom(other: Figure): Boolean = high < other.low || low > other.high

    val low: Double get() = tol.lower
    val high: Double get() = tol.upper
}

/** One nutrient, expressed on every basis it can be expressed on. */
data class Row(
    val nutrient: Nutrient,
    val printed: Figure,
    val per100: Figure,
    val perServing: Figure?,
    val perPacket: Figure?,
    val band: Band?,
    val level: BandLevel?,
    val ri: RiShare?,
    val note: String?,
)

data class RiShare(val percent: Double, val ofWhat: String) {
    val words: String get() = "${fmt(percent)}% of the ${ofWhat}"
}

/**
 * The energy row, carrying the two cross-checks that matter: whether the pack's own
 * kJ and kcal agree, and whether the macros add up to the printed energy.
 *
 * The macro sum is computed twice because Annex XV permits fibre to be treated
 * either as carbohydrate at 17 kJ/g or in its own right at 8 kJ/g, and a pack does
 * not say which it did. Agreement with either is treated as consistent.
 */
data class EnergyRow(
    val kj: Figure,
    val kcal: Figure,
    val bothTyped: Boolean,
    val conversionGapKj: Double?,
    val conversionOk: Boolean,
    val macroSumLow: Double?,
    val macroSumHigh: Double?,
    val sumMatches: Boolean?,
) {
    /** A mismatch usually means a mistyped digit, not a mislabelled pack. */
    val summary: String
        get() = when {
            !bothTyped && sumMatches == null -> "one energy figure only, so nothing to cross-check"
            !conversionOk -> "the kJ and kcal you typed are ${fmt(abs(conversionGapKj ?: 0.0))} kJ apart, which is more than rounding"
            sumMatches == false -> "the fat, carbohydrate and protein figures add up to ${fmt(macroSumLow ?: 0.0)}-${fmt(macroSumHigh ?: 0.0)} kJ, not ${fmt(kj.value)}"
            bothTyped && sumMatches == true -> "the two energy units agree, and the macros add up to the printed figure"
            else -> "the printed energy is consistent with the macros"
        }
}

class Reading(
    val unit: SizeUnit,
    val basis: PrintedBasis,
    val serving: Double?,
    val net: Double?,
    val rows: List<Row>,
    val energy: EnergyRow?,
    val notes: List<String>,
    val claims: List<ClaimOutcome>,
) {
    fun per100(key: String): Figure? = rows.firstOrNull { it.nutrient.key == key }?.per100
    fun row(key: String): Row? = rows.firstOrNull { it.nutrient.key == key }

    val servingsPerPacket: Double?
        get() {
            val s = serving ?: return null
            val n = net ?: return null
            if (s <= 0.0 || n <= 0.0) return null
            return n / s
        }

    /** How much of the packet one stated serving is, when both weights are known. */
    val servingShareOfPacket: Double?
        get() {
            val s = serving ?: return null
            val n = net ?: return null
            if (n <= 0.0 || s <= 0.0) return null
            return s / n * 100.0
        }

    /** Percentage of the energy value contributed by one nutrient, on the Annex XV factors. */
    fun shareOfEnergy(key: String): Double? {
        val e = energy ?: return null
        val f = per100(key) ?: return null
        val factor = nutrientByKey(key)?.kJPerGram ?: return null
        if (e.kj.value <= 0.0) return null
        return factor * f.value / e.kj.value * 100.0
    }

    private fun nutrientByKey(key: String): Nutrient? = Nutrients.formOrder.firstOrNull { it.key == key }
}

sealed interface Read {
    data class Complete(val reading: Reading) : Read
    data class Incomplete(val message: String) : Read
}

object Reader {

    const val SERVING = "serving"
    const val NET = "net"

    fun read(p: Packet): Read {
        val notes = mutableListOf<String>()
        val values = p.values

        val serving = parseAmount(values[SERVING].orEmpty())
        val net = parseAmount(values[NET].orEmpty())
        if (values[SERVING].orEmpty().isNotBlank() && serving == null) {
            notes += "The serving size is not a number, so nothing could be moved onto a per-100 basis."
        }
        if (serving != null && serving <= 0.0) {
            return Read.Incomplete("A serving has to be more than zero for the arithmetic to mean anything.")
        }
        if (net != null && serving != null && serving > net) {
            notes += "The stated serving is larger than the whole packet, so one of those two weights is wrong."
        }

        // Which basis the printed column sits on decides the scale applied to every row.
        val scale = when (p.basis) {
            PrintedBasis.PER_100 -> 1.0
            PrintedBasis.PER_SERVING -> if (serving == null || serving <= 0.0) 1.0 else 100.0 / serving
        }
        if (p.basis == PrintedBasis.PER_SERVING && serving == null) {
            return Read.Incomplete(
                "You picked per-serving figures, so the app needs the weight of one serving to put them on " +
                    "the same basis the thresholds are defined on. Type it, or switch to the per-100 column.",
            )
        }

        val saltFromSodium: Pair<Figure, Boolean>? = run {
            val salt = parseDeclared(values["salt"].orEmpty())
            val sodium = parseDeclared(values["sodium"].orEmpty())
            when {
                salt != null -> {
                    // The tolerance is stated in the unit the pack printed; scaling to
                    // 100 g happens with every row, so it must not happen twice here.
                    val n = Nutrients.SALT
                    Figure(salt.value, toleranceOf(n, salt, salt.value * scale)) to false
                }
                sodium != null -> {
                    // Annex XIII states the conditions in sodium; the printed row is
                    // usually salt. The conversion factor is defined as 2.5.
                    val n = Nutrients.SODIUM
                    val t = toleranceOf(n, sodium, sodium.value * scale).on(Conversions.SALT_PER_SODIUM)
                    Figure(sodium.value * Conversions.SALT_PER_SODIUM, t) to true
                }
                else -> null
            }
        }

        val rows = mutableListOf<Row>()
        for (n in Nutrients.tableRows) {
            val raw = if (n.key == "salt") saltFromSodium?.first else parseDeclared(values[n.key].orEmpty())?.let {
                Figure(it.value, toleranceOf(n, it, it.value * scale))
            }
            if (raw == null) continue
            val printed = raw
            val hundred = printed.on(scale)
            val perServing = if (serving != null) hundred.on(serving / 100.0) else null
            val perPacket = if (net != null) hundred.on(net / 100.0) else null
            val band = Bands.of(p.unit, n.key)
            val level = band?.let { bandOf(hundred.tol, it) }
            val ri = RefIntakes.perAdult[n.key]?.let { ref ->
                val amount = (perServing ?: hundred).value
                val basisWords = if (perServing != null) "reference intake for one serving" else "reference intake for 100 ${p.unit.short}"
                RiShare(amount / ref * 100.0, basisWords)
            }
            rows += Row(
                nutrient = n,
                printed = printed,
                per100 = hundred,
                perServing = perServing,
                perPacket = perPacket,
                band = band,
                level = level,
                ri = ri,
                note = when {
                    saltFromSodium?.second == true && n.key == "salt" ->
                        "read off a sodium row and multiplied by 2.5 to state it as salt, which is how the " +
                            "conditions are written on the other side of that figure"
                    p.basis == PrintedBasis.PER_SERVING ->
                        "moved onto 100 g from a serving the manufacturer suggested, not one it weighed"
                    else -> null
                },
            )
        }

        val energy = buildEnergy(values, scale, rows)

        if (rows.isEmpty() && energy == null) {
            return Read.Incomplete(
                "There is nothing to read yet. Type at least the energy and one of fat, saturates, sugars, " +
                    "fibre, protein or salt, from whichever column the packet shows.",
            )
        }

        if (p.basis == PrintedBasis.PER_SERVING) {
            notes += "Every figure below was derived from a suggested portion. The packet's own 100 g column, " +
                "where it has one, is the safer thing to type."
        }
        if (net == null) {
            notes += "No packet weight, so the whole-packet row is blank. That is the figure that settles " +
                "whether a serving is a portion or half the bag."
        }

        val claims = ClaimCatalogue.evaluate(p.claims, rows.associate { it.nutrient.key to it.per100 }, energy, p.unit)

        return Read.Complete(Reading(p.unit, p.basis, serving, net, rows, energy, notes, claims))
    }

    private fun buildEnergy(
        values: Map<String, String>,
        scale: Double,
        rows: List<Row>,
    ): EnergyRow? {
        val kjRaw = parseDeclared(values["energyKj"].orEmpty())
        val kcalRaw = parseDeclared(values["energyKcal"].orEmpty())
        val printed = (kjRaw ?: kcalRaw)?.value ?: return null
        val nutrient = if (kjRaw != null) Nutrients.ENERGY_KJ else Nutrients.ENERGY_KCAL
        val printedKj = kjRaw?.value ?: (kcalRaw?.value ?: 0.0) * Conversions.KJ_PER_KCAL
        val printedKcal = kcalRaw?.value ?: printedKj / Conversions.KJ_PER_KCAL
        val declared = toleranceOf(nutrient, Declared(printed, (kjRaw ?: kcalRaw)?.decimals ?: 0), printedKj * scale)

        val kj = Figure(printedKj, declared).on(scale)
        val kcal = Figure(printedKcal, declared).on(scale)

        val gap = if (kjRaw != null && kcalRaw != null) {
            // Two conventions are in circulation: the exact 4.184, and the 4.2 that
            // packs have used for decades when deriving kJ from kcal. A pair that fits
            // either is consistent, and calling it otherwise would be a false alarm.
            val exact = kjRaw.value - kcalRaw.value * Conversions.KJ_PER_KCAL
            val rounded = kjRaw.value - kcalRaw.value * 4.2
            if (abs(exact) <= abs(rounded)) exact else rounded
        } else null
        // Whole-number rounding on both units leaves a slack of about a kJ plus the
        // kcal quantum, so 2 kJ is the point where it stops looking like rounding.
        val conversionOk = gap == null || abs(gap) <= 2.0

        val fat = rows.firstOrNull { it.nutrient.key == "fat" }?.per100?.value
        val carbs = rows.firstOrNull { it.nutrient.key == "carbohydrate" }?.per100?.value
        val protein = rows.firstOrNull { it.nutrient.key == "protein" }?.per100?.value
        val fibre = rows.firstOrNull { it.nutrient.key == "fibre" }?.per100?.value
        val sumMatches: Boolean?
        val low: Double?
        val high: Double?
        if (fat != null && carbs != null && protein != null) {
            // Fibre sits inside carbohydrate on most packs; where it is counted
            // separately at 8 kJ/g the total is smaller, so both bounds are shown.
            val withFibreInCarbs = 37.0 * fat + 17.0 * carbs + 17.0 * protein
            val withFibreAlone = 37.0 * fat + 17.0 * (carbs - (fibre ?: 0.0)).coerceAtLeast(0.0) +
                8.0 * (fibre ?: 0.0) + 17.0 * protein
            low = minOf(withFibreInCarbs, withFibreAlone)
            high = maxOf(withFibreInCarbs, withFibreAlone)
            val slack = kj.value * 0.10 + 20.0
            sumMatches = kj.value in (low - slack)..(high + slack)
        } else {
            low = null; high = null; sumMatches = null
        }

        return EnergyRow(
            kj = kj,
            kcal = kcal,
            bothTyped = kjRaw != null && kcalRaw != null,
            conversionGapKj = gap,
            conversionOk = conversionOk,
            macroSumLow = low,
            macroSumHigh = high,
            sumMatches = sumMatches,
        )
    }

    /**
     * Row-by-row comparison of two packets on the only basis the thresholds share.
     * No winner is declared: where the rounding allowances overlap, the honest answer
     * is that these two cannot be told apart on that row, and that is printed as such.
     */
    fun compare(a: Reading, b: Reading): List<DiffRow> {
        val out = mutableListOf<DiffRow>()
        for (n in Nutrients.banded + listOf(Nutrients.CARBS)) {
            val x = a.per100(n.key) ?: continue
            val y = b.per100(n.key) ?: continue
            out += DiffRow(n.label, n.unit, x, y, orderWords(x, y))
        }
        val ea = a.energy?.kj
        val eb = b.energy?.kj
        if (ea != null && eb != null) out += DiffRow("Energy", "kJ", ea, eb, orderWords(ea, eb))
        return out
    }

    private fun orderWords(x: Figure, y: Figure): String {
        if (!x.separableFrom(y)) return "too close to tell apart, given the rounding both packs are allowed"
        val lower = if (x.high < y.low) "first packet" else "second packet"
        return "$lower is lower, and the gap is wider than either packet's rounding"
    }
}

data class DiffRow(val label: String, val unit: String, val a: Figure, val b: Figure, val verdict: String) {
    val aWords: String get() = fmt(a.value)
    val bWords: String get() = fmt(b.value)
}

/**
 * The reading as text, for the clipboard. It carries only what the arithmetic
 * produced — no product name, no shop, nothing identifying a purchase — because the
 * reading itself never held any of that.
 */
fun reportText(r: Reading, diffs: List<DiffRow>): String = buildString {
    appendLine("LabelGuard reading")
    appendLine("Printed on: ${r.basis.label}")
    appendLine("Basis shown: per 100 ${r.unit.short}")
    r.serving?.let { appendLine("Stated serving: ${fmt(it)} ${r.unit.short}") }
    r.net?.let { appendLine("Packet weight: ${fmt(it)} ${r.unit.short}") }
    r.servingShareOfPacket?.let { appendLine("One serving is ${fmt(it)}% of the packet") }
    appendLine()
    r.energy?.let {
        appendLine("Energy: ${fmt(it.kj.value)} kJ / ${fmt(it.kcal.value)} kcal per 100 ${r.unit.short}")
        appendLine("  ${it.summary}")
        appendLine()
    }
    appendLine("Reference basis, when a percentage is shown: a diet of ${fmt(RefIntakes.energyKcal)} kcal " +
        "(${fmt(RefIntakes.energyKj)} kJ). That is the figure the labelling convention uses for an average " +
        "adult. It is not a target, and it was not set for anybody reading this.")
    appendLine()
    appendLine("Figures per 100 ${r.unit.short}:")
    for (row in r.rows) {
        val band = if (row.band != null && row.level != null) bandWords(row.level, row.band) else "no published cut-off"
        appendLine("- ${row.nutrient.label}: ${fmt(row.per100.value)} ${row.nutrient.unit}  [$band]")
        appendLine("    rounding allows ${fmt(row.per100.low)} to ${fmt(row.per100.high)} (${row.per100.tol.why})")
        row.perServing?.let { appendLine("    per serving: ${fmt(it.value)} ${row.nutrient.unit}") }
        row.perPacket?.let { appendLine("    whole packet: ${fmt(it.value)} ${row.nutrient.unit}") }
        row.ri?.let { appendLine("    ${it.words}") }
        row.note?.let { appendLine("    note: $it") }
    }
    if (r.claims.isNotEmpty()) {
        appendLine()
        appendLine("Claims, against the table:")
        for (c in r.claims) {
            appendLine("- ${c.phrase}: ${ClaimCatalogue.verdictWords(c.verdict)}")
            appendLine("    ${c.detail}")
            appendLine("    condition: ${c.condition}")
        }
    }
    if (diffs.isNotEmpty()) {
        appendLine()
        appendLine("Against a second packet, per 100 ${r.unit.short}:")
        for (d in diffs) appendLine("- ${d.label}: ${d.aWords} vs ${d.bWords} ${d.unit} — ${d.verdict}")
    }
    if (r.notes.isNotEmpty()) {
        appendLine()
        appendLine("Caveats:")
        for (n in r.notes) appendLine("- $n")
    }
    appendLine()
    append("Generated on this device. No score, no health verdict, and no share of any individual's needs is")
    append(" produced here; reference intakes describe a 2,000 kcal reference diet set for labelling.")
}
