# LabelGuard — read a nutrition label on one basis, offline, on Android

**LabelGuard** takes the numbers off a food packet, puts every one of them onto
the basis the comparison thresholds are actually defined on, and checks the words
on the front against the table on the back. It prints the rounding the law allows
that packet to have used, so you can see how much of a figure is real. It never
gives the food a score, a rating, or a verdict about your health.

No upload. No account. No product database. No barcode lookup. No API key needed.

Type six numbers and it works in airplane mode. Build instructions are below.
The same page exists as a standalone, machine-readable document at
[`docs/index.html`](docs/index.html) in this repository.

---

## Why this exists

The same breakfast, two packets, both telling the truth:

| Packet | What its table says | Sugars, per 100 g |
| --- | --- | --- |
| A | 22 g per 100 g | 22 g |
| B | 6.6 g per 30 g serving | 22 g |

Packet B is not better. It is the same number, moved onto a portion somebody else
chose. Nearly every "is this a lot?" judgement made from a label in a shop is made
across that gap, and the packs that print per-serving columns are usually the ones
whose per-100 figure would read worse.

The skill this came from is a nutrition-advice prompt: a persona that collects your
allergies and goals into a database and tells you what to eat. That translation does
not belong on a phone, because a phone cannot weigh a person and has no business
keeping a record of their health. **The honest version of "is this food" on a device
you already hold is the arithmetic the packet refuses to do for you.** So LabelGuard
answers a different question — *what does this label say, on a shared basis, and how
much of it can it actually mean?* — and answers it without learning anything about you.

## What it actually runs

| Pass | What it does | Needs network? |
| --- | --- | --- |
| Normalise | Moves every declared row onto per 100 g/ml, the stated serving and the whole packet, from whichever column you typed | No |
| Tolerance | Reads the rounding quantum off *how many decimals the pack printed*, and shows the interval each figure licenses | No |
| Band | Applies the published per-100 comparison cut-offs, and prints "on the line" when an interval straddles one | No |
| Claim check | Matches the words on the front against the condition laid down for that wording, then tests it against the table | No |
| Cross-check | Tests the pack's own kJ against its own kcal, and the macros against the printed energy | No |
| Compare | Puts a second packet on the same basis and says plainly where the two cannot be told apart | No |
| Plain words | Optional: sends the figures, not the product, to your own endpoint for two sentences | Yes, only if you configure it |

The whole product type, unit, salt-or-sodium conversion, reference-intake share and
per-energy percentage are computed on the device from what you type.

## The thresholds, published

Nothing here is invented by this app. Each row is a figure a labelling authority
publishes, so each row can be argued with.

Comparison cut-offs, **defined per 100 g or per 100 ml and nowhere else**:

| Nutrient | Low is | High above (foods) | High above (drinks) |
| --- | --- | --- | --- |
| Fat | ≤ 3 g | 17.5 g | 9 g |
| Saturates | ≤ 1.5 g | 5 g | 4.5 g |
| Total sugars | ≤ 5 g | 22.5 g | 11 g |
| Salt | ≤ 0.3 g | 1.5 g | 0.75 g |
| Fibre (foods) | < 3 g is not a source | ≥ 3 g a source · ≥ 6 g high | — |

Conditions for the wordings on the front of the pack:

| Wording | The condition it has to meet | Can a table settle it? |
| --- | --- | --- |
| low fat | ≤ 3 g/100 g, or 1.5 g/100 ml | Yes |
| fat free | ≤ 0.5 g/100 g | Yes |
| low saturates | ≤ 1.5 g/100 g, plus a share-of-energy term | Partly — the energy half is stated, not ruled on |
| sugar free | ≤ 0.5 g/100 g | Yes |
| no added sugars | nothing sweetening was added | **No** — that is the ingredient list, and juice passes it at 11 g/100 ml |
| source of fibre · high fibre | ≥ 3 g/100 g · ≥ 6 g/100 g | Yes |
| source of protein · high in protein | ≥ 12% · ≥ 20% of the energy value | Yes, and against the share, not the grams |
| low sodium or low salt | ≤ 0.1 g sodium/100 g, written as 0.25 g salt | Yes |
| low energy | ≤ 40 kcal/100 g, or 20 kcal/100 ml | Yes |
| reduced, less, lower | 30% below a similar product the pack must name | **No** — the other product was never sent |
| light, lite | whatever the pack says next to it | **No** |
| natural | none laid down for a food | **No — there is no condition to check** |
| gluten free | ≤ 20 mg/kg of gluten | **No** — not a row on any table |

