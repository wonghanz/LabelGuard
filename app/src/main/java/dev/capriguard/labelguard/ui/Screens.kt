package dev.capriguard.labelguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.capriguard.labelguard.core.GlassCard
import dev.capriguard.labelguard.core.LiquidBackdrop
import dev.capriguard.labelguard.core.LocalGlass
import dev.capriguard.labelguard.core.Palette
import dev.capriguard.labelguard.core.Radii
import dev.capriguard.labelguard.core.tnum
import dev.capriguard.labelguard.label.BandLevel
import dev.capriguard.labelguard.label.ClaimCatalogue
import dev.capriguard.labelguard.label.DiffRow
import dev.capriguard.labelguard.label.EnergyRow
import dev.capriguard.labelguard.label.LabelViewModel
import dev.capriguard.labelguard.label.Packet
import dev.capriguard.labelguard.label.PrintedBasis
import dev.capriguard.labelguard.label.RefIntakes
import dev.capriguard.labelguard.label.Reader
import dev.capriguard.labelguard.label.Reading
import dev.capriguard.labelguard.label.Row as NutrientRow
import dev.capriguard.labelguard.label.SizeUnit
import dev.capriguard.labelguard.label.Verdict
import dev.capriguard.labelguard.label.bandWords
import dev.capriguard.labelguard.label.fmt
import dev.capriguard.labelguard.label.reportText

/* ------------------------------------------------------------------ shell */

