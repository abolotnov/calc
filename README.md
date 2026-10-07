# calc

Tax calculator library: looks up the tax rate for a salary in a band scale and computes the tax amount. Requires Java 21+.

```java
// bands: built-in defaults, or load from YAML/JSON (file or https URI)
TaxScaleConfig config = TaxScaleConfig.fromFile(Path.of("tax_bands.yaml"));
// TaxScaleConfig.fromURI(URI.create("https://example.com/tax_bands.json"));  // optional 2nd arg: timeout in seconds
// TaxScaleConfig.getDefaultConfig();

TaxScaleCalc calc = new TaxScaleCalc(config);
BigDecimal tax = calc.getEffectiveTaxAmount(new BigDecimal("55000")); // 4537.50
```

## Flat vs progressive

- `getEffectiveTaxAmount(salary)` is **flat**: the whole salary is taxed at the rate of the band it falls in.
- `getEffectiveProgressiveTaxAmount(salary)` is **progressive**: each slice of the salary is taxed at its own band's rate, then the slices are summed.
- `getProgressiveScaleRates(salary)` returns the slices as a `List<Portion>` (`amount`, `taxRate`) in band order, e.g. for the default bands:

```java
calc.getProgressiveScaleRates(new BigDecimal("55000"));
// [10000 @ 1.89, 10000 @ 2.5, 30000 @ 4.75, 5000 @ 8.25]
calc.getEffectiveProgressiveTaxAmount(new BigDecimal("55000")); // 2276.50 (flat: 4537.50)
```

Progressive calculation notes:
- Each slice's tax is rounded to 2 decimal places before summing, so the result always has scale 2.
- A band covers `from` up to the next band's `from`; the last band has no upper limit.
- Scales with a negative `from` are not supported (`CalculationInputException`).

Band format (see [docs/tax_bands.yaml](docs/tax_bands.yaml), [docs/tax_bands.json](docs/tax_bands.json)): `from` is the band's inclusive lower bound, `rate` is the tax percentage.

`new TaxScaleCalc(config, new LibSettings(enforceTaxRateIncrement))` tunes behavior:
- `enforceTaxRateIncrement` (default `true`): rates must strictly increase with each band, else `IllegalArgumentException`.

Salaries below the lowest band (including negative ones, with the default bands) throw `CalculationInputException`; to allow negative salaries, define a band with a negative `from`.
Progressive calculations do not support negative salaries, mostly because I think semantically losses must run against their own separate scale and have a different set of methods to run the numbers. 

Loading failures throw `ConfigLoadException`.
