package id.rahmat.projekakhir.wallet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import id.rahmat.projekakhir.BuildConfig;

public class EthereumNetwork {

    public static final EthereumNetwork MAINNET = preset(
            "mainnet",
            "Ethereum Mainnet",
            "Ethereum",
            "ETH",
            "ethereum",
            1L,
            buildInfuraRpcUrl("mainnet", BuildConfig.MAINNET_RPC_URL),
            "https://etherscan.io/tx/",
            true
    );

    public static final EthereumNetwork SEPOLIA = preset(
            "sepolia",
            "Sepolia Testnet",
            "Sepolia ETH",
            "ETH",
            "ethereum",
            11155111L,
            buildInfuraRpcUrl("sepolia", BuildConfig.SEPOLIA_RPC_URL),
            "https://sepolia.etherscan.io/tx/",
            true
    );

    public static final EthereumNetwork BSC = preset(
            "bsc",
            "BNB Smart Chain",
            "BNB",
            "BNB",
            "binancecoin",
            56L,
            trim(BuildConfig.BSC_RPC_URL),
            "https://bscscan.com/tx/",
            false
    );

    public static final EthereumNetwork AVALANCHE = preset(
            "avalanche",
            "Avalanche C-Chain",
            "Avalanche",
            "AVAX",
            "avalanche-2",
            43114L,
            trim(BuildConfig.AVALANCHE_RPC_URL),
            "https://snowtrace.io/tx/",
            false
    );

    public static final EthereumNetwork POLYGON = preset(
            "polygon",
            "Polygon PoS",
            "Polygon",
            "POL",
            "polygon-ecosystem-token",
            137L,
            trim(BuildConfig.POLYGON_RPC_URL),
            "https://polygonscan.com/tx/",
            false
    );

    public static final EthereumNetwork ARBITRUM = preset(
            "arbitrum",
            "Arbitrum One",
            "Arbitrum",
            "ETH",
            "ethereum",
            42161L,
            trim(BuildConfig.ARBITRUM_RPC_URL),
            "https://arbiscan.io/tx/",
            false
    );

    public static final EthereumNetwork OPTIMISM = preset(
            "optimism",
            "Optimism",
            "Optimism",
            "ETH",
            "ethereum",
            10L,
            trim(BuildConfig.OPTIMISM_RPC_URL),
            "https://optimistic.etherscan.io/tx/",
            false
    );

    public static final EthereumNetwork BASE = preset(
            "base",
            "Base",
            "Base",
            "ETH",
            "ethereum",
            8453L,
            trim(BuildConfig.BASE_RPC_URL),
            "https://basescan.org/tx/",
            false
    );

    public static final EthereumNetwork FANTOM = preset(
            "fantom",
            "Fantom Opera",
            "Fantom",
            "FTM",
            "fantom",
            250L,
            trim(BuildConfig.FANTOM_RPC_URL),
            "https://ftmscan.com/tx/",
            false
    );

    private static final List<EthereumNetwork> PRESETS;

    static {
        List<EthereumNetwork> presets = new ArrayList<>();
        presets.add(SEPOLIA);
        presets.add(MAINNET);
        presets.add(BSC);
        presets.add(AVALANCHE);
        presets.add(POLYGON);
        presets.add(ARBITRUM);
        presets.add(OPTIMISM);
        presets.add(BASE);
        presets.add(FANTOM);
        PRESETS = Collections.unmodifiableList(presets);
    }

    private final String key;
    private final String displayName;
    private final String nativeAssetName;
    private final String nativeSymbol;
    private final String coinGeckoAssetId;
    private final long chainId;
    private final String rpcUrl;
    private final String explorerTxBaseUrl;
    private final boolean supportsExplorerSync;
    private final boolean custom;

