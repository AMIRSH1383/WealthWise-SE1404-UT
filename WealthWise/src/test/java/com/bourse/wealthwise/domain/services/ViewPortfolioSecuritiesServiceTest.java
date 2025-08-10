package com.bourse.wealthwise.domain.services;

import com.bourse.wealthwise.domain.entity.account.User;
import com.bourse.wealthwise.domain.entity.action.*;
import com.bourse.wealthwise.domain.entity.portfolio.Portfolio;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.repository.ActionRepository;
import com.bourse.wealthwise.repository.PortfolioRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bourse.wealthwise.repository.SecurityPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;


@SpringBootTest
public class ViewPortfolioSecuritiesServiceTest {
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private ViewPortfolioSecuritiesService portfolioSecuritiesService;
    @Autowired
    private ActionRepository actionRepository;

    @Autowired
    private SecurityPriceRepository securityPriceRepository;

    private Portfolio portfolio;
    private Security security;

    @BeforeEach
    public void setUp() {
        User user = User.builder().build();
        this.security = Security.builder().build();
        this.portfolio = new Portfolio("21e42b92-cef6-453f-9e52-fa76b1d830f6",
                user,
                "portfo");

        portfolioRepository.save(portfolio);

    }

    @Test
    public void noActionsForPortfolio_getSecurities_zeroSecurityReturned(){
        assertEquals(null, portfolioSecuritiesService.getPortfolioSecurities(
                "21e42b92-cef6-453f-9e52-fa76b1d830f6",
                LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    public void newBuyActionEnters_getSecuritiesInfo_NewSecurityInfoAdded(){
        Deposit deposit = Deposit.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .amount(BigInteger.valueOf(1000))
                .actionType(ActionType.DEPOSIT)
                .build();
        actionRepository.save(deposit);

        Buy buy = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .volume(BigInteger.valueOf(100))
                .price(3)
                .totalValue(BigInteger.valueOf(300))
                .security(security)
                .actionType(ActionType.BUY)
                .build();
        actionRepository.save(buy);
        System.out.println(
        portfolioSecuritiesService.getPortfolioSecurities(
                "21e42b92-cef6-453f-9e52-fa76b1d830f6",
                LocalDateTime.now().plusSeconds(1)
        ).getFirst().getValue());
        actionRepository.deleteById(buy.getUuid());
        actionRepository.deleteById(deposit.getUuid());
    }
}