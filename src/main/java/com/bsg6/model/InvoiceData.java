package com.bsg6.model;

import java.math.BigDecimal;

public record InvoiceData(
        String sellerNip,
        String buyerNip,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal grossAmount
) {}
