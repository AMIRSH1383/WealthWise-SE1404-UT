package com.bourse.wealthwise.domain.services;

import com.bourse.wealthwise.domain.entity.DTOs.CapitalRaiseData;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.domain.entity.security.SecurityType;
import com.bourse.wealthwise.repository.SecurityPriceRepository;
import com.bourse.wealthwise.repository.SecurityRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Getter
public class CapitalRaiseListenerService {
    @Autowired
    private SecurityRepository securityRepository;
    @Autowired
    private SecurityPriceRepository securityPriceRepository;

    private List<CapitalRaiseData> capitalRaiseDataList = new ArrayList<>();
    public static final String IN_QUEUE = "CapitalRaiseQueue";


    // Format: CAPITAL_RAISE <symbol> <decimal>
    private static final Pattern MSG = Pattern.compile(
            "^\\s*CAPITAL_RAISE\\s+(\\S+)\\s+([0-9]+(?:\\.[0-9]+)?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    @JmsListener(destination = IN_QUEUE)
    public void onMessage(String message) {
        System.out.println("[CapitalRaiseListener] received: " + message);

        String normalized = normalize(message);
        Matcher m = MSG.matcher(normalized);
        if (!m.matches()) {
            System.err.println("[CapitalRaiseListener] bad format. expected: 'CAPITAL_RAISE <SYMBOL> <DECIMAL>' | got: " + normalized);
            return;
        }

        String symbol = m.group(1);
        double rightsPerShare = Double.parseDouble(m.group(2));
        LocalDateTime effectiveAt = LocalDateTime.now();

        try {
            allocateRightsStub(symbol, rightsPerShare, effectiveAt);
            System.out.printf("[CapitalRaiseListener] OK symbol=%s ratio=%s at=%s%n",
                    symbol, rightsPerShare, effectiveAt);
        } catch (Exception ex) {
            System.err.println("[CapitalRaiseListener] ERROR: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private String normalize(String msg) {
        if (msg == null) return "";
        String s = msg.trim();

        // JSON
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1);
        }

        // XML
        if (s.startsWith("<") && s.endsWith(">")) {
            // <CAPITAL_RAISE>Foolad 0.5</CAPITAL_RAISE>
            String inner = s.replaceAll("<[^>]+>", " ").trim();
            if (!inner.toUpperCase().startsWith("CAPITAL_RAISE")) {
                s = "CAPITAL_RAISE " + inner;
            } else {
                s = inner;
            }
        }
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private void allocateRightsStub(String symbol, double ratio, LocalDateTime at) {
        capitalRaiseDataList.add(
                new CapitalRaiseData(symbol, ratio)
        );
        Security stockSecurity = securityRepository.getSecurityBySymbol(symbol);
        Security stockRightSecurity = Security.builder()
                .name("H" + stockSecurity.getName())
                .symbol("H" + symbol)
                .isin("H" + stockSecurity.getIsin())
                .securityType(SecurityType.STOCK_RIGHT)
                .build();
        securityRepository.addSecurity(stockRightSecurity);
        securityPriceRepository.addPrice(stockRightSecurity.getIsin(), LocalDate.from(LocalDateTime.now()), 100.0);
        System.out.printf("New capital raise for: %s, with ratio of %f!\n", symbol, ratio);
        System.out.println(capitalRaiseDataList.size());
    }
}
