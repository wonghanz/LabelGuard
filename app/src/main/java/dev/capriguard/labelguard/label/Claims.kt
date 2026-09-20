package dev.capriguard.labelguard.label

/**
 * Front-of-pack claims, and what a printed table can and cannot settle about them.
 *
 * This is the part of the app most likely to be asked for and the part that has to
 * say no most often. "Reduced fat" is a comparison against a product the packet never
 * names, so no reading of this packet's own figures can confirm it, and pretending
 * otherwise would be the app inventing a fact. Each outcome therefore carries the
 * condition in words, so a refusal can be checked rather than trusted.
 */

enum class Verdict {
    SUPPORTED,
    NOT_SUPPORTED,
    NOT_CHECKABLE,
    NO_CONDITION,
}

data class ClaimOutcome(
    val phrase: String,
    val condition: String,
    val verdict: Verdict,
    val detail: String,
)

private class ClaimRule(
    val phrase: String,
    val pattern: Regex,
    val condition: String,
    val check: (Map<String, Figure>, EnergyRow?, SizeUnit, String) -> Pair<Verdict, String>,
)

private fun missing(): Pair<Verdict, String> =
    Verdict.NOT_CHECKABLE to "the table you typed has no row for it"

/** The whole rounding interval must clear the limit, or the claim is left unresolved. */
private fun atMost(f: Figure?, limit: Double, what: String, basis: String): Pair<Verdict, String> {
    if (f == null) return missing()
    return when {
        f.high <= limit -> Verdict.SUPPORTED to
            "${fmt(f.value)} $what per 100 $basis, and every value the pack may have rounded from stays at or under ${fmt(limit)}"
        f.low > limit -> Verdict.NOT_SUPPORTED to
            "${fmt(f.value)} $what per 100 $basis is above the ${fmt(limit)} the condition sets"
        else -> Verdict.NOT_CHECKABLE to
            "${fmt(f.value)} $what per 100 $basis sits on the ${fmt(limit)} line. The rounding this pack is allowed " +
                "to use covers it, so these figures neither support the claim nor contradict it"
    }
}

private fun atLeast(f: Figure?, limit: Double, what: String, basis: String): Pair<Verdict, String> {
    if (f == null) return missing()
    return when {
        f.low >= limit -> Verdict.SUPPORTED to
            "${fmt(f.value)} $what per 100 $basis clears the ${fmt(limit)} the condition sets, on every value the rounding allows"
        f.high < limit -> Verdict.NOT_SUPPORTED to
            "${fmt(f.value)} $what per 100 $basis is short of the ${fmt(limit)} required"
        else -> Verdict.NOT_CHECKABLE to
            "${fmt(f.value)} $what per 100 $basis reaches ${fmt(limit)} only once you count rounding, so the claim " +
                "cannot be settled from these figures"
    }
}

/** Percentage of the energy value contributed by a nutrient, on its Annex XV factor. */
private fun energyShare(per100: Map<String, Figure>, e: EnergyRow?, key: String, kJPerGram: Double): Double? {
    val f = per100[key] ?: return null
    if (e == null || e.kj.value <= 0.0) return null
    return kJPerGram * f.value / e.kj.value * 100.0
}

private fun proteinShareOutcome(per100: Map<String, Figure>, e: EnergyRow?): Pair<Verdict, String> {
    val share = energyShare(per100, e, "protein", 17.0)
        ?: return missing()
    val grams = per100["protein"]?.value
    val asWords = if (grams == null) "" else ", which is ${fmt(grams)} g per 100"
    return when {
        share >= 20.0 -> Verdict.SUPPORTED to
            "protein supplies ${fmt(share)}% of the energy$asWords. That is the figure for high in protein, and the " +
                "condition is written against the share of energy rather than the grams"
        share >= 12.0 -> Verdict.SUPPORTED to
            "protein supplies ${fmt(share)}% of the energy$asWords: enough for a source of protein, short of the 20% " +
                "needed for high in protein"
        else -> Verdict.NOT_SUPPORTED to
            "protein supplies ${fmt(share)}% of the energy$asWords, and the floor for either wording is 12%"
    }
}

object ClaimCatalogue {

