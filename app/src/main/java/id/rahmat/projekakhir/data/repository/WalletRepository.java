package id.rahmat.projekakhir.data.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import id.rahmat.projekakhir.BuildConfig;
import id.rahmat.projekakhir.utils.FormatUtils;
import id.rahmat.projekakhir.utils.AppPreferences;
import id.rahmat.projekakhir.wallet.TokenBalance;
import id.rahmat.projekakhir.wallet.EthereumNetwork;
import id.rahmat.projekakhir.wallet.EthereumNetworkRegistry;
import id.rahmat.projekakhir.wallet.EthereumService;
import id.rahmat.projekakhir.wallet.WalletManager;
import id.rahmat.projekakhir.wallet.WalletSnapshot;

public class WalletRepository {

    private static final BigDecimal USD_TO_IDR_FALLBACK = new BigDecimal("15500");
    private final WalletManager walletManager;
    private final EthereumService ethereumService;
    private final PriceRepository priceRepository;
    private final AppPreferences appPreferences;

    private static class TrackedSwapToken {
        final String name;
        final String symbol;
        final String tokenAddress;
        final String poolAddress;
        final int decimals;
        final String imageUrl;

        TrackedSwapToken(String name, String symbol, String tokenAddress, String poolAddress,
                         int decimals, String imageUrl) {
            this.name = name;
            this.symbol = symbol;
            this.tokenAddress = tokenAddress;
            this.poolAddress = poolAddress;
            this.decimals = decimals;
            this.imageUrl = imageUrl;
        }
    }

    public WalletRepository(WalletManager walletManager, EthereumService ethereumService,
                            PriceRepository priceRepository, AppPreferences appPreferences) {
        this.walletManager = walletManager;
        this.ethereumService = ethereumService;
        this.priceRepository = priceRepository;
        this.appPreferences = appPreferences;
    }

    public boolean hasWallet() {
        return walletManager.hasWallet();
    }

    public String getWalletAddress() {
        return walletManager.getWalletAddress();
    }

    public String getMnemonic() {
        return walletManager.getMnemonic();
    }

    public WalletManager getWalletManager() {
        return walletManager;
    }

    public EthereumNetwork getSelectedNetwork() {
        return EthereumNetworkRegistry.resolve(appPreferences.getSelectedNetwork(), appPreferences);
    }

    public void clearSession() {
        walletManager.clearWallet();
        appPreferences.clearSession();
    }

