package com.quantor.application.ports;

import com.quantor.domain.portfolio.Fill;
import com.quantor.domain.portfolio.PortfolioPosition;
import com.quantor.domain.portfolio.PortfolioSnapshot;

public interface PortfolioPort {
    PortfolioSnapshot getSnapshot() throws Exception;
    PortfolioPosition getPosition(String symbol) throws Exception;

    /** For PAPER/BACKTEST. LIVE can implement as no-op. */
    void applyFill(Fill fill) throws Exception;
}