Salt is the one figure with two official lines: the claim condition sits at
0.25 g per 100 g, and a widely printed front-of-pack scale calls anything at or
under 0.3 g low. Both are kept, and the app names the one it used rather than
quietly picking the kinder number.

## The rounding, which is the interesting part

A printed figure is a rounded figure. Three rules apply at once:

- Energy is declared to whole kJ and whole kcal.
- Fat, saturates, carbohydrate, sugars, protein and salt are declared to the
  nearest 0.1 g — **except** that 0.2 g may be used below 1.5 g per 100 g.
- A value printed as `0` does not mean none. It means at or below the
  contains-no threshold: 0.5 g per 100 g for fat and sugars, 0.1 g for
  saturates.

So LabelGuard reads the quantum off the precision the pack itself committed to.
A row printed `0.28` cannot have come from 0.2 g steps, so it is held to
±0.005. A row printed `0.4` on a low-value line gives nothing away, so it is
held to ±0.1. And where the interval crosses a cut-off, the band is printed as
**"on the line"** instead of being resolved by guessing:

> `22.5 g sugars per 100 g` — rounding allows 22.4 to 22.6, and the high-sugars
> line is 22.5. The app declines to call this high or middle, and says why.

That is also why two packets 0.1 g apart cannot be ranked. In compare mode, where
the intervals overlap, the row prints *"too close to tell apart, given the
rounding both packs are allowed"* rather than inventing a winner.

## What it will not tell you

Stated as plainly as the rest, because this is where a food app goes wrong:

- **No score, no rating, no traffic light of its own.** One number over a whole
  table is what makes a label unreadable.
- **No "healthy" or "unhealthy".** Those are statements about a person's whole
  diet and body, neither of which is in this app.
- **No share of *your* needs.** The reference intakes it prints describe a
  2,000 kcal / 8,400 kJ reference diet chosen for labelling. It is not a target
  and was never measured against you.
- **No band is coloured.** Low-sugar rows and high-sugar rows are the same grey,
  on purpose: green-red on those words *is* the health verdict this app refuses.
- **No serving-size advice**, no ingredient list, no allergens, no additives, no
  origin, no weight-loss claims, no BMI, no period or cycle features.
- **No product database.** It cannot look up what you are holding, which is the
  same fact that stops it learning what you buy.

## Bring your own key, and it is genuinely optional

Settings holds a base URL, a model name and a key. They go to this app's private
DataStore and nowhere else; the manifest sets `allowBackup=false`, so they cannot
ride out in a cloud backup, a device transfer or an `adb backup`.

The optional pass sends the per-100 figures, their rounding intervals and which
claims stood or failed. It sends no product name, no shop and nothing that
identifies a purchase, because the reading never held any of that. The prompt
forbids recommending the product, calling it healthy, and introducing any figure
not already in the message; the output is labelled as generated and kept apart
from the arithmetic on screen.

Turn the switch off and the app makes zero requests. Nothing else changes: the
plain-words pass adds no number.

## Requirements

- Android 8.0 (API 26) or newer
- **No runtime permission is requested.** The manifest declares only `INTERNET`,
  for the optional pass you configure. There is no share target, because a
  nutrition table is copied off a packet, not sent to you as text.

## Build from source

