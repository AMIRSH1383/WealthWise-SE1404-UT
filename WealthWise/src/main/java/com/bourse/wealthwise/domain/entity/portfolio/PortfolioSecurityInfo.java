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
    private double value;
    public PortfolioSecurityInfo(Security security, BigInteger volume, double value){
        this.security = security;
        this.volume = volume;
        this.value = value;
    }


}
