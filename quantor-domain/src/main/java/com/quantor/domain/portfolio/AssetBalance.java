package com.quantor.domain.portfolio;

import java.math.BigDecimal;

public class AssetBalance {
    private final String asset;
    private BigDecimal free;
    private BigDecimal locked;

    public AssetBalance(String asset, BigDecimal free, BigDecimal locked) {
        this.asset = asset;
        this.free = free;
        this.locked = locked;
    }

    public String getAsset() { return asset; }
    public BigDecimal getFree() { return free; }
    public BigDecimal getLocked() { return locked; }

    public void setFree(BigDecimal free) { this.free = free; }
    public void setLocked(BigDecimal locked) { this.locked = locked; }
}
