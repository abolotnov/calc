package org.sasha.entity;

import org.sasha.util.ConfigReader;

import java.math.BigDecimal;
import java.util.NavigableMap;

/**
 * Tax scale configuration
 */
public class TaxScaleConfig {
    private final NavigableMap<BigDecimal, BigDecimal> scale;

    /**
     * Constructor with tax bands (floor-based lookups in keys (range) for corresponding tax % amount)
     * @param scale NavigableMap with bands
     */
    public TaxScaleConfig(NavigableMap<BigDecimal, BigDecimal> scale){
        this.scale = scale;
    }

    /**
     * Constructs the instance with default tax bands
     * @return TaxScaleConfig with the bands from the bundled default_tax_bands.txt
     */
    public static TaxScaleConfig getDefaultConfig(){
        return ConfigReader.fromFlatResource("/default_tax_bands.txt");
    }

    /**
     * Getter for tax bands config
     * @return NavigableMap with tax bands
     */
    public NavigableMap<BigDecimal, BigDecimal> getScale(){
        return scale;
    }
}
