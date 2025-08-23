package com.bourse.wealthwise.domain.entity.DTOs;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CapitalRaiseData {
    // Getters and setters
    private String securitySymbol;
    private double stockRightAmountPerShare;
    public CapitalRaiseData(String securitySymbol, double stockRightAmountPerShare){
        this.securitySymbol = securitySymbol;
        this.stockRightAmountPerShare = stockRightAmountPerShare;
    }
}