    /**
     * Order matters where two patterns could both match, because the first hit wins.
     * The comparative wordings come first so that "lower fat" is never read as "low fat".
     */
    private val rules: List<ClaimRule> = listOf(
        ClaimRule(
            phrase = "reduced, less or lower fat",
            pattern = Regex("""(?i)\b(reduced|less|lower|more)\s(fat|sugar|salt|calorie|calories)\b"""),
            condition = "at least 30% less than a similar product, which the packet has to identify",
        ) { _, _, _, _ ->
            Verdict.NOT_CHECKABLE to
                "a comparative claim is a claim about a different product. Nothing here says which product it is 30% " +
                    "lower than, so these figures can neither confirm nor refute it. The regulation makes the " +
                    "comparison product part of the claim, and a packet that omits it has told you nothing checkable."
        },
        ClaimRule(
            phrase = "low fat",
            pattern = Regex("""(?i)\blow[\s-]?fat\b"""),
            condition = "no more than 3 g of fat per 100 g, or 1.5 g per 100 ml",
        ) { p, _, u, b ->
            atMost(p["fat"], if (u == SizeUnit.MILLILITRE) 1.5 else 3.0, "g of fat", b)
        },
        ClaimRule(
            phrase = "fat free",
            pattern = Regex("""(?i)\bfat[\s-]?free\b"""),
            condition = "no more than 0.5 g of fat per 100 g or 100 ml",
        ) { p, _, _, b -> atMost(p["fat"], 0.5, "g of fat", b) },
        ClaimRule(
            phrase = "low saturates",
            pattern = Regex("""(?i)\blow[\s-]?(saturated\s?fatty\s?acids?|saturated\s?fat|saturates)\b"""),
            condition = "no more than 1.5 g of saturates per 100 g, with a further condition on the share of energy",
        ) { p, e, u, b ->
            val first = atMost(p["saturates"], if (u == SizeUnit.MILLILITRE) 0.75 else 1.5, "g of saturates", b)
            val share = energyShare(p, e, "saturates", 37.0)
            val second = if (share == null) {
                "Without an energy row the share-of-energy half of the condition could not even be quoted."
            } else {
                "The saturates share of energy works out at ${fmt(share)}%. The Annex also sets a limit on that " +
                    "share, and this app does not rule on it: the condition is written against the whole fat profile, " +
                    "including the mono- and polyunsaturated figures a nutrition table does not print."
            }
            first.first to "${first.second}. $second"
        },
        ClaimRule(
            phrase = "sugar free",
            pattern = Regex("""(?i)\b(sugar|sugars)[\s-]?free\b|\bno\s+added\s+sugar(s)?\s+either\b"""),
            condition = "no more than 0.5 g of sugars per 100 g or 100 ml",
        ) { p, _, _, b -> atMost(p["sugars"], 0.5, "g of sugars", b) },
        ClaimRule(
            phrase = "no added sugars, or unsweetened",
            pattern = Regex("""(?i)\bno\s+added\s+sugar(s)?\b|\bunsweetened\b|\bwithout\s+sugar(s)?\s+added\b"""),
            condition = "no mono- or disaccharide, and no dried food used for its sweetening taste, has been added",
        ) { p, _, _, _ ->
            val s = p["sugars"]
            val tail = if (s != null && s.value >= 5.0) {
                "Sugars read ${fmt(s.value)} g per 100 here, which is fully compatible with the claim: fruit juice " +
                    "and milk reach that figure with nothing added at all."
            } else {
                "A low total neither supports nor undermines it, because the claim is about what was put in."
            }
            Verdict.NOT_CHECKABLE to
                "This is a statement about the ingredient list, not about the table. $tail"
        },
        ClaimRule(
            phrase = "source of fibre",
            pattern = Regex("""(?i)\bsources?\s+of\s+fibre\b|\bfibre\s+source\b"""),
            condition = "at least 3 g of fibre per 100 g",
        ) { p, _, _, b -> atLeast(p["fibre"], 3.0, "g of fibre", b) },
        ClaimRule(
            phrase = "high fibre",
            pattern = Regex("""(?i)\b(high|rich|richer)\s+(in\s+)?fibre\b"""),
            condition = "at least 6 g of fibre per 100 g",
        ) { p, _, _, b -> atLeast(p["fibre"], 6.0, "g of fibre", b) },
        ClaimRule(
            phrase = "high in protein",
            pattern = Regex("""(?i)\b(high|rich|richer)\s+(in\s+)?protein\b"""),
            condition = "at least 20% of the energy value supplied by protein",
        ) { p, e, _, _ -> proteinShareOutcome(p, e) },
        ClaimRule(
            phrase = "source of protein",
            pattern = Regex("""(?i)\bsources?\s+of\s+protein\b"""),
            condition = "at least 12% of the energy value supplied by protein",
        ) { p, e, _, _ -> proteinShareOutcome(p, e) },
        ClaimRule(
            phrase = "low salt or low sodium",
            pattern = Regex("""(?i)\blow[\s-]?(salt|sodium)\b"""),
            condition = "no more than 0.1 g of sodium per 100 g, which the same rule writes as 0.25 g of salt",
        ) { p, _, _, b ->
            val r = atMost(p["salt"], 0.25, "g of salt", b)
            r.first to r.second + ". A separate and equally published front-of-pack scale calls anything at or under " +
                "0.3 g of salt per 100 low. Both figures are official and they are not the same line, so the app " +
                "names the one it used rather than quietly picking the kinder one."
        },
        ClaimRule(
            phrase = "low energy or low calorie",
            pattern = Regex("""(?i)\blow[\s-]?(energy|calorie|calories|kcal)\b"""),
            condition = "no more than 40 kcal per 100 g of a solid, or 20 kcal per 100 ml of a drink",
        ) { _, e, u, _ ->
            val limit = if (u == SizeUnit.MILLILITRE) 20.0 else 40.0
            if (e == null) missing() else {
                // Energy is rounded to whole kJ and kcal, so the interval is the
                // declared one already carried on the figure, not a fresh guess.
                atMost(e.kcal, limit, "kcal", if (u == SizeUnit.MILLILITRE) "ml" else "g")
            }
        },
        ClaimRule(
            phrase = "light or lite",
            pattern = Regex("""(?i)\b(light|lite|lightly)\b"""),
            condition = "must say which characteristic was reduced, by how much, and against what",
        ) { _, _, _, _ ->
            Verdict.NOT_CHECKABLE to
                "the word alone fixes no figure. It means something only beside the statement of what was reduced " +
                    "and by how much, which is the part worth hunting for on the back."
        },
        ClaimRule(
            phrase = "natural",
            pattern = Regex("""(?i)\bnatural(ly)?\b"""),
            condition = "none laid down for a food described as natural",
        ) { _, _, _, _ ->
            Verdict.NO_CONDITION to
                "there is no definition of natural for a food in the claims regulation. The defined use is natural " +
                    "flavouring, which is narrower and not a claim about the food. Nothing in the table can settle " +
                    "what you are reading, so this app will not behave as though it can."
        },
        ClaimRule(
            phrase = "gluten free",
            pattern = Regex("""(?i)\bgluten[\s-]?free\b"""),
            condition = "no more than 20 mg of gluten per kg of the food as sold",
        ) { _, _, _, _ ->
            Verdict.NOT_CHECKABLE to
                "measured on the food itself in milligrams per kilogram. A nutrition table does not report gluten at " +
                    "all, so this is settled by the manufacturer's analysis and not by anything you can read off the packet."
        },
    )

