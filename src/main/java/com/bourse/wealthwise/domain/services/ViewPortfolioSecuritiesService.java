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

    private void sortPortfolioSecurityInfo(List<PortfolioSecurityInfo> infoList){

    }

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

        // Aggregate all security changes for valid actions.  Some actions (e.g., CapitalRaise, StockRightUsage)
        // may emit multiple SecurityChange events.  We should apply each change individually to the holdings.
        if (targetDataPortfolioActions != null) {
            for (BaseAction validAction : targetDataPortfolioActions) {
                List<SecurityChange> securityChangeList = validAction.getSecurityChanges();
                for (SecurityChange sc : securityChangeList) {
                    Security security = sc.getSecurity();
                    BigInteger volumeChange = sc.getVolumeChange();

                    // Try to find existing PortfolioSecurityInfo for this security and update its volume
                    boolean found = false;
                    for (PortfolioSecurityInfo info : portfolioSecurityInfoList) {
                        if (info.getSecurity().equals(security)) {
                            info.setVolume(info.getVolume().add(volumeChange));
                            found = true;
                            break;
                        }
                    }
                    // If not found, create a new entry with the current price
                    if (!found) {
                        double price = securityPriceRepository.getPrice(security.getIsin(), LocalDate.from(targetDateTime));
                        portfolioSecurityInfoList.add(new PortfolioSecurityInfo(security, volumeChange, price));
                    }
                }
            }
        }


        for (int i = 0; i < portfolioSecurityInfoList.size(); i++){
            var value = securityPriceRepository.getPrice(portfolioSecurityInfoList.get(i).getSecurity().getIsin(),
                    LocalDate.from(targetDateTime)) * portfolioSecurityInfoList.get(i).getVolume().doubleValue();
            portfolioSecurityInfoList.get(i).setValue(value);
        }
        portfolioSecurityInfoList.sort((o1, o2) -> o1.getSecurity().getName().compareTo(o2.getSecurity().getName()));
        portfolioSecurityInfoList.removeIf(info -> info.getVolume().equals(BigInteger.ZERO)); // Remove zero volumes
        return portfolioSecurityInfoList;
    }
}
