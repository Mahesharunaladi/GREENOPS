package com.greenops.scrapper.model;

public record SavingsEstimate(String currency, double hourlyRate, double estimatedMonthlySavings) {
    public static SavingsEstimate zero() {
        return new SavingsEstimate("USD", 0.0, 0.0);
    }
}
