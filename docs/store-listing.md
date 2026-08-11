# Play Store Listing — Unit Converter

_Reflects app version 1.1.0 (versionCode 2). Per-version "What's new" text lives in
[release-notes.md](release-notes.md)._

## Short description (80 char max)

> Fast, offline unit converter — length, mass, temperature, shoe & shirt sizes

_(76 characters)_

### Alternatives
- Search, convert, copy — offline unit converter with no ads and no tracking _(74)_
- Convert length, weight, temperature, sizes & more — free, fast & offline _(72)_

---

## Full description (4000 char max)

```
Unit Converter is a fast, simple, and completely offline app for everyday conversions. Whether you're cooking, traveling, studying, shopping online, or working on a project, get accurate results instantly — no internet connection, no account, and no ads.

Just pick a category, type a value, and see every conversion update in real time.

★ KEY FEATURES ★

• Works 100% offline — no internet permission required
• No ads and no tracking — your data never leaves your device
• Instant, real-time results as you type
• Search every category and unit, including symbols — try "psi", "mpg" or "knot"
• Long-press any row to copy its value
• Picks up where you left off — your last value in each category is remembered
• Choose how many decimal places results are shown to
• Rearrange the categories into the order you use them
• Clean, modern design with light and dark themes
• Lightweight and fast

★ WHAT YOU CAN CONVERT ★

• Length — meters, feet, inches, miles, kilometers and more
• Mass / Weight — kilograms, pounds, ounces, grams, tons
• Temperature — Celsius, Fahrenheit, Kelvin
• Area — square meters, acres, hectares, square feet
• Volume — liters, gallons, cups, milliliters
• Speed — km/h, mph, knots, m/s
• Fuel Economy — mpg, L/100km, km/L
• EV Efficiency — for electric vehicles
• Time — seconds, minutes, hours, days
• Digital Storage — bytes, KB, MB, GB, TB
• Energy — joules, calories, kWh
• Pressure — bar, psi, pascal, atm
• Power — watts, horsepower, kilowatts
• Angle — degrees, radians
• Frequency — hertz, kHz, MHz
• Pay — hourly, monthly and yearly, worked out from your own hours per week
• Men's & Women's Shoe Sizes — US, UK, EU and more
• Men's & Women's Shirt Sizes — across regional sizing

★ WHY YOU'LL LOVE IT ★

Unit Converter is built to stay out of your way. There are no sign-ups, no pop-ups, and no clutter — just the conversions you need, whenever you need them. Because it runs entirely on your device, it's fast and works anywhere, even with no signal.

★ FEEDBACK ★

Have a suggestion, found a bug, or want a new conversion category added? You can send feedback right from inside the app — we read every message and are always adding new conversions.

Download Unit Converter today and make everyday conversions effortless!
```

_(2,384 characters — within the 4,000 limit)_

---

## Notes
- Categories reflect the 20 in `Units.kt`: Length, Mass, Temperature, Area, Volume,
  Speed, Fuel Economy, EV Efficiency, Time, Digital Storage, Energy, Pressure, Power,
  Angle, Frequency, Pay, Men's/Women's Shoe Size, Men's/Women's Shirt Size.
- Example units per category are illustrative; update if exact unit lists change.
- "No ads / no tracking / offline" claims match the build (no INTERNET permission,
  no ad/analytics SDKs). Adding currency conversion would break all three — rewrite
  this listing first if that ever ships.
- The Pay bullet assumes the adjustable work week added in 1.1.0; it defaults to 40
  hours (2080 per year) until the user changes it.
