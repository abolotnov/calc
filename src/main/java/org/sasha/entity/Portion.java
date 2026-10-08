package org.sasha.entity;

import java.math.BigDecimal;

public record Portion(BigDecimal amount, BigDecimal taxRate){}
