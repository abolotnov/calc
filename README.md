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

Band format (see [docs/tax_bands.yaml](docs/tax_bands.yaml), [docs/tax_bands.json](docs/tax_bands.json)): `from` is the band's inclusive lower bound, `rate` is the tax percentage.

`new TaxScaleCalc(config, new LibSettings(enforceTaxRateIncrement))` tunes behavior:
- `enforceTaxRateIncrement` (default `true`): rates must strictly increase with each band, else `IllegalArgumentException`.

Salaries below the lowest band (including negative ones, with the default bands) throw `CalculationInputException`; to allow negative salaries, define a band with a negative `from`.

Loading failures throw `ConfigLoadException`.
