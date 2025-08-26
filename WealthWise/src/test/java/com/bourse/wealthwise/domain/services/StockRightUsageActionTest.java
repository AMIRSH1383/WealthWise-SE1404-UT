package com.bourse.wealthwise.domain.services;

import com.bourse.wealthwise.domain.entity.DTOs.CapitalRaiseData;
import com.bourse.wealthwise.domain.entity.account.User;
import com.bourse.wealthwise.domain.entity.action.*;
import com.bourse.wealthwise.domain.entity.portfolio.Portfolio;
import com.bourse.wealthwise.domain.entity.portfolio.PortfolioSecurityInfo;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.domain.entity.security.SecurityType;
import com.bourse.wealthwise.domain.services.CapitalRaiseListenerService;
import com.bourse.wealthwise.domain.services.ViewPortfolioSecuritiesService;
import com.bourse.wealthwise.domain.services.BalanceActionService;
import com.bourse.wealthwise.repository.ActionRepository;
import com.bourse.wealthwise.repository.PortfolioRepository;
import com.bourse.wealthwise.repository.SecurityPriceRepository;
import com.bourse.wealthwise.repository.SecurityRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class StockRightUsageActionTest {

    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private ActionRepository actionRepository;
    @Autowired private SecurityRepository securityRepository;
    @Autowired private SecurityPriceRepository securityPriceRepository;
    @Autowired private CapitalRaiseListenerService listenerService;
    @Autowired private ViewPortfolioSecuritiesService portfolioSecuritiesService;
    @Autowired private BalanceActionService balanceActionService;
    @Autowired private JmsTemplate jmsTemplate;

    private Portfolio portfolio;
    private Security baseSecurity;

    @BeforeEach
    public void setUp() {
        securityRepository.clear();
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .uuid(UUID.randomUUID().toString())
                .build();
        portfolio = new Portfolio(UUID.randomUUID().toString(), user, "usage_portfolio");
        portfolioRepository.save(portfolio);

        baseSecurity = Security.builder()
                .name("BaseCo")
                .symbol("BASE2")
                .isin("IR0BASE00002")
                .securityType(SecurityType.STOCK)
                .build();
        securityRepository.addSecurity(baseSecurity);
        securityPriceRepository.addPrice(baseSecurity.getIsin(), LocalDate.now(), 100.0);

        Deposit deposit = Deposit.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .amount(BigInteger.valueOf(1_000))
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

    @Test
    public void rightsUsage_convertsRightsToSharesAndDeductsBalance() {
        listenerService.getCapitalRaiseDataList().clear();
        String msg = "CAPITAL_RAISE BASE2 0.5";
        jmsTemplate.convertAndSend(CapitalRaiseListenerService.IN_QUEUE, msg);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(listenerService.getCapitalRaiseDataList()).hasSize(1);
        });

        CapitalRaiseData data = listenerService.getCapitalRaiseDataList().getFirst();
        Security rightsSecurity = securityRepository.getSecurityBySymbol("H" + data.getSecuritySymbol());

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

        BigInteger rightsToUse = BigInteger.valueOf((long) (6 * data.getStockRightAmountPerShare()));
        StockRightUsage usage = StockRightUsage.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .stockRightSecurity(rightsSecurity)
                .mainSecurity(baseSecurity)
                .volume(rightsToUse)
                .pricePerRight(BigInteger.valueOf(100))
                .actionType(ActionType.STOCK_RIGHT_USAGE)
                .build();
        actionRepository.save(usage);

        List<PortfolioSecurityInfo> infos = portfolioSecuritiesService.getPortfolioSecurities(
                portfolio.getUuid(), LocalDateTime.now().plusSeconds(1)
        );
        assertEquals(1, infos.size());
        PortfolioSecurityInfo info = infos.get(0);
        assertEquals(baseSecurity, info.getSecurity());
        assertEquals(BigInteger.valueOf(9), info.getVolume());

        BigInteger expectedBalance = BigInteger.valueOf(1_000)
                .subtract(BigInteger.valueOf(6 * 100))
                .subtract(BigInteger.valueOf(3 * 100));
        BigInteger actualBalance = balanceActionService.getBalanceForPortfolio(
                portfolio.getUuid(), LocalDateTime.now().plusSeconds(1)
        );
        assertEquals(expectedBalance, actualBalance);
        assertTrue(infos.stream().noneMatch(i -> i.getSecurity().equals(rightsSecurity)));

        actionRepository.deleteById(capitalRaise.getUuid());
        actionRepository.deleteById(usage.getUuid());
    }

    @Test
    public void partialRightsUsage_leavesRemainingRights() {
        listenerService.getCapitalRaiseDataList().clear();
        String msg = "CAPITAL_RAISE BASE2 0.5";
        jmsTemplate.convertAndSend(CapitalRaiseListenerService.IN_QUEUE, msg);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(listenerService.getCapitalRaiseDataList()).hasSize(1);
        });

        CapitalRaiseData data = listenerService.getCapitalRaiseDataList().getFirst();
        Security rightsSecurity = securityRepository.getSecurityBySymbol("H" + data.getSecuritySymbol());
        securityPriceRepository.addPrice(rightsSecurity.getIsin(), LocalDate.now(), 100.0);

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

        BigInteger rightsToUse = BigInteger.valueOf(2);
        StockRightUsage usage = StockRightUsage.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .stockRightSecurity(rightsSecurity)
                .mainSecurity(baseSecurity)
                .volume(rightsToUse)
                .pricePerRight(BigInteger.valueOf(100))
                .actionType(ActionType.STOCK_RIGHT_USAGE)
                .build();
        actionRepository.save(usage);

        BigInteger expectedBaseVolume   = BigInteger.valueOf(8);
        BigInteger expectedRightsVolume = BigInteger.ONE;
        List<PortfolioSecurityInfo> infos = portfolioSecuritiesService.getPortfolioSecurities(
                portfolio.getUuid(), LocalDateTime.now().plusSeconds(1)
        );
        assertThat(infos).hasSize(2);
        PortfolioSecurityInfo infoBase   = infos.get(0).getSecurity().equals(baseSecurity) ? infos.get(0) : infos.get(1);
        PortfolioSecurityInfo infoRights = infos.get(0).getSecurity().equals(rightsSecurity) ? infos.get(0) : infos.get(1);
        assertEquals(expectedBaseVolume, infoBase.getVolume());
        assertEquals(expectedRightsVolume, infoRights.getVolume());

        BigInteger expectedBalance = BigInteger.valueOf(1_000)
                .subtract(BigInteger.valueOf(6 * 100))
                .subtract(BigInteger.valueOf(2 * 100));
        BigInteger actualBalance = balanceActionService.getBalanceForPortfolio(
                portfolio.getUuid(), LocalDateTime.now().plusSeconds(1)
        );
        assertEquals(expectedBalance, actualBalance);

        actionRepository.deleteById(capitalRaise.getUuid());
        actionRepository.deleteById(usage.getUuid());
    }

    @Test
    public void rightsUsage_withAdditionalPurchasedRights_correctVolumesAndBalance() {
        listenerService.getCapitalRaiseDataList().clear();
        Deposit extraDeposit = Deposit.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .amount(BigInteger.valueOf(1_000))
                .actionType(ActionType.DEPOSIT)
                .build();
        actionRepository.save(extraDeposit);

        String msg = "CAPITAL_RAISE BASE2 0.5";
        jmsTemplate.convertAndSend(CapitalRaiseListenerService.IN_QUEUE, msg);
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(listenerService.getCapitalRaiseDataList()).hasSize(1);
        });

        CapitalRaiseData data = listenerService.getCapitalRaiseDataList().getFirst();
        Security rightsSecurity = securityRepository.getSecurityBySymbol("H" + data.getSecuritySymbol());
        securityPriceRepository.addPrice(rightsSecurity.getIsin(), LocalDate.now(), 100.0);

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

        StockRightUsage usage = StockRightUsage.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .stockRightSecurity(rightsSecurity)
                .mainSecurity(baseSecurity)
                .volume(BigInteger.valueOf(5))
                .pricePerRight(BigInteger.valueOf(100))
                .actionType(ActionType.STOCK_RIGHT_USAGE)
                .build();
        actionRepository.save(usage);

        List<PortfolioSecurityInfo> infos = portfolioSecuritiesService.getPortfolioSecurities(
                portfolio.getUuid(), LocalDateTime.now().plusSeconds(1)
        );
        assertThat(infos).hasSize(2);
        PortfolioSecurityInfo infoBase   = infos.get(0).getSecurity().equals(baseSecurity) ? infos.get(0) : infos.get(1);
        PortfolioSecurityInfo infoRights = infos.get(0).getSecurity().equals(rightsSecurity) ? infos.get(0) : infos.get(1);
        assertEquals(BigInteger.valueOf(11), infoBase.getVolume());
        assertEquals(BigInteger.valueOf(2), infoRights.getVolume());

        BigInteger expectedBalance = BigInteger.valueOf(2_000)
                .subtract(BigInteger.valueOf(6 * 100))
                .subtract(BigInteger.valueOf(4 * 100))
                .subtract(BigInteger.valueOf(5 * 100));
        BigInteger actualBalance = balanceActionService.getBalanceForPortfolio(
                portfolio.getUuid(), LocalDateTime.now().plusSeconds(1)
        );
        assertEquals(expectedBalance, actualBalance);

        actionRepository.deleteById(extraDeposit.getUuid());
        actionRepository.deleteById(capitalRaise.getUuid());
        actionRepository.deleteById(buyRights.getUuid());
        actionRepository.deleteById(usage.getUuid());
    }

    @Test
    public void zeroRightsUsage_noEffectOnPortfolio() {
        listenerService.getCapitalRaiseDataList().clear();
        String msg = "CAPITAL_RAISE BASE2 0.5";
        jmsTemplate.convertAndSend(CapitalRaiseListenerService.IN_QUEUE, msg);
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(listenerService.getCapitalRaiseDataList()).hasSize(1);
        });
        CapitalRaiseData data = listenerService.getCapitalRaiseDataList().getFirst();
        Security rightsSecurity = securityRepository.getSecurityBySymbol("H" + data.getSecuritySymbol());

        securityPriceRepository.addPrice(rightsSecurity.getIsin(), LocalDate.now(), 100.0);

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

        StockRightUsage usage = StockRightUsage.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .stockRightSecurity(rightsSecurity)
                .mainSecurity(baseSecurity)
                .volume(BigInteger.ZERO)
                .pricePerRight(BigInteger.valueOf(100))
                .actionType(ActionType.STOCK_RIGHT_USAGE)
                .build();
        actionRepository.save(usage);

        List<PortfolioSecurityInfo> infos = portfolioSecuritiesService.getPortfolioSecurities(
                portfolio.getUuid(), LocalDateTime.now().plusSeconds(1)
        );
        assertThat(infos).hasSize(2);
        PortfolioSecurityInfo infoBase   = infos.get(0).getSecurity().equals(baseSecurity) ? infos.get(0) : infos.get(1);
        PortfolioSecurityInfo infoRights = infos.get(0).getSecurity().equals(rightsSecurity) ? infos.get(0) : infos.get(1);
        assertEquals(BigInteger.valueOf(6), infoBase.getVolume());
        assertEquals(BigInteger.valueOf(3), infoRights.getVolume());

        BigInteger expectedBalance = BigInteger.valueOf(1_000)
                .subtract(BigInteger.valueOf(6 * 100));
        BigInteger actualBalance = balanceActionService.getBalanceForPortfolio(
                portfolio.getUuid(), LocalDateTime.now().plusSeconds(1)
        );
        assertEquals(expectedBalance, actualBalance);

        actionRepository.deleteById(capitalRaise.getUuid());
        actionRepository.deleteById(usage.getUuid());
    }
}
