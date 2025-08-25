package com.bourse.wealthwise.domain.entity.action.utils;


import com.bourse.wealthwise.domain.entity.action.*;

public interface ActionVisitor {
    String visit(Buy buy);
    String visit(Sale sale);
    String visit(Deposit deposit);
    String visit(Withdrawal withdrawal);

    String visit(CapitalRaise capitalRaise);

    /**
     * Visit a StockRightUsage action.  Visitors can override this to provide
     * meaningful descriptions or behaviour for exercising stock rights.
     *
     * @param stockRightUsage the stock right usage action to visit
     * @return a string describing the action
     */
    default String visit(com.bourse.wealthwise.domain.entity.action.StockRightUsage stockRightUsage) {
        return String.format("Stock right usage: exercised %s rights of %s into %s",
                stockRightUsage.getVolume(),
                stockRightUsage.getStockRightSecurity().getSymbol(),
                stockRightUsage.getMainSecurity().getSymbol());
    }
}