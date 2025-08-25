package com.bourse.wealthwise.domain.entity.portfolio;

import com.bourse.wealthwise.domain.entity.security.Security;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
@Getter
@Setter
public class PortfolioSecurityInfo {
    // Getters and setters
    private Security security;
    private BigInteger volume;
    private Double price;
    private Double value;
    public PortfolioSecurityInfo(Security security, BigInteger volume, Double price){
        this.security = security;
        this.volume = volume;
        this.price = price;
    }
}
