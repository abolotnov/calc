package org.sasha;

import org.sasha.entity.LibSettings;
import org.sasha.entity.Portion;
import org.sasha.entity.TaxScaleConfig;
import org.sasha.exception.CalculationInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tax calculation based on salary and its corresponding tax rate.
 */
public class TaxScaleCalc {
    private final TaxScaleConfig config;
    private static final LibSettings defaultLibSettings = new LibSettings(true);

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

    private BigDecimal getEffectiveTaxAmountForRate(BigDecimal salary, BigDecimal rate){
        return rate.multiply(salary).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getEffectiveTaxAmount(BigDecimal salary){
        return getRateForSalary(salary)
                .multiply(salary)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getEffectiveProgressiveTaxAmount(BigDecimal salary){
        return getProgressiveScaleRates(salary).stream()
                .map(p -> getEffectiveTaxAmountForRate(p.amount(), p.taxRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Splits the salary across the scale's brackets, each portion taxed at its own bracket rate.
     * For instance, if the brackets are 0-20K 5% and 20K+ 8%,
     * a 50K salary produces [20K at 5%, 30K at 8%].
     * Does not support scales with negative bands.
     * @param salary - amount earned
     * @return portions of the salary in bracket order, each with its bracket rate
     * @throws CalculationInputException if the scale has negative bands or the salary is below the lowest band
     */
    public List<Portion> getProgressiveScaleRates(BigDecimal salary){
        var scale = config.getScale();
        if (scale.firstKey().signum() < 0){
            throw new CalculationInputException("Progressive calculation does not support negative scale");
        }
        if (salary.compareTo(scale.firstKey()) < 0){
            throw new CalculationInputException("salary %s below lowest band %s".formatted(salary, scale.firstKey()));
        }
        List<Portion> portions = new ArrayList<>();
        for (var e: scale.entrySet()) {
            var lower = e.getKey();
            var upper = scale.higherKey(lower); // null for the last bracket: no upper cap
            var top = upper == null ? salary : salary.min(upper);
            if (top.compareTo(lower) <= 0){
                break; // salary doesn't reach this bracket
            }
            portions.add(new Portion(top.subtract(lower), e.getValue()));
        }
        return portions;
    }

    public List<Portion> getProcessiveScaleRatesOpt(BigDecimal salary){
        return config.getScale().entrySet().stream()
                .map(e -> {
                    var lower = e.getKey();
                    var upper = config.getScale().higherKey(lower);
                    var top = upper == null ? salary : salary.min(upper);
                    return new Portion(top.subtract(lower), e.getValue());
                })
                .filter(p->p.amount().signum() > 0)
                .toList();
    }
}