    public WalletSnapshot loadWalletSnapshot() throws Exception {
        if (!walletManager.hasWallet()) {
            EthereumNetwork network = getSelectedNetwork();
            return new WalletSnapshot(
                    "",
                    network.getDisplayName(),
                    network.getNativeAssetName(),
                    network.getNativeSymbol(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        EthereumNetwork network = getSelectedNetwork();
        BigDecimal balance = ethereumService.getBalance(walletManager.getWalletAddress(), network);
        PriceRepository.PriceSnapshot priceSnapshot = priceRepository.getLatestPrice(network);
        return new WalletSnapshot(
                walletManager.getWalletAddress(),
                network.getDisplayName(),
                network.getNativeAssetName(),
                network.getNativeSymbol(),
                balance,
                priceSnapshot.usd,
                priceSnapshot.idr
        );
    }

    public List<TokenBalance> loadTokenBalances() throws Exception {
        if (!walletManager.hasWallet()) {
            return Collections.emptyList();
        }

        String walletAddress = walletManager.getWalletAddress();
        if (walletAddress == null || walletAddress.isEmpty()) {
            return Collections.emptyList();
        }

        List<TokenBalance> tokens = loadNativeAssetBalances(walletAddress);
        tokens.addAll(loadTrackedTokenBalances(walletAddress, getSelectedNetwork()));
        return tokens;
    }

    private List<TokenBalance> loadNativeAssetBalances(String walletAddress) {
        List<TokenBalance> balances = new ArrayList<>();
        List<EthereumNetwork> orderedNetworks = new ArrayList<>();
        EthereumNetwork selectedNetwork = getSelectedNetwork();
        orderedNetworks.add(selectedNetwork);
        for (EthereumNetwork network : EthereumNetworkRegistry.getAll(appPreferences)) {
            if (selectedNetwork.isSameNetwork(network)) {
                continue;
            }
            orderedNetworks.add(network);
        }

        for (EthereumNetwork network : orderedNetworks) {
            if (!network.hasRpcUrl()) {
                continue;
            }
            try {
                BigDecimal nativeBalance = ethereumService.getBalance(walletAddress, network);
                PriceRepository.PriceSnapshot priceSnapshot = priceRepository.getLatestPrice(network);
                balances.add(new TokenBalance(
                        network.getNativeAssetName(),
                        network.getDisplayName(),
                        network.getNativeSymbol(),
                        nativeBalance,
                        null,
                        priceSnapshot.idr,
                        priceSnapshot.usd,
                        true
                ));
            } catch (Exception ignored) {
                // Skip broken RPCs so one bad endpoint does not block the full home portfolio.
            }
        }
        return balances;
    }

    private List<TokenBalance> loadTrackedTokenBalances(String walletAddress, EthereumNetwork network) throws Exception {
        if (!EthereumNetwork.SEPOLIA.isSameNetwork(network)) {
            return Collections.emptyList();
        }
        List<TokenBalance> tokens = new ArrayList<>();
        PriceRepository.PriceSnapshot nativePrice = priceRepository.getLatestPrice(network);
        Set<String> addedAddresses = new LinkedHashSet<>();

        for (TrackedSwapToken token : getTrackedSwapTokens()) {
            if (!hasAddress(token.tokenAddress) || addedAddresses.contains(token.tokenAddress.toLowerCase())) {
                continue;
            }

            tokens.add(loadTrackedTokenBalance(walletAddress, network, nativePrice, token));
            addedAddresses.add(token.tokenAddress.toLowerCase());
        }

        return tokens;
    }

    private TokenBalance loadTrackedTokenBalance(String walletAddress, EthereumNetwork network,
                                                 PriceRepository.PriceSnapshot nativePrice,
                                                 TrackedSwapToken token) {
        try {
            BigDecimal balance = ethereumService.getErc20Balance(
                    walletAddress,
                    token.tokenAddress,
                    token.decimals,
                    network
            );
            PriceRepository.PriceSnapshot unitPrice = getDisplayPrice(token, nativePrice);
            BigDecimal unitPriceEth = hasAddress(token.poolAddress) && unitPrice.usd.compareTo(BigDecimal.ZERO) <= 0
                    ? ethereumService.quoteTokenToEth(BigDecimal.ONE, token.decimals, token.poolAddress, network)
                    : BigDecimal.ZERO;
            BigDecimal unitPriceUsd = unitPrice.usd.compareTo(BigDecimal.ZERO) > 0
                    ? unitPrice.usd
                    : FormatUtils.safeMultiply(unitPriceEth, nativePrice.usd);
            BigDecimal unitPriceIdr = unitPrice.idr.compareTo(BigDecimal.ZERO) > 0
                    ? unitPrice.idr
                    : FormatUtils.safeMultiply(unitPriceEth, nativePrice.idr);
            return new TokenBalance(
                    token.name,
                    network.getDisplayName(),
                    token.symbol,
                    balance,
                    token.imageUrl,
                    unitPriceIdr,
                    unitPriceUsd,
                    false,
                    buildUnitPriceLabel(token.symbol, unitPriceUsd, unitPriceIdr)
            );
        } catch (Exception exception) {
            PriceRepository.PriceSnapshot unitPrice = getDisplayPrice(token, nativePrice);
            return new TokenBalance(
                    token.name,
                    network.getDisplayName(),
                    token.symbol,
                    BigDecimal.ZERO,
                    token.imageUrl,
                    unitPrice.idr,
                    unitPrice.usd,
                    false,
                    buildUnitPriceLabel(token.symbol, unitPrice.usd, unitPrice.idr)
            );
        }
    }

    private PriceRepository.PriceSnapshot getDisplayPrice(TrackedSwapToken token,
                                                          PriceRepository.PriceSnapshot nativePrice) {
        String symbol = safeString(token.symbol).toUpperCase();
        if ("USDT".equals(symbol) || "USDC".equals(symbol) || "DAI".equals(symbol)) {
            return usdPrice("1");
        }
        if ("IDRX".equals(symbol)) {
            return new PriceRepository.PriceSnapshot(
                    BigDecimal.ONE.divide(USD_TO_IDR_FALLBACK, 12, java.math.RoundingMode.HALF_UP),
                    BigDecimal.ONE
            );
        }
        if ("WBTC".equals(symbol)) {
            return usdPrice("70000");
        }
        if ("LINK".equals(symbol)) {
            return usdPrice("15");
        }
        if ("UNI".equals(symbol)) {
            return usdPrice("8");
        }
        if ("AAVE".equals(symbol)) {
            return usdPrice("100");
        }
        if ("ARB".equals(symbol)) {
            return usdPrice("1");
        }
        if ("OP".equals(symbol)) {
            return usdPrice("2");
        }
        if ("SHIB".equals(symbol)) {
            return usdPrice("0.000025");
        }
        if ("PEPE".equals(symbol)) {
            return usdPrice("0.000012");
        }
        if ("MATS".equals(symbol)) {
            return new PriceRepository.PriceSnapshot(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new PriceRepository.PriceSnapshot(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private PriceRepository.PriceSnapshot usdPrice(String usdValue) {
        BigDecimal usd = new BigDecimal(usdValue);
        return new PriceRepository.PriceSnapshot(usd, usd.multiply(USD_TO_IDR_FALLBACK));
    }

    private String buildUnitPriceLabel(String symbol, BigDecimal unitPriceUsd, BigDecimal unitPriceIdr) {
        if (unitPriceUsd == null || unitPriceUsd.compareTo(BigDecimal.ZERO) <= 0
                || unitPriceIdr == null || unitPriceIdr.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return "1 " + symbol + " ≈ " + FormatUtils.formatUsd(unitPriceUsd)
                + " | " + FormatUtils.formatIdr(unitPriceIdr);
    }

    private List<TrackedSwapToken> getTrackedSwapTokens() {
        List<TrackedSwapToken> tokens = new ArrayList<>();
        tokens.add(new TrackedSwapToken(
                "Mats Token",
                "MATS",
                BuildConfig.MATS_TOKEN_ADDRESS,
                BuildConfig.MATS_SWAP_POOL_ADDRESS,
                18,
                null
        ));
        tokens.add(new TrackedSwapToken(
                "IDRX Token",
                "IDRX",
                BuildConfig.IDRX_TOKEN_ADDRESS,
                BuildConfig.IDRX_SWAP_POOL_ADDRESS,
                18,
                null
        ));
        tokens.add(new TrackedSwapToken(
                "Tether USD",
                "USDT",
                BuildConfig.SWAP_USDT_TOKEN_ADDRESS,
                BuildConfig.SWAP_USDT_POOL_ADDRESS,
                BuildConfig.SWAP_USDT_DECIMALS,
                null
        ));
        tokens.add(new TrackedSwapToken(
                "USD Coin",
                "USDC",
                BuildConfig.SWAP_USDC_TOKEN_ADDRESS,
                BuildConfig.SWAP_USDC_POOL_ADDRESS,
                BuildConfig.SWAP_USDC_DECIMALS,
                null
        ));
        tokens.add(new TrackedSwapToken(
                "Dai Stablecoin",
                "DAI",
                BuildConfig.SWAP_DAI_TOKEN_ADDRESS,
                BuildConfig.SWAP_DAI_POOL_ADDRESS,
                BuildConfig.SWAP_DAI_DECIMALS,
                null
        ));
        tokens.add(new TrackedSwapToken(
                "Wrapped Bitcoin",
                "WBTC",
                BuildConfig.SWAP_WBTC_TOKEN_ADDRESS,
                BuildConfig.SWAP_WBTC_POOL_ADDRESS,
                BuildConfig.SWAP_WBTC_DECIMALS,
                null
        ));
        tokens.add(new TrackedSwapToken(
                "Chainlink",
                "LINK",
                BuildConfig.SWAP_LINK_TOKEN_ADDRESS,
                BuildConfig.SWAP_LINK_POOL_ADDRESS,
                BuildConfig.SWAP_LINK_DECIMALS,
                null
        ));
        tokens.add(new TrackedSwapToken(
                "Uniswap",
                "UNI",
                BuildConfig.SWAP_UNI_TOKEN_ADDRESS,
                BuildConfig.SWAP_UNI_POOL_ADDRESS,
                BuildConfig.SWAP_UNI_DECIMALS,
                null
        ));
        tokens.add(new TrackedSwapToken(
                "Aave",
                "AAVE",
                BuildConfig.SWAP_AAVE_TOKEN_ADDRESS,
                BuildConfig.SWAP_AAVE_POOL_ADDRESS,
                BuildConfig.SWAP_AAVE_DECIMALS,
                null
        ));
        tokens.add(new TrackedSwapToken(
                "Shiba Inu",
                "SHIB",
                BuildConfig.SWAP_SHIB_TOKEN_ADDRESS,
                BuildConfig.SWAP_SHIB_POOL_ADDRESS,
                BuildConfig.SWAP_SHIB_DECIMALS,
                null
        ));
        tokens.add(new TrackedSwapToken(
                "Pepe",
                "PEPE",
                BuildConfig.SWAP_PEPE_TOKEN_ADDRESS,
                BuildConfig.SWAP_PEPE_POOL_ADDRESS,
                BuildConfig.SWAP_PEPE_DECIMALS,
                null
        ));
        tokens.add(new TrackedSwapToken(
                "Arbitrum",
                "ARB",
                BuildConfig.SWAP_ARB_TOKEN_ADDRESS,
                BuildConfig.SWAP_ARB_POOL_ADDRESS,
                BuildConfig.SWAP_ARB_DECIMALS,
                null
        ));
        tokens.add(new TrackedSwapToken(
                "Optimism",
                "OP",
                BuildConfig.SWAP_OP_TOKEN_ADDRESS,
                BuildConfig.SWAP_OP_POOL_ADDRESS,
                BuildConfig.SWAP_OP_DECIMALS,
                null
        ));
        addCustomTrackedToken(tokens, BuildConfig.SWAP_TOKEN_1_NAME, BuildConfig.SWAP_TOKEN_1_SYMBOL,
                BuildConfig.SWAP_TOKEN_1_ADDRESS, BuildConfig.SWAP_TOKEN_1_POOL_ADDRESS, BuildConfig.SWAP_TOKEN_1_DECIMALS);
        addCustomTrackedToken(tokens, BuildConfig.SWAP_TOKEN_2_NAME, BuildConfig.SWAP_TOKEN_2_SYMBOL,
                BuildConfig.SWAP_TOKEN_2_ADDRESS, BuildConfig.SWAP_TOKEN_2_POOL_ADDRESS, BuildConfig.SWAP_TOKEN_2_DECIMALS);
        addCustomTrackedToken(tokens, BuildConfig.SWAP_TOKEN_3_NAME, BuildConfig.SWAP_TOKEN_3_SYMBOL,
                BuildConfig.SWAP_TOKEN_3_ADDRESS, BuildConfig.SWAP_TOKEN_3_POOL_ADDRESS, BuildConfig.SWAP_TOKEN_3_DECIMALS);
        addCustomTrackedToken(tokens, BuildConfig.SWAP_TOKEN_4_NAME, BuildConfig.SWAP_TOKEN_4_SYMBOL,
                BuildConfig.SWAP_TOKEN_4_ADDRESS, BuildConfig.SWAP_TOKEN_4_POOL_ADDRESS, BuildConfig.SWAP_TOKEN_4_DECIMALS);
        addCustomTrackedToken(tokens, BuildConfig.SWAP_TOKEN_5_NAME, BuildConfig.SWAP_TOKEN_5_SYMBOL,
                BuildConfig.SWAP_TOKEN_5_ADDRESS, BuildConfig.SWAP_TOKEN_5_POOL_ADDRESS, BuildConfig.SWAP_TOKEN_5_DECIMALS);
        addCustomTrackedToken(tokens, BuildConfig.SWAP_TOKEN_6_NAME, BuildConfig.SWAP_TOKEN_6_SYMBOL,
                BuildConfig.SWAP_TOKEN_6_ADDRESS, BuildConfig.SWAP_TOKEN_6_POOL_ADDRESS, BuildConfig.SWAP_TOKEN_6_DECIMALS);
        addCustomTrackedToken(tokens, BuildConfig.SWAP_TOKEN_7_NAME, BuildConfig.SWAP_TOKEN_7_SYMBOL,
                BuildConfig.SWAP_TOKEN_7_ADDRESS, BuildConfig.SWAP_TOKEN_7_POOL_ADDRESS, BuildConfig.SWAP_TOKEN_7_DECIMALS);
        addCustomTrackedToken(tokens, BuildConfig.SWAP_TOKEN_8_NAME, BuildConfig.SWAP_TOKEN_8_SYMBOL,
                BuildConfig.SWAP_TOKEN_8_ADDRESS, BuildConfig.SWAP_TOKEN_8_POOL_ADDRESS, BuildConfig.SWAP_TOKEN_8_DECIMALS);
        addCustomTrackedToken(tokens, BuildConfig.SWAP_TOKEN_9_NAME, BuildConfig.SWAP_TOKEN_9_SYMBOL,
                BuildConfig.SWAP_TOKEN_9_ADDRESS, BuildConfig.SWAP_TOKEN_9_POOL_ADDRESS, BuildConfig.SWAP_TOKEN_9_DECIMALS);
        addCustomTrackedToken(tokens, BuildConfig.SWAP_TOKEN_10_NAME, BuildConfig.SWAP_TOKEN_10_SYMBOL,
                BuildConfig.SWAP_TOKEN_10_ADDRESS, BuildConfig.SWAP_TOKEN_10_POOL_ADDRESS, BuildConfig.SWAP_TOKEN_10_DECIMALS);
        addCustomTrackedToken(tokens, BuildConfig.SWAP_TOKEN_11_NAME, BuildConfig.SWAP_TOKEN_11_SYMBOL,
                BuildConfig.SWAP_TOKEN_11_ADDRESS, BuildConfig.SWAP_TOKEN_11_POOL_ADDRESS, BuildConfig.SWAP_TOKEN_11_DECIMALS);
        addCustomTrackedToken(tokens, BuildConfig.SWAP_TOKEN_12_NAME, BuildConfig.SWAP_TOKEN_12_SYMBOL,
                BuildConfig.SWAP_TOKEN_12_ADDRESS, BuildConfig.SWAP_TOKEN_12_POOL_ADDRESS, BuildConfig.SWAP_TOKEN_12_DECIMALS);
        return tokens;
    }

    private void addCustomTrackedToken(List<TrackedSwapToken> tokens, String name, String symbol,
                                       String tokenAddress, String poolAddress, int decimals) {
        if (!hasAddress(tokenAddress)) {
            return;
        }
        String safeSymbol = safeString(symbol).isEmpty() ? "TOKEN" : safeString(symbol).toUpperCase();
        String safeName = safeString(name).isEmpty() ? safeSymbol + " Token" : safeString(name);
        tokens.add(new TrackedSwapToken(safeName, safeSymbol, tokenAddress, poolAddress, Math.max(decimals, 0), null));
    }

    private boolean hasAddress(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
