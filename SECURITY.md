# Security and safety

## The one thing to read first

**Do not use this app to manage an allergy, an intolerance or a medical diet.**
It reads a nutrition table. A nutrition table does not contain allergen
information, and this app has no ingredient-list parser, no manufacturer advisory
check and no product database. Its "gluten free" row is a worked example of the
limit: the condition is 20 mg of gluten per kg, measured on the food, and no table
on a pack reports it — so the answer it prints for that claim is *cannot be settled
from a nutrition table*, which is correct and is not the same as safe.

The same applies to "no added sugars", which is a statement about the ingredients
and not about the table, and to "natural", for which no condition exists.

If you eat for a medical reason, this app can help you read a label faster. It
cannot tell you whether a product is permitted for you, and it was not built to.

## Attack surface, stated plainly

- **No exported component except the launcher activity.** There is no share target
  and no `VIEW` intent filter, so no other app can hand this one data to process.
  Nothing is read from a URI, a file, an image or an archive, so there is no parser
  to fuzz — no `MediaStore` access, no `ExifInterface` call path, no WebView.
- **No runtime permission is requested** and none is needed: the manifest declares
  only `INTERNET`.
- **No persistence.** Nothing is written to DataStore, to a file, to a log or to a
  database. The typed figures live in a ViewModel in memory and are cleared on
  `ON_STOP`, so a session does not survive into the next launch.
- **No telemetry.** No analytics, crash reporting, advertising, attribution or
  error-reporting SDK is linked, in any build type.
- **No bundled credential.** The repository and the APK contain no key, token or
  endpoint other than the placeholder base URL string `https://api.openai.com` in
  `core/Settings.kt`, which is never contacted unless you type a key and turn the
  switch on.

## What leaves the device, and only if you configure it

The optional plain-words pass sends: the per-100 figures, each figure's rounding
interval, the reference basis, the energy cross-check outcome, and which claims
stood or failed. It does not send a product name, a brand, a shop, a barcode, a
photograph, or any identifier of a purchase — because the reading never held one to
send.

Transport is HTTPS to a host you typed, with your key in an `Authorization` header.
Be aware of what that means: **the endpoint you configure can see the nutritional
profile of whatever you were reading**, and a hostile or compromised endpoint could
return misleading prose. The UI separates generated text from the arithmetic and
labels it as generated, so the figures above it remain the app's own. If that
exposure is unacceptable, leave the switch off; the whole reading path works without
it and gains nothing from it.

## Honest residual risks

- **Recents and screenshots are not blocked.** Unlike the password checker in this
  family, LabelGuard does not set `FLAG_SECURE`, because a reading is useful to
  show somebody and there is no secret behind it. The consequence is that a task
  thumbnail or a screenshot can display the numbers you typed. If your threat model
  includes somebody with your unlocked phone, that is a disclosure to weigh.
- **A figure can be wrong and nothing will notice.** If you type 2.2 instead of
  22, the reading is confidently wrong. The kJ/kcal cross-check and the macro sum
  catch some transcription errors and are labelled as indicators rather than proofs,
  because they also fail on packs that round each unit independently or that count
  fibre differently.
- **The published numbers can move.** Thresholds, claim conditions and rounding
  allowances come from retained EU regulation and UK guidance and have diverged
  since 2020; the divergence for the figures used here is, as far as this repository
  can tell, nil, but nobody should assume that stays true. Each figure's source is
  named in `label/Nutrients.kt` so a reader can check the ones they care about.
- **US labels are only partly covered.** Normalisation works, because it is
  arithmetic. The bands and claim conditions are UK/EU figures, and the FDA's
  `% Daily Value` basis and reference amounts customarily consumed are different
  numbers on a different convention. The app does not pretend to have applied them.
- **Rounding is modelled from the printed decimals.** That is an inference about
  which allowance a manufacturer used, made from the precision they chose. It is a
  better inference than ignoring precision, and it is not certainty.

## Reporting

Open a [private security advisory](https://github.com/wonghanz/LabelGuard/security)
rather than an issue for anything that could leak typed figures, defeat the
no-persistence design, or make the app state a figure it has no source for. A wrong
threshold is not a vulnerability; it is a bug with a very specific file to fix, and
a plain issue is faster and more useful.
