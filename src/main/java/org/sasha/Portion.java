package org.sasha;

import java.math.BigDecimal;

public record Portion(BigDecimal amount, BigDecimal taxRate){}
