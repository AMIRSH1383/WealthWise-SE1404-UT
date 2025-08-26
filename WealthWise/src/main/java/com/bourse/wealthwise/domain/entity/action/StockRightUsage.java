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

/**
 * Represents the use of stock rights by a portfolio holder.  When a user decides to
 * exercise their rights, each right is converted into a share of the underlying
 * stock at a fixed price.  This action will deduct the required cash from the
 * portfolio balance and adjust the holdings accordingly: the volume of the
 * stock‑right security decreases and the volume of the underlying stock increases.
 * Both resulting securities are marked as non‑tradable until the capital raise is
 * finalized.
 */
@SuperBuilder
@Getter
public class StockRightUsage extends BaseAction {
    /** The security representing the stock right being exercised. */
    private final Security stockRightSecurity;
    /** The underlying common stock security that rights will convert into. */
    private final Security mainSecurity;
    /** Number of rights being exercised. */
    private final BigInteger volume;
    /** Fixed price to pay per right.  This should be a positive value (e.g. 100). */
    private final BigInteger pricePerRight;

    @Override
    public List<BalanceChange> getBalanceChanges() {
        // Calculate total payment and record it as a negative balance change
        BigInteger totalPayment = pricePerRight.multiply(volume);
        return List.of(
                BalanceChange.builder()
                        .uuid(UUID.randomUUID())
                        .portfolio(this.portfolio)
                        .datetime(this.datetime)
                        .change_amount(totalPayment.negate())
                        .action(this)
                        .build()
        );
    }

    @Override
    public List<SecurityChange> getSecurityChanges() {
        // Two events: remove rights and add underlying shares; both untradable until finalization
        SecurityChange rightsRemoval = SecurityChange.builder()
                .uuid(UUID.randomUUID())
                .portfolio(this.portfolio)
                .datetime(this.datetime)
                .security(this.stockRightSecurity)
                .volumeChange(this.volume.negate())
                .action(this)
                .isTradable(Boolean.FALSE)
                .build();

        SecurityChange sharesAddition = SecurityChange.builder()
                .uuid(UUID.randomUUID())
                .portfolio(this.portfolio)
                .datetime(this.datetime)
                .security(this.mainSecurity)
                .volumeChange(this.volume)
                .action(this)
                .isTradable(Boolean.FALSE)
                .build();

        return List.of(rightsRemoval, sharesAddition);
    }

    @Override
    public String accept(ActionVisitor visitor) {
        // If visitor supports StockRightUsage it will return a description; otherwise fall back
        try {
            return visitor.visit(this);
        } catch (Exception ignored) {
            return String.format("[%s] Exercised %s rights of %s into %s", this.datetime,
                    this.volume, stockRightSecurity.getSymbol(), mainSecurity.getSymbol());
        }
    }

    /**
     * Sets the internal actionType to the appropriate enum constant.  Lombok's builder
     * will call this method after object construction.
     */
    private void setActionType() {
        this.actionType = ActionType.STOCK_RIGHT_USAGE;
    }
}