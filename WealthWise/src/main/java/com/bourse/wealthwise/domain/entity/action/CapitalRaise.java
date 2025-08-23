package com.bourse.wealthwise.domain.entity.action;

import com.bourse.wealthwise.domain.entity.action.utils.ActionVisitor;
import com.bourse.wealthwise.domain.entity.balance.BalanceChange;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.domain.entity.security.SecurityChange;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

@SuperBuilder
@Getter
public class CapitalRaise extends BaseAction {

    private Security HSecurity;
    private BigInteger securityCurrentVolume;
    private double stockRightAmountPerShare;
    @Override
    public List<BalanceChange> getBalanceChanges() {
        return List.of(
                BalanceChange.builder()
                        .uuid(UUID.randomUUID())
                        .portfolio(this.portfolio)
                        .datetime(this.datetime)
                        .change_amount(BigInteger.valueOf(0)) // Deduct total cost
                        .action(this)
                        .build()
        );
    }

    @Override
    public List<SecurityChange> getSecurityChanges() {
        if(securityCurrentVolume.equals(BigInteger.valueOf(0))){
            return List.of();
        }

        var volumeChange = securityCurrentVolume.doubleValue() * stockRightAmountPerShare;
        return List.of(
                SecurityChange.builder()
                        .uuid(UUID.randomUUID())
                        .portfolio(this.portfolio)
                        .datetime(this.datetime)
                        .security(this.HSecurity)
                        .volumeChange(BigInteger.valueOf((long) volumeChange))
                        .action(this)
                        .build()
        );
    }

    @Override
    public String accept(ActionVisitor visitor) {
        return visitor.visit(this);
    }

    private void setActionType() {
        this.actionType = ActionType.CAPITAL_RAISE;
    }
}
