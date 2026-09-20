package dev.capriguard.labelguard.label

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.capriguard.labelguard.core.AiClient
import dev.capriguard.labelguard.core.AiConfig
import dev.capriguard.labelguard.core.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything typed here stays in memory while the screen is up and is dropped when
 * the app stops. Nothing about a shopping trip is written to DataStore, to a file,
 * or to a log — there is no reason for a food label to persist, and a history of
 * what somebody buys is the sensitive part of this app, not the arithmetic.
 */
class LabelViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val ai = AiClient()

    var config by mutableStateOf(
        AiConfig(SettingsRepository.DEFAULT_BASE_URL, "", SettingsRepository.DEFAULT_MODEL, false),
    )
        private set

    init {
        viewModelScope.launch { settings.config.collect { config = it } }
    }

    fun setBaseUrl(v: String) { viewModelScope.launch { settings.setBaseUrl(v) } }
    fun setApiKey(v: String) { viewModelScope.launch { settings.setApiKey(v) } }
    fun setModel(v: String) { viewModelScope.launch { settings.setModel(v) } }
    fun setReaderOn(v: Boolean) { viewModelScope.launch { settings.setVisionOn(v) } }

    var packet by mutableStateOf(Packet())
        private set
    var other by mutableStateOf(Packet())
        private set
    var comparing by mutableStateOf(false)
        private set
    var reading by mutableStateOf<Reading?>(null)
        private set
    var otherReading by mutableStateOf<Reading?>(null)
        private set
    var diffs by mutableStateOf<List<DiffRow>>(emptyList())
        private set
    var problem by mutableStateOf<String?>(null)
        private set
    var plain by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busyRemote by mutableStateOf(false)
        private set

    fun typeField(key: String, raw: String) {
        packet = packet.with(key, raw)
        error = null
        if (reading != null) rerun()
    }

    fun typeOtherField(key: String, raw: String) {
        other = other.with(key, raw)
        if (otherReading != null) rerun()
    }

    fun typeClaims(v: String) {
        packet = packet.copy(claims = v)
        error = null
        if (reading != null) rerun()
    }

    fun typeOtherClaims(v: String) {
        other = other.copy(claims = v)
        if (otherReading != null) rerun()
    }

    fun pickUnit(u: SizeUnit) {
        packet = packet.copy(unit = u)
        rerun()
    }

    fun pickOtherUnit(u: SizeUnit) {
        other = other.copy(unit = u)
        if (comparing) rerun()
    }

    fun pickBasis(b: PrintedBasis) {
        packet = packet.copy(basis = b)
        rerun()
    }

    fun pickOtherBasis(b: PrintedBasis) {
        other = other.copy(basis = b)
        if (comparing) rerun()
    }

    /** Turns the second packet on, so two things can be put on one basis side by side. */
    fun startCompare() {
        comparing = true
        rerun()
    }

    fun stopCompare() {
        comparing = false
        other = Packet()
        otherReading = null
        diffs = emptyList()
        rerun()
    }

    fun rerun() {
        if (packet.isEmpty && other.isEmpty) {
            reading = null
            otherReading = null
            diffs = emptyList()
            problem = null
            return
        }
        run()
    }

    fun run() {
        error = null
        plain = null
        when (val r = Reader.read(packet)) {
            is Read.Complete -> {
                reading = r.reading
                problem = null
            }
            is Read.Incomplete -> {
                reading = null
                problem = r.message
            }
        }
        if (comparing) {
            when (val r = Reader.read(other)) {
                is Read.Complete -> {
                    otherReading = r.reading
                    val a = reading
                    diffs = if (a == null) emptyList() else Reader.compare(a, r.reading)
                }
                is Read.Incomplete -> {
                    otherReading = null
                    diffs = emptyList()
                    if (problem == null) problem = "Second packet: " + r.message
                }
            }
        }
    }

    fun clearAll() {
        packet = Packet()
        other = Packet()
        comparing = false
        reading = null
        otherReading = null
        diffs = emptyList()
        problem = null
        plain = null
        error = null
    }

    fun wipe() {
        packet = Packet()
        other = Packet()
        reading = null
        otherReading = null
        diffs = emptyList()
        plain = null
    }

    /**
     * The optional pass. What leaves the device is the normalised figures and the
     * claim outcomes — no product name, no shop, no barcode, nothing that identifies
     * what was bought. It exists because "the saturates row is 1.5 g per 100 g and the
     * pack says low saturates" is true but not obvious, and a sentence of plain words
     * is sometimes the difference. It adds no number that the arithmetic did not produce.
     */
    fun askReader() {
        val r = reading ?: return
        viewModelScope.launch {
            busyRemote = true
            error = null
            runCatching {
                val cfg = settings.current()
                if (!cfg.usable) throw IllegalArgumentException("Add a base URL, model and key in Settings first.")
                withContext(Dispatchers.IO) {
                    ai.complete(
                        endpoint = cfg.endpoint,
                        apiKey = cfg.apiKey,
                        model = cfg.model,
                        prompt = buildPrompt(r),
                        jpegBase64 = null,
                    )
                }
            }.onSuccess { plain = it.trim() }
                .onFailure { error = it.message ?: "The request failed." }
            busyRemote = false
        }
    }

    private fun buildPrompt(r: Reading): String = buildString {
        appendLine("You are the optional plain-words reader inside LabelGuard, an open-source Android app that")
        appendLine("recalculates what a food label says onto a shared basis. The arithmetic above has already")
        appendLine("been done on the device; you are only asked to put it into words.")
        appendLine()
        appendLine("Product type: ${if (r.unit == SizeUnit.MILLILITRE) "a drink, figures per 100 ml" else "a solid, figures per 100 g"}")
        appendLine("Label printed: ${r.basis.label}")
        if (r.serving != null) appendLine("Stated serving: ${fmt(r.serving)} ${r.unit.short}")
        if (r.net != null) appendLine("Packet weight: ${fmt(r.net)} ${r.unit.short}")
        appendLine("Figures per 100 ${r.unit.short}, each with the interval its rounding allows:")
        for (row in r.rows) {
            val f = row.per100
            appendLine("- ${row.nutrient.label}: ${fmt(f.value)} ${row.nutrient.unit} " +
                "(between ${fmt(f.low)} and ${fmt(f.high)})" +
                (if (row.level != null && row.band != null) " — " + bandWords(row.level, row.band) else ""))
        }
        r.energy?.let { appendLine("Energy: ${fmt(it.kj.value)} kJ / ${fmt(it.kcal.value)} kcal per 100 ${r.unit.short}. ${it.summary}") }
        if (r.claims.isNotEmpty()) {
            appendLine("Claims read off the front of the pack, and how each fared:")
            for (c in r.claims) {
                appendLine("- ${c.phrase}: ${ClaimCatalogue.verdictWords(c.verdict)}. Condition: ${c.condition}.")
            }
        }
        appendLine()
        appendLine("Task:")
        appendLine("- Two or three sentences of plain words about what stands out in these figures, written")
        appendLine("  for someone standing in a shop with the packet in their hand.")
        appendLine("Rules, all binding:")
        appendLine("- Do not recommend eating or avoiding it, and do not say healthy, unhealthy, good or bad.")
        appendLine("- Do not produce a score, a percentage of anyone's daily needs, a weight, or a time.")
        appendLine("- Do not state anything about a person's body, health, or circumstances; none was sent.")
        appendLine("- Do not introduce any figure that is not listed above, and do not re-derive one differently.")
        appendLine("- Where a claim could not be settled, keep saying it could not be settled.")
        appendLine("- If the figures are too thin to say anything useful about, reply exactly: too thin to read.")
        appendLine("- Format: the sentences only. No heading, no bullet points, no preamble.")
    }
}