    fun evaluate(
        text: String,
        per100: Map<String, Figure>,
        energy: EnergyRow?,
        unit: SizeUnit,
    ): List<ClaimOutcome> {
        if (text.isBlank()) return emptyList()
        val basis = if (unit == SizeUnit.MILLILITRE) "ml" else "g"
        val out = mutableListOf<ClaimOutcome>()
        val taken = mutableListOf<MatchResult>()
        for (rule in rules) {
            val m = rule.pattern.find(text) ?: continue
            if (taken.any { it.value.equals(m.value, ignoreCase = true) }) continue
            taken += m
            val (verdict, detail) = rule.check(per100, energy, unit, basis)
            out += ClaimOutcome("“${m.value.trim()}” — ${rule.phrase}", rule.condition, verdict, detail)
        }
        return out
    }

    /** The wordings recognised, so a reader can see what the app would have missed. */
    val recognised: List<String> get() = rules.map { it.phrase }

    fun verdictWords(v: Verdict): String = when (v) {
        Verdict.SUPPORTED -> "these figures support it"
        Verdict.NOT_SUPPORTED -> "these figures do not support it"
        Verdict.NOT_CHECKABLE -> "cannot be settled from a nutrition table"
        Verdict.NO_CONDITION -> "there is no condition to settle it against"
    }
}
