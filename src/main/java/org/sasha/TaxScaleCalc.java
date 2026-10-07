package org.sasha;

import org.sasha.exception.CalculationInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Tax calculation based on salary and its corresponding tax rate.
 */
public class TaxScaleCalc {
    private final TaxScaleConfig config;
    private static final LibSettings  defaultLibSettings = new LibSettings(true);

    public TaxScaleCalc(TaxScaleConfig config){
        this(config, defaultLibSettings);
    }

    public TaxScaleCalc(TaxScaleConfig config, LibSettings libSettings){
        this.config = config;
        if (libSettings.enforceTaxRateIncrement()) {
            BigDecimal prev = null;
            for (BigDecimal rate : config.getScale().values()) {
                if (prev != null && rate.compareTo(prev) <= 0) {
                    throw new IllegalArgumentException("tax rates must increase with each band, got %s after %s".formatted(rate, prev));
                }
                prev = rate;
            }
        }
    }

    public Map.Entry<BigDecimal, BigDecimal> getBand(BigDecimal salary){
        return config.getScale().floorEntry(salary);
    }

    private BigDecimal getRateForSalary(BigDecimal salary){
        Map.Entry<BigDecimal, BigDecimal> band = config.getScale().floorEntry(salary);
        if (band == null){
            throw new CalculationInputException("salary %s below lowest band %s".formatted(salary, config.getScale().firstEntry().getKey()));
        }
        return band.getValue();
    }

    public BigDecimal getEffectiveTaxAmount(BigDecimal salary){
        return getRateForSalary(salary)
                .multiply(salary)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
