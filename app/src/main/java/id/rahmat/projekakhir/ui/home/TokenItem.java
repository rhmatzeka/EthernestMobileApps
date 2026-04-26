package id.rahmat.projekakhir.ui.home;

import java.math.BigDecimal;

public class TokenItem {

    private final String symbol;
    private final String badge;
    private final String name;
    private final String balance;
    private final String fiatValue;
    private final String unitPrice;
    private final String imageUrl;
    private final int imageResId;
    private final BigDecimal balanceAmount;

    public TokenItem(String symbol, String badge, String name, String balance,
                     String fiatValue, String unitPrice, String imageUrl, int imageResId,
                     BigDecimal balanceAmount) {
        this.symbol = symbol;
        this.badge = badge;
        this.name = name;
        this.balance = balance;
        this.fiatValue = fiatValue;
        this.unitPrice = unitPrice == null ? "" : unitPrice;
        this.imageUrl = imageUrl;
        this.imageResId = imageResId;
        this.balanceAmount = balanceAmount == null ? BigDecimal.ZERO : balanceAmount;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getBadge() {
        return badge;
    }

    public String getName() {
        return name;
    }

    public String getBalance() {
        return balance;
    }

    public String getFiatValue() {
        return fiatValue;
    }

    public String getUnitPrice() {
        return unitPrice;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getImageResId() {
        return imageResId;
    }

    public boolean hasPositiveBalance() {
        return balanceAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}
