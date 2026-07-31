package com.trading.risk.domain;

public enum RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL;

    public boolean isHigherThan(RiskLevel other) {
        return this.ordinal() > other.ordinal();
    }
}
