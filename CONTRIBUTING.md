# Contributing

Small repository, one rule that matters more than the rest: **every figure this
app can state must live in `label/Nutrients.kt` and point at a published source.**
No threshold, conversion factor, claim condition or rounding rule should appear
anywhere else, because that file is the whole audit surface. If a number cannot be
defended there, it does not belong in the app.

## What is here

```
app/src/main/java/dev/capriguard/labelguard/
├── label/Nutrients.kt   every published figure, the rounding rules, the bands
├── label/Reader.kt      normalisation, tolerances, cross-checks, comparison
├── label/Claims.kt      the claim wordings, their conditions, and their verdicts
├── label/LabelViewModel.kt  memory-only state, the optional text pass
└── ui/Screens.kt        every screen
```

`core/Core.kt` and `core/Settings.kt` are shared with the other Guard apps and
carry the visual system and the AI client. Changes there should be proposed in the
other repositories too, or not at all.

## The two reports worth filing

1. **A published figure that is wrong, stale or attributed loosely.** Name the
   regulation, guidance document or table, quote the line, and say what the app
   currently claims. If the figure is a comparison cut-off, changing it changes
   bands across every reading, so it needs a source and not a hunch.
2. **A reading that looks wrong.** Use *Copy this reading* and paste the result
   into the issue. The report text carries each figure's basis, its licensed
   interval and the band it fell into, so a disagreement can be traced to one line
   of arithmetic rather than argued in the abstract.

## Before you open a pull request

Build it:

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew --no-daemon :app:assembleDebug
```

`BUILD SUCCESSFUL` and an APK are the bar for a compile. Correctness is not checked
by the build, and there is no instrumented test in this repository and none is
planned: the app's risk is not crashes, it is stating a figure that is wrong.

The analysis layer has no Android dependency at all — `Nutrients.kt`, `Reader.kt`
and `Claims.kt` import nothing but `kotlin.math` — so it can be compiled and run on
a plain JVM against the Kotlin compiler jar your Gradle cache already holds. That
is how this code was verified before it shipped: roughly seventy assertions covering
comma decimals, the declared-zero allowance, the one-decimal-versus-two-decimal
rounding distinction, the per-serving normalisation, the sodium-to-salt conversion,
both kJ/kcal conventions, the macro energy sum under either fibre treatment, each
claim verdict including the ones that must come back unresolved, and the comparison
rows that must decline to pick a winner.

**No harness is checked in here.** It lives outside the repository and runs by hand.
If you want one in-tree, open an issue first: the argument for it is that a
regression in a threshold is silent and user-visible, and the argument against is
that a test file that asserts `22.5` is the high-sugars line is only ever going to
be a copy of the constant.

## The judgement calls, so they are not relitigated by accident

- **Bands carry no colour.** Tinting low green and high red is a health verdict
  delivered through design. Please do not "improve" the legibility of the band
  column that way.
- **No score.** A total over a whole table is the thing this app exists to
  replace. If you want to rank two packets, the compare screen's job is to say
  where they cannot be separated.
- **Refusals are features, not gaps.** "Reduced fat", "light", "natural" and
  "gluten free" returning *cannot be settled from a nutrition table* is correct
  behaviour, and a patch that manufactures an answer for them should not be merged.
- **No OCR.** Adding camera access converts a manual, checkable input into an
  inferred one, and would end the claim that nothing is read from your device.
