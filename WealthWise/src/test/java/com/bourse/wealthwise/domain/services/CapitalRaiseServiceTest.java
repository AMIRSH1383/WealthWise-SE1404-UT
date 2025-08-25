package com.bourse.wealthwise.domain.services;

import com.bourse.wealthwise.domain.entity.DTOs.CapitalRaiseData;
import com.bourse.wealthwise.domain.entity.action.ActionType;
import com.bourse.wealthwise.domain.entity.action.Buy;
import com.bourse.wealthwise.domain.entity.action.CapitalRaise;
import com.bourse.wealthwise.domain.entity.action.Deposit;
import com.bourse.wealthwise.domain.entity.portfolio.PortfolioSecurityInfo;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;
import com.bourse.wealthwise.domain.entity.account.User;
import com.bourse.wealthwise.domain.entity.portfolio.Portfolio;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.repository.ActionRepository;
import com.bourse.wealthwise.repository.PortfolioRepository;
import com.bourse.wealthwise.repository.SecurityPriceRepository;
import com.bourse.wealthwise.repository.SecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class CapitalRaiseServiceTest {
    @Autowired
    private JmsTemplate jmsTemplate;
    @Autowired
    private CapitalRaiseListenerService listenerService;
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
    void capitalRaiseMessageCome_getCapitalRaiseData_CapitalRaiseDataReadCorrectly() {
        String msg = "CAPITAL_RAISE IKCQ1 0.5";
        jmsTemplate.convertAndSend(CapitalRaiseListenerService.IN_QUEUE, msg);

        // Assert (no Thread.sleep; wait up to 5s for the async consumer to finish)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(listenerService.getCapitalRaiseDataList()).hasSize(1);
            assertThat(listenerService.getCapitalRaiseDataList().get(0).getSecuritySymbol())
                    .isEqualTo("IKCQ1");
            assertThat(listenerService.getCapitalRaiseDataList().get(0).getStockRightAmountPerShare())
                    .isEqualTo(0.5);
        });
    }
    @Test //2
    public void capitalRaiseForBoughtStockEnters_getSecuritiesInfo_NewStockRightSecurityAdded(){
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

        // Artemis send message
        String msg = "CAPITAL_RAISE IKCQ1 0.5";
        jmsTemplate.convertAndSend(CapitalRaiseListenerService.IN_QUEUE, msg);
        await().atLeast(4, TimeUnit.SECONDS);

        CapitalRaiseData capitalRaiseData = listenerService.getCapitalRaiseDataList().getFirst();
        Security stockRightSecurity = securityRepository.getSecurityBySymbol("H" + capitalRaiseData.getSecuritySymbol());
        CapitalRaise capitalRaise = CapitalRaise.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .HSecurity(stockRightSecurity)
                .securityCurrentVolume(BigInteger.valueOf(5))
                .stockRightAmountPerShare(capitalRaiseData.getStockRightAmountPerShare())
                .actionType(ActionType.CAPITAL_RAISE)
                .build();
        actionRepository.save(capitalRaise);

        var stockRightVolume = capitalRaise.getStockRightAmountPerShare() * 5.0;
        expectedPortfolioSecurities.add(new PortfolioSecurityInfo(stockRightSecurity,
                BigInteger.valueOf((long) stockRightVolume),
                securityPriceRepository.getPrice(stockRightSecurity.getIsin(), LocalDate.now())));

        var actualPortfolioSecurities = portfolioSecuritiesService.getPortfolioSecurities(
                "21e42b92-cef6-453f-9e52-fa76b1d830f6", LocalDateTime.now().plusSeconds(1));

        expectedPortfolioSecurities.sort((o1, o2) -> o1.getSecurity().getName().compareTo(o2.getSecurity().getName()));
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
        actionRepository.deleteById(capitalRaise.getUuid());
    }
    @Test //3
    public void capitalRaiseForNotPurchasedStockEnters_getSecuritiesInfo_NoNewStockRightSecurityAdded(){
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

        // Artemis send message
        String msg = "CAPITAL_RAISE BMLT1 0.7";
        jmsTemplate.convertAndSend(CapitalRaiseListenerService.IN_QUEUE, msg);
        await().atLeast(5, TimeUnit.SECONDS);

        CapitalRaiseData capitalRaiseData = listenerService.getCapitalRaiseDataList().getFirst();
        Security stockRightSecurity = securityRepository.getSecurityBySymbol("H" + capitalRaiseData.getSecuritySymbol());
        CapitalRaise capitalRaise = CapitalRaise.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .HSecurity(stockRightSecurity)
                .securityCurrentVolume(BigInteger.valueOf(0))
                .stockRightAmountPerShare(capitalRaiseData.getStockRightAmountPerShare())
                .actionType(ActionType.CAPITAL_RAISE)
                .build();
        actionRepository.save(capitalRaise);

        var actualPortfolioSecurities = portfolioSecuritiesService.getPortfolioSecurities(
                "21e42b92-cef6-453f-9e52-fa76b1d830f6", LocalDateTime.now().plusSeconds(1));

        expectedPortfolioSecurities.sort((o1, o2) -> o1.getSecurity().getName().compareTo(o2.getSecurity().getName()));
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
        actionRepository.deleteById(deposit.getUuid());
        actionRepository.deleteById(capitalRaise.getUuid());
    }
}