```bash
git clone https://github.com/wonghanz/LabelGuard.git
cd LabelGuard
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew --no-daemon :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, compileSdk 35. The debug APK is
signed with the standard Android debug key.

## Privacy, stated

- Nothing is read from your device. Every figure is typed, so there is no
  gallery, file, camera, account or clipboard access to grant and none to withhold.
- No analytics, no crash reporter, no telemetry, no ad or attribution SDK.
- The form is wiped when the app leaves the foreground, so a half-filled packet
  does not sit in a recent-apps thumbnail or survive into the next session.
- Screenshots are **not** blocked. A reading is the sort of thing you would want
  to show somebody, and there is nothing sensitive behind it to protect.
- The only outbound code path is the one you configure, and it carries figures.

## FAQ

**Can LabelGuard tell me if a food is healthy?**
No, and it will not try. It applies published per-100 g comparison thresholds and
says where a figure sits against them, which is a claim about the packet. Whether
a food is healthy depends on the rest of a diet and a body, neither of which this
app knows or stores.

**How do I remove the %RI confusion from a label?**
Type the row. The app shows the reference-intake share the pack implies *and*
states that the reference is a 2,000 kcal diet fixed for labelling purposes, which
is the part packs leave out.

**Why does my packet's 0 g of sugar still worry it?**
Because a declared zero means "at or below the contains-no threshold", which for
sugars is 0.5 g per 100 g. LabelGuard prints that interval instead of treating a
zero as an absence.

**Why does it say "on the line" instead of picking a band?**
Because the rounding that packet is allowed to have used straddles the cut-off.
Choosing a side would be the app guessing where the law says a range is legitimate.

**Does it work with UK, EU or US labels?**
The arithmetic, unit conversions and per-100 normalisation work with any of them.
The bands and claim conditions are the UK/EU published sets, so a US panel-only
label will produce fewer findings; per-serving US figures normalise onto 100 g the
same way. Anything the app cannot match is reported as unchecked, not as fine.

**Can it scan a label photo?**
No. There is no camera permission and no vision call in this build, so there is no
OCR to be wrong about. You type what you can read, which is also the only way to be
sure a digit was not misread by a model.

**Do I need an account or an API key?**
Neither. Every pass works offline with no configuration. A key only unlocks the
optional plain-words rewrite.

**Why is there no comparison score between two packets?**
Because "which is better" needs a goal, and a goal needs a person. What it does
give is both packets on one basis, row by row, with an explicit statement wherever
their rounding intervals overlap and the two genuinely cannot be told apart.

**Will it store what I buy?**
No. Nothing is written to disk at all — not the packet, not the reading, not a
history. That is the design, not a setting.

**I weigh 60 kg, how much of my daily sugar is this?**
The app will not answer that, and it is worth being suspicious of anything that
does. Its percentages are shares of a reference diet the law picked for an average
adult of unspecified weight, sex and activity.

## Repo map

```
app/src/main/java/dev/capriguard/labelguard/
├── MainActivity.kt            two routes, and the wipe on backgrounding
├── core/Core.kt               palette, continuous-corner shapes, glass, type ramp
├── core/Settings.kt           DataStore settings and the OpenAI-compatible client
├── label/Nutrients.kt         every published figure this app can quote, in one file
├── label/Reader.kt            normalisation, tolerances, bands, cross-checks, compare
├── label/Claims.kt            the claim wordings, their conditions, and what fails
├── label/LabelViewModel.kt    memory-only state and the optional text pass
└── ui/Screens.kt              every screen
```

## Contributing

Issues and pull requests welcome. Two kinds of report are the most useful here:

1. **A figure in `label/Nutrients.kt` that is wrong or out of date**, with the
   source. That file is the whole audit surface — every threshold, condition and
   conversion the app can state lives there, and nothing should be in it that
   cannot be pointed at.
2. **A reading that looks wrong**, from the Copy button. The report text carries
   the basis, the interval and the band, so a disagreement can be traced to one
   line of arithmetic.

## License

MIT — see [LICENSE](LICENSE).
