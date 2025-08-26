package com.bourse.wealthwise.domain.services;

import com.bourse.wealthwise.domain.entity.account.User;
import com.bourse.wealthwise.domain.entity.action.*;
import com.bourse.wealthwise.domain.entity.portfolio.Portfolio;
import com.bourse.wealthwise.domain.entity.portfolio.PortfolioSecurityInfo;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.domain.entity.security.SecurityType;
import com.bourse.wealthwise.domain.entity.DTOs.CapitalRaiseData;
import com.bourse.wealthwise.repository.ActionRepository;
import com.bourse.wealthwise.repository.PortfolioRepository;
import com.bourse.wealthwise.repository.SecurityPriceRepository;
import com.bourse.wealthwise.repository.SecurityRepository;
import com.bourse.wealthwise.domain.services.CapitalRaiseListenerService;
import com.bourse.wealthwise.domain.services.ViewPortfolioSecuritiesService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class StockRightTradeTest {
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private ActionRepository actionRepository;
    @Autowired private SecurityRepository securityRepository;
    @Autowired private SecurityPriceRepository securityPriceRepository;
    @Autowired private CapitalRaiseListenerService listenerService;
    @Autowired private ViewPortfolioSecuritiesService portfolioSecuritiesService;
    @Autowired private JmsTemplate jmsTemplate;
    private Portfolio portfolio;
    private Security baseSecurity;

    @BeforeEach
    public void setUp() {
        // Clear securities repository to isolate each test.
        securityRepository.clear();

        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .uuid(UUID.randomUUID().toString())
                .build();
        portfolio = new Portfolio(UUID.randomUUID().toString(), user, "rights_portfolio");
        portfolioRepository.save(portfolio);

        // Base security and price
        baseSecurity = Security.builder()
                .name("BaseCo")
                .symbol("BASE1")
                .isin("IR0BASE00001")
                .securityType(SecurityType.STOCK)
                .build();
        securityRepository.addSecurity(baseSecurity);
        securityPriceRepository.addPrice(baseSecurity.getIsin(), LocalDate.now(), 100.0);

        // Fund and buy base shares
        Deposit deposit = Deposit.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .amount(BigInteger.valueOf(10_000))
                .actionType(ActionType.DEPOSIT)
                .build();
        actionRepository.save(deposit);

        Buy buyBase = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .volume(BigInteger.valueOf(6))
                .price(securityPriceRepository.getPrice(baseSecurity.getIsin(), LocalDate.now()))
                .totalValue(BigInteger.valueOf((long) (6 * securityPriceRepository.getPrice(baseSecurity.getIsin(), LocalDate.now()))))
                .security(baseSecurity)
                .actionType(ActionType.BUY)
                .build();
        actionRepository.save(buyBase);
    }
    @Test //1
    public void rightsAllocated_buyAndSellRights_volumeUpdatedCorrectly() {
        // Clear previous capital raise messages
        listenerService.getCapitalRaiseDataList().clear();
        // Send capital raise message
        String msg = "CAPITAL_RAISE BASE1 0.5";
        jmsTemplate.convertAndSend(CapitalRaiseListenerService.IN_QUEUE, msg);

        // Wait for listener
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(listenerService.getCapitalRaiseDataList()).hasSize(1);
        });

        CapitalRaiseData data = listenerService.getCapitalRaiseDataList().getFirst();
        Security rightsSecurity = securityRepository.getSecurityBySymbol("H" + data.getSecuritySymbol());

        // Save capital raise action allocating rights (3 rights)
        CapitalRaise capitalRaise = CapitalRaise.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .HSecurity(rightsSecurity)
                .securityCurrentVolume(BigInteger.valueOf(6))
                .stockRightAmountPerShare(data.getStockRightAmountPerShare())
                .actionType(ActionType.CAPITAL_RAISE)
                .build();
        actionRepository.save(capitalRaise);

        // Buy 4 more rights
        Buy buyRights = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .volume(BigInteger.valueOf(4))
                .price(securityPriceRepository.getPrice(rightsSecurity.getIsin(), LocalDate.now()))
                .totalValue(BigInteger.valueOf((long) (4 * securityPriceRepository.getPrice(rightsSecurity.getIsin(), LocalDate.now()))))
                .security(rightsSecurity)
                .actionType(ActionType.BUY)
                .build();
        actionRepository.save(buyRights);

        // Sell 2 rights
        Sale saleRights = Sale.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .volume(BigInteger.valueOf(2))
                .price(securityPriceRepository.getPrice(rightsSecurity.getIsin(), LocalDate.now()))
                .totalValue(BigInteger.valueOf((long) (2 * securityPriceRepository.getPrice(rightsSecurity.getIsin(), LocalDate.now()))))
                .security(rightsSecurity)
                .actionType(ActionType.SALE)
                .build();
        actionRepository.save(saleRights);

        // Expected volumes
        BigInteger expectedRightsVolume = BigInteger.valueOf(5); // 3 + 4 - 2
        BigInteger expectedBaseVolume   = BigInteger.valueOf(6);

        List<PortfolioSecurityInfo> infos = portfolioSecuritiesService.getPortfolioSecurities(
                portfolio.getUuid(), LocalDateTime.now().plusSeconds(1)
        );
        infos.sort((o1, o2) -> o1.getSecurity().getName().compareTo(o2.getSecurity().getName()));
        assertThat(infos).hasSize(2);

        PortfolioSecurityInfo infoBase   = infos.get(0).getSecurity().equals(baseSecurity) ? infos.get(0) : infos.get(1);
        PortfolioSecurityInfo infoRights = infos.get(0).getSecurity().equals(rightsSecurity) ? infos.get(0) : infos.get(1);

        assertEquals(baseSecurity, infoBase.getSecurity());
        assertEquals(expectedBaseVolume, infoBase.getVolume());
        assertEquals(rightsSecurity, infoRights.getSecurity());
        assertEquals(expectedRightsVolume, infoRights.getVolume());

        // Clean up
        actionRepository.deleteById(capitalRaise.getUuid());
        actionRepository.deleteById(buyRights.getUuid());
        actionRepository.deleteById(saleRights.getUuid());
    }
    @Test //2
    public void rightsAllocation_fractionalRatioTruncates() {
        listenerService.getCapitalRaiseDataList().clear();
        String msg = "CAPITAL_RAISE BASE1 0.3";
        jmsTemplate.convertAndSend(CapitalRaiseListenerService.IN_QUEUE, msg);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(listenerService.getCapitalRaiseDataList()).hasSize(1);
        });

        CapitalRaiseData data = listenerService.getCapitalRaiseDataList().getFirst();
        Security rightsSecurity = securityRepository.getSecurityBySymbol("H" + data.getSecuritySymbol());
        securityPriceRepository.addPrice(rightsSecurity.getIsin(), LocalDate.now(), 50.0);

        CapitalRaise capitalRaise = CapitalRaise.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .HSecurity(rightsSecurity)
                .securityCurrentVolume(BigInteger.valueOf(6))
                .stockRightAmountPerShare(data.getStockRightAmountPerShare())
                .actionType(ActionType.CAPITAL_RAISE)
                .build();
        actionRepository.save(capitalRaise);

        BigInteger expectedRightsVolume = BigInteger.ONE;  // floor(6*0.3) = 1
        BigInteger expectedBaseVolume   = BigInteger.valueOf(6);

        List<PortfolioSecurityInfo> infos = portfolioSecuritiesService.getPortfolioSecurities(
                portfolio.getUuid(), LocalDateTime.now().plusSeconds(1)
        );
        assertThat(infos).hasSize(2);
        PortfolioSecurityInfo infoBase   = infos.get(0).getSecurity().equals(baseSecurity) ? infos.get(0) : infos.get(1);
        PortfolioSecurityInfo infoRights = infos.get(0).getSecurity().equals(rightsSecurity) ? infos.get(0) : infos.get(1);
        assertEquals(expectedBaseVolume, infoBase.getVolume());
        assertEquals(expectedRightsVolume, infoRights.getVolume());

        actionRepository.deleteById(capitalRaise.getUuid());
    }
}
