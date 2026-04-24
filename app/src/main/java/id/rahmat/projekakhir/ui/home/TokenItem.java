package id.rahmat.projekakhir.ui.home;

public class TokenItem {

    private final String symbol;
    private final String badge;
    private final String name;
    private final String balance;
    private final String fiatValue;
    private final String imageUrl;
    private final int imageResId;

    public TokenItem(String symbol, String badge, String name, String balance,
                     String fiatValue, String imageUrl, int imageResId) {
        this.symbol = symbol;
        this.badge = badge;
        this.name = name;
        this.balance = balance;
        this.fiatValue = fiatValue;
        this.imageUrl = imageUrl;
        this.imageResId = imageResId;
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

    public String getImageUrl() {
        return imageUrl;
    }

    public int getImageResId() {
        return imageResId;
    }
}
