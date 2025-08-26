package com.bourse.wealthwise.domain.services;

import com.bourse.wealthwise.domain.entity.account.User;
import com.bourse.wealthwise.domain.entity.action.*;
import com.bourse.wealthwise.domain.entity.portfolio.Portfolio;
import com.bourse.wealthwise.domain.entity.portfolio.PortfolioSecurityInfo;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.repository.ActionRepository;
import com.bourse.wealthwise.repository.PortfolioRepository;

import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
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
import java.util.ArrayList;
import java.util.List;
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

    @Test //1
    public void noActionsForPortfolio_getSecurities_zeroSecurityReturned(){
        assertThat(portfolioSecuritiesService.getPortfolioSecurities(
                "21e42b92-cef6-453f-9e52-fa76b1d830f6",
                LocalDateTime.now().plusSeconds(1)).isEmpty());
    }
    @Test //2
    public void newBuyActionEnters_getSecuritiesInfo_NewSecurityInfoAdded(){
        List<PortfolioSecurityInfo> expectedPortfolioSecurities = new ArrayList<>();
        
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
        expectedPortfolioSecurities.add(new PortfolioSecurityInfo(security, BigInteger.valueOf(5),
                securityPriceRepository.getPrice(security.getIsin(), LocalDate.now())));

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
        expectedPortfolioSecurities.add(new PortfolioSecurityInfo(security, BigInteger.valueOf(6),
                securityPriceRepository.getPrice(security.getIsin(), LocalDate.now())));

        var actualPortfolioSecurities = portfolioSecuritiesService.getPortfolioSecurities(
                "21e42b92-cef6-453f-9e52-fa76b1d830f6", LocalDateTime.now().plusSeconds(1));

        for(int i = 0; i < actualPortfolioSecurities.size(); i++){
            assertEquals(expectedPortfolioSecurities.get(i).getSecurity(),
                    actualPortfolioSecurities.get(i).getSecurity());
            assertEquals(expectedPortfolioSecurities.get(i).getVolume(),
                    actualPortfolioSecurities.get(i).getVolume());
            assertEquals(
                    expectedPortfolioSecurities.get(i).getPrice() * expectedPortfolioSecurities.get(i).getVolume().doubleValue(),
                    actualPortfolioSecurities.get(i).getValue());
        }
        actionRepository.deleteById(buy.getUuid());
        actionRepository.deleteById(buy2.getUuid());
        actionRepository.deleteById(deposit.getUuid());
    }
    @Test //3
    public void newSaleActionEnters_getSecuritiesInfo_SecurityVolumeDecreases(){
        List<PortfolioSecurityInfo> expectedPortfolioSecurities = new ArrayList<>();

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
        expectedPortfolioSecurities.add(new PortfolioSecurityInfo(security, BigInteger.valueOf(5),
                securityPriceRepository.getPrice(security.getIsin(), LocalDate.now())));

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

        Sale sale = Sale.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .volume(BigInteger.valueOf(3))
                .price(securityPriceRepository.getPrice(security.getIsin(), LocalDate.now()))
                .totalValue(BigInteger.valueOf((long) (3 * securityPriceRepository.getPrice(security.getIsin(), LocalDate.now()))))
                .security(security)
                .actionType(ActionType.SALE)
                .build();
        actionRepository.save(sale);

        expectedPortfolioSecurities.add(new PortfolioSecurityInfo(security, BigInteger.valueOf(6-3),
                securityPriceRepository.getPrice(security.getIsin(), LocalDate.now())));

        var actualPortfolioSecurities = portfolioSecuritiesService.getPortfolioSecurities(
                "21e42b92-cef6-453f-9e52-fa76b1d830f6", LocalDateTime.now().plusSeconds(1));

        for(int i = 0; i < actualPortfolioSecurities.size(); i++){
            assertEquals(expectedPortfolioSecurities.get(i).getSecurity(),
                    actualPortfolioSecurities.get(i).getSecurity());
            assertEquals(expectedPortfolioSecurities.get(i).getVolume(),
                    actualPortfolioSecurities.get(i).getVolume());
            assertEquals(
                    expectedPortfolioSecurities.get(i).getPrice() * expectedPortfolioSecurities.get(i).getVolume().doubleValue(),
                    actualPortfolioSecurities.get(i).getValue());
            System.out.println(String.format("Security: %s \t Volume: %d \t Value: %f" ,
                    actualPortfolioSecurities.get(i).getSecurity().getName(),
                    actualPortfolioSecurities.get(i).getVolume().intValue(),
                    actualPortfolioSecurities.get(i).getValue().floatValue()));
        }
        actionRepository.deleteById(buy.getUuid());
        actionRepository.deleteById(buy2.getUuid());
        actionRepository.deleteById(deposit.getUuid());
    }
    @Test //4
    public void oneSecuritySoldOut_getSecuritiesInfo_oneSecurityLeft(){
        List<PortfolioSecurityInfo> expectedPortfolioSecurities = new ArrayList<>();

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
        expectedPortfolioSecurities.add(new PortfolioSecurityInfo(security, BigInteger.valueOf(5),
                securityPriceRepository.getPrice(security.getIsin(), LocalDate.now())));

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

        Sale sale = Sale.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .volume(BigInteger.valueOf(6))
                .price(securityPriceRepository.getPrice(security.getIsin(), LocalDate.now()))
                .totalValue(BigInteger.valueOf((long) (6 * securityPriceRepository.getPrice(security.getIsin(), LocalDate.now()))))
                .security(security)
                .actionType(ActionType.SALE)
                .build();
        actionRepository.save(sale);

        expectedPortfolioSecurities.add(new PortfolioSecurityInfo(security, BigInteger.valueOf(6-6),
                securityPriceRepository.getPrice(security.getIsin(), LocalDate.now())));

        var actualPortfolioSecurities = portfolioSecuritiesService.getPortfolioSecurities(
                "21e42b92-cef6-453f-9e52-fa76b1d830f6", LocalDateTime.now().plusSeconds(1));

        for(int i = 0; i < actualPortfolioSecurities.size(); i++){
            assertEquals(expectedPortfolioSecurities.get(i).getSecurity(),
                    actualPortfolioSecurities.get(i).getSecurity());
            assertEquals(expectedPortfolioSecurities.get(i).getVolume(),
                    actualPortfolioSecurities.get(i).getVolume());
            assertEquals(
                    expectedPortfolioSecurities.get(i).getPrice() * expectedPortfolioSecurities.get(i).getVolume().doubleValue(),
                    actualPortfolioSecurities.get(i).getValue());
            System.out.println(String.format("Security: %s \t Volume: %d \t Value: %f" ,
                    actualPortfolioSecurities.get(i).getSecurity().getName(),
                    actualPortfolioSecurities.get(i).getVolume().intValue(),
                    actualPortfolioSecurities.get(i).getValue().floatValue()));
        }
        actionRepository.deleteById(buy.getUuid());
        actionRepository.deleteById(buy2.getUuid());
        actionRepository.deleteById(deposit.getUuid());
        //check
    }
}