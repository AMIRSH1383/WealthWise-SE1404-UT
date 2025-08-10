package com.bourse.wealthwise.domain.services;
import com.bourse.wealthwise.domain.entity.action.BaseAction;
import com.bourse.wealthwise.domain.entity.portfolio.Portfolio;
import com.bourse.wealthwise.domain.entity.portfolio.PortfolioSecurityInfo;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.domain.entity.security.SecurityChange;
import com.bourse.wealthwise.repository.ActionRepository;
import com.bourse.wealthwise.repository.PortfolioRepository;
import com.bourse.wealthwise.repository.SecurityPriceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class ViewPortfolioSecuritiesService {

    private final PortfolioRepository portfolioRepository;
    private final SecurityPriceRepository securityPriceRepository;
    private final ActionRepository actionRepository;

    public ViewPortfolioSecuritiesService(
            PortfolioRepository portfolioRepository,
            SecurityPriceRepository securityPriceRepository,
            ActionRepository actionRepository
    ) {
        this.portfolioRepository = portfolioRepository;
        this.securityPriceRepository = securityPriceRepository;
        this.actionRepository = actionRepository;
    }
    public List<PortfolioSecurityInfo> getPortfolioSecurities(String portfolioId, LocalDateTime targetDateTime) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));
        List<PortfolioSecurityInfo> portfolioSecurityInfoList = new ArrayList<>();

        List<BaseAction> portfolioActions = actionRepository.findAllActionsOf(portfolioId);
        List<BaseAction> targetDataPortfolioActions = new ArrayList<>();

        for(BaseAction action : portfolioActions){
            if(action.getDatetime().isBefore(targetDateTime)){
                targetDataPortfolioActions.add(action);
            }
        }

        if(targetDataPortfolioActions != null) {
            for (BaseAction validAction : Objects.requireNonNull(targetDataPortfolioActions)) {
                var securityChangeList = validAction.getSecurityChanges();
                if (securityChangeList.size() == 1) {
                    Security security = securityChangeList.get(0).getSecurity();
                    BigInteger volumeChange = securityChangeList.get(0).getVolumeChange();

                    boolean newSecurity = true;
                    for (int i = 0; i < Objects.requireNonNull(portfolioSecurityInfoList).size(); i++) {
                        if (portfolioSecurityInfoList.get(i).getSecurity().equals(security)) {
                            BigInteger volume = portfolioSecurityInfoList.get(i).getVolume().add(volumeChange);
                            portfolioSecurityInfoList.get(i).setVolume(volume);
                            newSecurity = false;
                            break;
                        }
                    }

                    if (newSecurity) {
                        var value = securityPriceRepository.getPrice(security.getIsin(), LocalDate.from(targetDateTime));
                        portfolioSecurityInfoList.add(new PortfolioSecurityInfo(security, volumeChange, 0));
                    }
                }
            }
        }
        return portfolioSecurityInfoList;
    }
}