    public EthereumNetwork(String key, String displayName, String nativeAssetName, String nativeSymbol,
                           String coinGeckoAssetId, long chainId, String rpcUrl,
                           String explorerTxBaseUrl, boolean supportsExplorerSync, boolean custom) {
        this.key = trim(key);
        this.displayName = trim(displayName);
        this.nativeAssetName = trim(nativeAssetName);
        this.nativeSymbol = trim(nativeSymbol);
        this.coinGeckoAssetId = trim(coinGeckoAssetId);
        this.chainId = chainId;
        this.rpcUrl = trim(rpcUrl);
        this.explorerTxBaseUrl = trim(explorerTxBaseUrl);
        this.supportsExplorerSync = supportsExplorerSync;
        this.custom = custom;
    }

    private static EthereumNetwork preset(String key, String displayName, String nativeAssetName, String nativeSymbol,
                                          String coinGeckoAssetId, long chainId, String rpcUrl,
                                          String explorerTxBaseUrl, boolean supportsExplorerSync) {
        return new EthereumNetwork(
                key,
                displayName,
                nativeAssetName,
                nativeSymbol,
                coinGeckoAssetId,
                chainId,
                rpcUrl,
                explorerTxBaseUrl,
                supportsExplorerSync,
                false
        );
    }

    public static EthereumNetwork custom(String displayName, String nativeAssetName, String nativeSymbol,
                                         String coinGeckoAssetId, long chainId, String rpcUrl,
                                         String explorerTxBaseUrl) {
        String normalizedSymbol = trim(nativeSymbol).toUpperCase(Locale.US);
        String resolvedName = trim(nativeAssetName).isEmpty() ? normalizedSymbol : trim(nativeAssetName);
        String resolvedDisplay = trim(displayName).isEmpty() ? resolvedName : trim(displayName);
        String key = "custom-" + chainId + "-" + slugify(resolvedDisplay);
        return new EthereumNetwork(
                key,
                resolvedDisplay,
                resolvedName,
                normalizedSymbol,
                trim(coinGeckoAssetId),
                chainId,
                trim(rpcUrl),
                trim(explorerTxBaseUrl),
                false,
                true
        );
    }

    public static List<EthereumNetwork> getPresetNetworks() {
        return PRESETS;
    }

    public static EthereumNetwork defaultNetwork() {
        return fromPresetKey(BuildConfig.DEFAULT_ETH_NETWORK);
    }

    public static EthereumNetwork fromPresetKey(String key) {
        for (EthereumNetwork network : PRESETS) {
            if (network.key.equalsIgnoreCase(trim(key))) {
                return network;
            }
        }
        return SEPOLIA;
    }

    public boolean isCustom() {
        return custom;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNativeAssetName() {
        return nativeAssetName;
    }

    public String getNativeSymbol() {
        return nativeSymbol;
    }

    public String getCoinGeckoAssetId() {
        return coinGeckoAssetId;
    }

    public long getChainId() {
        return chainId;
    }

    public String getRpcUrl() {
        if (!hasRpcUrl()) {
            throw new IllegalStateException("RPC untuk " + displayName + " belum diisi. Tambahkan lewat Settings > Tambah Custom RPC.");
        }
        return rpcUrl;
    }

    public boolean hasRpcUrl() {
        return !rpcUrl.isEmpty();
    }

    public String getExplorerTxBaseUrl() {
        return explorerTxBaseUrl;
    }

    public boolean hasExplorerUrl() {
        return !explorerTxBaseUrl.isEmpty();
    }

    public boolean supportsExplorerSync() {
        return supportsExplorerSync;
    }

    public boolean isSameNetwork(EthereumNetwork other) {
        return other != null && key.equalsIgnoreCase(other.key);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EthereumNetwork)) {
            return false;
        }
        EthereumNetwork that = (EthereumNetwork) object;
        return key.equalsIgnoreCase(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key.toLowerCase(Locale.US));
    }

    private static String buildInfuraRpcUrl(String networkKey, String fallbackRpcUrl) {
        String projectId = trim(BuildConfig.INFURA_PROJECT_ID);
        if (projectId.isEmpty() || projectId.startsWith("your_")) {
            return trim(fallbackRpcUrl);
        }
        return "https://" + networkKey + ".infura.io/v3/" + projectId;
    }

    private static String slugify(String value) {
        String slug = trim(value).toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-");
        return slug.replaceAll("^-+", "").replaceAll("-+$", "");
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
