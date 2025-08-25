package com.bourse.wealthwise.domain.entity.action;

public enum ActionType {
    BUY,
    SALE,
    DEPOSIT,
    WITHDRAWAL,
    CAPITAL_RAISE,

    /**
     * Represents an action where a portfolio holder exercises their stock rights
     * by paying a fixed amount per right and converting those rights into
     * regular shares. This event deducts cash from the portfolio, removes the
     * corresponding number of stock‑right units and issues an equal number of
     * underlying shares. See {@link com.bourse.wealthwise.domain.entity.action.StockRightUsage}
     * for the implementation.
     */
    STOCK_RIGHT_USAGE,

}
