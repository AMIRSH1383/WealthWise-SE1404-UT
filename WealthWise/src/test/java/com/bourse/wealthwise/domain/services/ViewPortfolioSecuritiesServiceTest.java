package com.bourse.wealthwise.domain.services;

import com.bourse.wealthwise.domain.entity.account.User;
import com.bourse.wealthwise.domain.entity.action.*;
import com.bourse.wealthwise.domain.entity.portfolio.Portfolio;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.repository.ActionRepository;
import com.bourse.wealthwise.repository.PortfolioRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bourse.wealthwise.repository.SecurityPriceRepository;
import com.bourse.wealthwise.repository.SecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;


@SpringBootTest
public class ViewPortfolioSecuritiesServiceTest {
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private ActionRepository actionRepository;
    @Autowired
    private SecurityRepository securityRepository;
    @Autowired
    private SecurityPriceRepository securityPriceRepository;
    @Autowired
    private ViewPortfolioSecuritiesService portfolioSecuritiesService;
    private Portfolio portfolio;
    private Security security;

    private void addNewSecurity(String name, String symbol, String isin, Double initial_price){
        this.security = Security.builder()
                .name(name)
                .symbol(symbol)
                .isin(isin)
                .build();
        securityRepository.addSecurity(security);
        securityPriceRepository.addPrice(security.getIsin(), LocalDate.from(LocalDateTime.now()), initial_price);
    }

    @BeforeEach
    public void setUp() {
        User user = User.builder()
                .firstName("Amirarsalan")
                .lastName("Shahbazi")
                .uuid(UUID.randomUUID().toString())
                .build();

        addNewSecurity("Iran Khodro-D", "IKCQ1", "IRB5IKCO8751", 100.0);
        addNewSecurity("S*Mellat Bank", "BMLT1", "IRO1BMLT0001", 50.0);

        this.portfolio = new Portfolio("21e42b92-cef6-453f-9e52-fa76b1d830f6",
                user,
                "portfo1");

        portfolioRepository.save(portfolio);
    }

    @Test
    public void noActionsForPortfolio_getSecurities_zeroSecurityReturned(){
        assertThat(portfolioSecuritiesService.getPortfolioSecurities(
                "21e42b92-cef6-453f-9e52-fa76b1d830f6",
                LocalDateTime.now().plusSeconds(1)).isEmpty());
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

        this.security = securityRepository.findSecurityByIsin("IRB5IKCO8751");
        Buy buy = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .volume(BigInteger.valueOf(5))
                .price(securityPriceRepository.getPrice(security.getIsin(), LocalDate.now()))
                .totalValue(BigInteger.valueOf((long) (5 * securityPriceRepository.getPrice(security.getIsin(), LocalDate.now()))))
                .security(security)
                .actionType(ActionType.BUY)
                .build();
        actionRepository.save(buy);

        this.security = securityRepository.findSecurityByIsin("IRO1BMLT0001");
        Buy buy2 = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .volume(BigInteger.valueOf(6))
                .price(securityPriceRepository.getPrice(security.getIsin(), LocalDate.now()))
                .totalValue(BigInteger.valueOf((long) (6 * securityPriceRepository.getPrice(security.getIsin(), LocalDate.now()))))
                .security(security)
                .actionType(ActionType.BUY)
                .build();
        actionRepository.save(buy2);

        var portfolioSecurities = portfolioSecuritiesService.getPortfolioSecurities(
                "21e42b92-cef6-453f-9e52-fa76b1d830f6", LocalDateTime.now().plusSeconds(1));

        for(var securityInfo: portfolioSecurities){
            System.out.println(securityInfo.getSecurity());
            System.out.println(securityInfo.getVolume());
            System.out.println(securityInfo.getPrice() * securityInfo.getVolume().doubleValue());
        }

        actionRepository.deleteById(buy.getUuid());
        actionRepository.deleteById(buy2.getUuid());
        actionRepository.deleteById(deposit.getUuid());
    }
}