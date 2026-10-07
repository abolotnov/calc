package org.sasha;

import org.junit.jupiter.api.Test;
import org.sasha.exception.CalculationInputException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class TaxBandTest {
    private final TaxScaleCalc scale = new TaxScaleCalc(TaxScaleConfig.getDefaultConfig());

    @Test
    void getTaxRate() {
        assertEquals(new BigDecimal("1.89"), scale.getBand(BigDecimal.valueOf(0L)).getValue());
        assertEquals(new BigDecimal("1.89"), scale.getBand(BigDecimal.valueOf(9_999L)).getValue());
        assertEquals(new BigDecimal("2.5"), scale.getBand(BigDecimal.valueOf(10_000L)).getValue());
        assertEquals(new BigDecimal("10.5"), scale.getBand(BigDecimal.valueOf(Long.MAX_VALUE)).getValue());
    }

    @Test
    void negativeSalaryRejected() {
        assertThrows(CalculationInputException.class, () -> scale.getEffectiveTaxAmount(BigDecimal.valueOf(-1L)));
    }

    @Test
    void effectiveTaxHasScale2() {
        assertEquals(new BigDecimal("94.50"), scale.getEffectiveTaxAmount(BigDecimal.valueOf(5_000L)));
    }


    @Test
    void negativeSalaryAllowedWhenBandsCoverIt() {
        var calc = new TaxScaleCalc(new TaxScaleConfig(new TreeMap<>(Map.of(
                BigDecimal.valueOf(-10_000L), new BigDecimal("1"),
                BigDecimal.ZERO, new BigDecimal("2")))));
        assertEquals(new BigDecimal("-10.00"), calc.getEffectiveTaxAmount(BigDecimal.valueOf(-1_000L)));
    }

    @Test
    void progressiveScale(){
        var expected = new ArrayList<Portion>(List.of(
                new Portion(BigDecimal.valueOf(10_000), BigDecimal.valueOf(1.89)),
                new Portion(BigDecimal.valueOf(2_256.56), BigDecimal.valueOf(2.5))
        ));
        assertEquals(
                expected,
                new TaxScaleCalc(TaxScaleConfig.getDefaultConfig()).getProgressiveScaleRates(BigDecimal.valueOf(12_256.56))
        );
    }

    @Test
    void processiveEffectiveRate(){
        var expected = new BigDecimal("239.00");
        assertEquals(expected, new TaxScaleCalc(TaxScaleConfig.getDefaultConfig()).getEffectiveProgressiveTaxAmount(BigDecimal.valueOf(12_000)));
    }
}