@Composable
fun ScreenShell(
    title: String,
    onBack: (() -> Unit)?,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    LiquidBackdrop(base = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconBadge(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                } else {
                    Spacer(Modifier.width(40.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun IconBadge(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(Radii.PillShape)
            .background(LocalGlass.current.scrim)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val tokens = LocalGlass.current
    Box(
        modifier = Modifier
            .clip(Radii.PillShape)
            .background(if (on) MaterialTheme.colorScheme.primary else tokens.scrim)
            .border(0.7.dp, if (on) MaterialTheme.colorScheme.primary else tokens.hairline, Radii.PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/* ------------------------------------------------------------------- home */

@Composable
fun HomeScreen(vm: LabelViewModel, onOpenSettings: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val r = vm.reading

    ScreenShell(title = "LabelGuard", onBack = null, action = {
        IconBadge(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
    }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (r == null && vm.packet.isEmpty && !vm.comparing) IntroCard()

            BasisCard(vm)
            PacketForm(
                title = if (vm.comparing) "Packet A" else "The packet in your hand",
                packet = vm.packet,
                onField = vm::typeField,
                onClaims = vm::typeClaims,
                claimsLabel = "The wording on the front, as printed",
            )

            Button(
                onClick = { vm.run() },
                shape = Radii.ControlShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Read the label", style = MaterialTheme.typography.labelLarge)
            }

            vm.problem?.let { WhyCard(it) }

            if (r != null) {
                EnergyCard(r.energy, r.unit)
                ReadingCard(r)
                ClaimsCard(r)
                NotesCard(r.notes)

                if (!vm.comparing) {
                    OutlinedAction(
                        icon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                        text = "Compare a second packet",
                        onClick = vm::startCompare,
                    )
                } else {
                    PacketForm(
                        title = "Packet B",
                        packet = vm.other,
                        onField = vm::typeOtherField,
                        onClaims = vm::typeOtherClaims,
                        claimsLabel = "Its wording",
                    )
                    vm.otherReading?.let { o ->
                        CompareCard(Reader.compare(r, o))
                        OutlinedAction(
                            icon = { Icon(Icons.Filled.Close, null, Modifier.size(18.dp)) },
                            text = "Stop comparing",
                            onClick = vm::stopCompare,
                        )
                    }
                }

                PlainCard(vm)

                OutlinedAction(
                    icon = { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)) },
                    text = "Copy this reading",
                    onClick = { clipboard.setText(AnnotatedString(reportText(r, vm.diffs))) },
                )
                RefusalCard()
                OutlinedAction(
                    icon = { Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)) },
                    text = "Clear everything",
                    onClick = vm::clearAll,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun OutlinedAction(icon: @Composable () -> Unit, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radii.ControlShape)
            .border(0.7.dp, LocalGlass.current.hairline, Radii.ControlShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun IntroCard() {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Type the table, not the brand", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Two packets of nearly the same thing can print the same true figure and read completely " +
                    "differently, because one shows per 100 g and the other a 30 g serving its manufacturer " +
                    "suggested. Enter whichever column you have, and LabelGuard works out every other basis from " +
                    "it — carrying the rounding that packet was allowed to use.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Then it checks the words on the front against the numbers on the back, and says plainly which " +
                    "claims no table could ever settle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WhyCard(why: String) {
    GlassCard(padding = 15.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Info, null, Modifier.size(18.dp), tint = Palette.Warm)
            Text(why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BasisCard(vm: LabelViewModel) {
    GlassCard(padding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("The column you are copying")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("per 100 g or ml", vm.packet.basis == PrintedBasis.PER_100) { vm.pickBasis(PrintedBasis.PER_100) }
                Chip("per serving", vm.packet.basis == PrintedBasis.PER_SERVING) { vm.pickBasis(PrintedBasis.PER_SERVING) }
            }
            SectionTitle("Sold by")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("grams", vm.packet.unit == SizeUnit.GRAM) { vm.pickUnit(SizeUnit.GRAM) }
                Chip("millilitres", vm.packet.unit == SizeUnit.MILLILITRE) { vm.pickUnit(SizeUnit.MILLILITRE) }
            }
            if (vm.comparing) {
                SectionTitle("Packet B, where it differs")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("per 100", vm.other.basis == PrintedBasis.PER_100) { vm.pickOtherBasis(PrintedBasis.PER_100) }
                    Chip("per serving", vm.other.basis == PrintedBasis.PER_SERVING) { vm.pickOtherBasis(PrintedBasis.PER_SERVING) }
                    Chip(
                        if (vm.other.unit == SizeUnit.GRAM) "grams" else "millilitres",
                        true,
                    ) {
                        vm.pickOtherUnit(if (vm.other.unit == SizeUnit.GRAM) SizeUnit.MILLILITRE else SizeUnit.GRAM)
                    }
                }
            }
        }
    }
}

@Composable
private fun PacketForm(
    title: String,
    packet: Packet,
    onField: (String, String) -> Unit,
    onClaims: (String) -> Unit,
    claimsLabel: String,
) {
    val b = packet.unit.short
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(title)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField(Reader.SERVING, "Serving, $b", "30", packet, onField, Modifier.weight(1f))
                AmountField(Reader.NET, "Whole packet, $b", "375", packet, onField, Modifier.weight(1f))
            }
            Text(
                "Neither is required. A serving shows you the portion the pack chose; the packet weight shows " +
                    "you what finishing the bag actually is.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField("energyKj", "Energy, kJ", "1600", packet, onField, Modifier.weight(1f))
                AmountField("energyKcal", "Energy, kcal", "382", packet, onField, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField("fat", "Fat, g", "9", packet, onField, Modifier.weight(1f))
                AmountField("saturates", "of which saturates, g", "1.1", packet, onField, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField("carbohydrate", "Carbohydrate, g", "65", packet, onField, Modifier.weight(1f))
                AmountField("sugars", "of which sugars, g", "22", packet, onField, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField("fibre", "Fibre, g", "6.8", packet, onField, Modifier.weight(1f))
                AmountField("protein", "Protein, g", "9", packet, onField, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField("salt", "Salt, g", "0.42", packet, onField, Modifier.weight(1f))
                AmountField("sodium", "Sodium, g, if that is printed", "0.17", packet, onField, Modifier.weight(1f))
            }
            Text(
                "Type whichever column your packet shows. A decimal comma is fine, and a row you leave out is " +
                    "left out of the reading rather than guessed at.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = packet.claims,
                onValueChange = onClaims,
                label = { Text(claimsLabel, style = MaterialTheme.typography.labelMedium) },
                placeholder = {
                    Text("high in protein · low fat · no added sugars · natural", style = MaterialTheme.typography.bodySmall)
                },
                shape = Radii.ControlShape,
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Default),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Wordings it knows: ${ClaimCatalogue.recognised.joinToString(" · ")}. Anything else on the front " +
                    "is reported as unchecked, because it is unchecked.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AmountField(
    key: String,
    label: String,
    hint: String,
    packet: Packet,
    onField: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = packet.raw(key),
        onValueChange = { onField(key, it) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        shape = Radii.ControlShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier,
    )
}

/* ----------------------------------------------------------------- result */

@Composable
private fun EnergyCard(e: EnergyRow?, unit: SizeUnit) {
    if (e == null) return
    GlassCard(padding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SectionTitle("Energy, per 100 ${unit.short}")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fmt(e.kj.value), style = MaterialTheme.typography.headlineMedium.tnum())
                Text("kJ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(e.kcal.value), style = MaterialTheme.typography.headlineMedium.tnum())
                Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(e.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!e.conversionOk || e.sumMatches == false) {
                Text(
                    "That is usually a mistyped digit rather than a wrong packet. Read the row again before " +
                        "trusting either answer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Warm,
                )
            }
        }
    }
}

@Composable
private fun ReadingCard(r: Reading) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionTitle("Every basis, from the one you typed")
            r.rows.forEach { RowLine(it, r.unit, r.serving) }
            Text(
                "The words on the right are the published per-100 comparison cut-offs and nothing else. They are " +
                    "printed in one colour deliberately: tinting low green and high red would be a health verdict, " +
                    "which this app does not make.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun RowLine(row: NutrientRow, unit: SizeUnit, serving: Double?) {
    val unresolved = row.level == BandLevel.ON_THE_LINE
    val printedZero = row.printed.value == 0.0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                row.nutrient.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(fmt(row.per100.value), style = MaterialTheme.typography.titleMedium.tnum())
            Text(
                "${row.nutrient.unit}/100${unit.short}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val band = row.band
            val level = row.level
            if (band != null && level != null) {
                Box(
                    Modifier
                        .clip(Radii.PillShape)
                        .border(0.7.dp, LocalGlass.current.hairline, Radii.PillShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        bandWords(level, band),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unresolved) Palette.Warm else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            row.ri?.let {
                Text(it.words, style = MaterialTheme.typography.labelSmall.tnum(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val others = buildString {
            row.perServing?.let {
                append("${fmt(it.value)} ${row.nutrient.unit} in a serving of ${fmt(serving ?: 0.0)} ${unit.short}")
            }
            row.perPacket?.let {
                if (isNotEmpty()) append("   ·   ")
                append("${fmt(it.value)} ${row.nutrient.unit} in the whole packet")
            }
        }
        if (others.isNotEmpty()) {
            Text(others, style = MaterialTheme.typography.bodySmall.tnum(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (unresolved || printedZero || row.note != null) {
            Text(
                buildString {
                    if (printedZero) {
                        val ceiling = row.nutrient.zeroCeiling
                        append("A printed 0 means none was detectable at the level the law allows, not none at all")
                        if (ceiling != null) append(" (up to ${fmt(ceiling)} per 100 ${unit.short} is permitted)")
                    } else {
                        append(row.per100.tol.why)
                    }
                    row.note?.let { append(". ").append(it) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Warm,
            )
        }
    }
}

@Composable
private fun ClaimsCard(r: Reading) {
    if (r.claims.isEmpty()) return
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            SectionTitle("The front of the pack, against the back")
            r.claims.forEach { c ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(Radii.PillShape)
                                .background(
                                    when (c.verdict) {
                                        Verdict.SUPPORTED -> Palette.Calm
                                        Verdict.NOT_SUPPORTED -> Palette.Alert
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                ),
                        )
                        Text(c.phrase, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    }
                    Text(
                        ClaimCatalogue.verdictWords(c.verdict),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(c.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Condition: ${c.condition}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesCard(notes: List<String>) {
    if (notes.isEmpty()) return
    GlassCard(padding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SectionTitle("What it would not assume")
            notes.forEach {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, null, Modifier.size(15.dp), tint = Palette.Warm)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CompareCard(diffs: List<DiffRow>) {
    if (diffs.isEmpty()) return
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("Both packets, on the basis the cut-offs share")
            diffs.forEach { d ->
                Column(Modifier.padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(d.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(d.aWords, style = MaterialTheme.typography.titleSmall.tnum())
                        Text("vs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(d.bWords, style = MaterialTheme.typography.titleSmall.tnum())
                        Text(d.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(d.verdict, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "No winner is named. Where the two readings overlap once each packet's rounding is counted, the " +
                    "honest answer is that they cannot be told apart on that row, and that is what prints there.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlainCard(vm: LabelViewModel) {
    val on = vm.config.usable
    GlassCard(padding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("In plain words, if you want it")
            Text(
                if (on) {
                    "Sends the normalised figures and the claim outcomes to your own endpoint. No product name, " +
                        "no shop, nothing that says what you bought. It cannot add a number the arithmetic did not."
                } else {
                    "Optional, and off. It needs an endpoint you configure in Settings, and the reading above " +
                        "stands on its own without it."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (on) {
                Button(
                    onClick = vm::askReader,
                    enabled = !vm.busyRemote,
                    shape = Radii.ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                ) {
                    Text(if (vm.busyRemote) "Asking your endpoint…" else "Rewrite the reading", style = MaterialTheme.typography.labelLarge)
                }
            }
            vm.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Alert) }
            vm.plain?.let {
                Box(Modifier.fillMaxWidth().clip(Radii.ControlShape).background(LocalGlass.current.scrim).padding(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Generated by your endpoint from the figures above. Not part of the arithmetic.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Warm,
                        )
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun RefusalCard() {
    GlassCard(padding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SectionTitle("What this will not tell you")
            listOf(
                "No score, and no rating out of anything. One number over a whole table is the thing that makes a " +
                    "label unreadable.",
                "No healthy or unhealthy. Those are statements about a person's whole diet and body, neither of " +
                    "which is in this app or inferable from one packet.",
                "No share of your needs. The reference intakes shown describe the " +
                    fmt(RefIntakes.energyKcal) + " kcal diet the law picked for comparison. It is not a target for " +
                    "you and was never measured against you.",
                "No serving-size advice, and no view on whether a portion is right.",
                "No ingredient list, allergens, additives, origin, or weight-loss claims. The table, and the words " +
                    "printed on the front, and nothing else.",
                "Nothing is uploaded to check it. There is no product database behind this, so it cannot look up " +
                    "what you are holding — which is also why it cannot tell you anything it has not been told.",
            ).forEach {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(4.dp).clip(Radii.PillShape).background(MaterialTheme.colorScheme.outline))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/* --------------------------------------------------------------- settings */

@Composable
fun SettingsScreen(vm: LabelViewModel, onBack: () -> Unit) {
    var reveal by remember { mutableStateOf(false) }

    ScreenShell(title = "Settings", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Recalculating a table, applying the published cut-offs and checking a claim against it all happen " +
                    "on the device and need none of this. The endpoint below is only for the optional plain-words pass.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Plain-words pass", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = vm.config.visionOn,
                    onCheckedChange = vm::setReaderOn,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
            SettingsField("Base URL", vm.config.baseUrl, "https://api.example.com", onDone = vm::setBaseUrl)
            SettingsField("Model", vm.config.model, "gpt-4o-mini", onDone = vm::setModel)
            SettingsField(
                label = "API key",
                value = vm.config.apiKey,
                placeholder = "Typed here, kept on this device",
                masked = !reveal,
                secret = true,
                onDone = vm::setApiKey,
            )
            Text(
                "What goes out when you use it: the per-100 figures, the intervals their rounding allows, and " +
                    "which claims stood or failed. The prompt forbids recommending the product, calling it healthy, " +
                    "or introducing any figure not already in the message.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { reveal = !reveal }) {
                Text(if (reveal) "Hide key" else "Show key", style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "The key lives in this app's private storage and the manifest sets allowBackup=false, so it cannot " +
                    "ride out in a cloud backup, a device transfer or an adb backup. This build ships without a key " +
                    "and without anyone else's quota.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    placeholder: String,
    onDone: (String) -> Unit,
    masked: Boolean = false,
    secret: Boolean = false,
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        singleLine = !secret,
        visualTransformation = if (masked) androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone(text) }),
        shape = Radii.ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
