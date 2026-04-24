package id.rahmat.projekakhir.ui.swap;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.math.BigDecimal;

import id.rahmat.projekakhir.BuildConfig;
import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.data.repository.WalletRepository;
import id.rahmat.projekakhir.di.ServiceLocator;
import id.rahmat.projekakhir.utils.AppExecutors;
import id.rahmat.projekakhir.utils.FormatUtils;
import id.rahmat.projekakhir.wallet.EthereumNetwork;
import id.rahmat.projekakhir.wallet.EthereumService;
import org.web3j.crypto.Credentials;

public class SwapViewModel extends AndroidViewModel {

    public enum Direction {
        TOKEN_TO_ETH,
        ETH_TO_TOKEN
    }

    public enum Asset {
        MATS,
        IDRX,
        ETH,
        BNB,
        AVAX,
        POL,
        FTM
    }

    public static class SwapAssetConfig {
        public final Asset asset;
        public final String symbol;
        public final String tokenAddress;
        public final String poolAddress;
        public final int decimals;

        public SwapAssetConfig(Asset asset, String symbol, String tokenAddress, String poolAddress, int decimals) {
            this.asset = asset;
            this.symbol = symbol;
            this.tokenAddress = tokenAddress;
            this.poolAddress = poolAddress;
            this.decimals = decimals;
        }
    }

    public static class SwapSuccessEvent {
        public final String symbol;
        public final String transactionHash;
        public final String shortTransactionHash;

        public SwapSuccessEvent(String symbol, String transactionHash, String shortTransactionHash) {
            this.symbol = symbol;
            this.transactionHash = transactionHash;
            this.shortTransactionHash = shortTransactionHash;
        }
    }

    private final WalletRepository walletRepository;
    private final EthereumService ethereumService;
    private final MutableLiveData<String> quoteState = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> approvalRequired = new MutableLiveData<>(false);
    private final MutableLiveData<String> eventState = new MutableLiveData<>(null);
    private final MutableLiveData<SwapSuccessEvent> swapSuccessState = new MutableLiveData<>(null);
    private Asset selectedAsset = Asset.MATS;

    public SwapViewModel(@NonNull Application application) {
        super(application);
        walletRepository = ServiceLocator.getWalletRepository(application);
        ethereumService = new EthereumService();
        if (!hasTokenFor(Asset.MATS) && hasTokenFor(Asset.IDRX)) {
            selectedAsset = Asset.IDRX;
        }
    }

    public LiveData<String> getQuoteState() {
        return quoteState;
    }

    public LiveData<Boolean> getApprovalRequired() {
        return approvalRequired;
    }

    public LiveData<String> getEventState() {
        return eventState;
    }

    public LiveData<SwapSuccessEvent> getSwapSuccessState() {
        return swapSuccessState;
    }

    public String getNetworkLabel() {
        return walletRepository.getSelectedNetwork().getDisplayName();
    }

    public void setSelectedAsset(Asset asset) {
        selectedAsset = asset;
    }

    public Asset getSelectedAsset() {
        return selectedAsset;
    }

    public boolean hasAsset(Asset asset) {
        return hasTokenFor(asset);
    }

    public boolean isSwapReady(Asset asset) {
        return hasSwapConfig(getConfig(asset));
    }

    public String getAssetSymbol() {
        return getConfig(selectedAsset).symbol;
    }

    public String getAssetSymbol(Asset asset) {
        return getConfig(asset).symbol;
    }

    public String getAssetDisplayLabel(Asset asset) {
        SwapAssetConfig config = getConfig(asset);
        String base = config.symbol + " • " + getAssetName(asset);
        return isSwapReady(asset) ? base : base + " (Segera hadir)";
    }

    public String getAssetName(Asset asset) {
        switch (asset) {
            case IDRX:
                return getApplication().getString(R.string.swap_asset_name_idrx);
            case ETH:
                return getApplication().getString(R.string.swap_asset_name_eth);
            case BNB:
                return getApplication().getString(R.string.swap_asset_name_bnb);
            case AVAX:
                return getApplication().getString(R.string.swap_asset_name_avax);
            case POL:
                return getApplication().getString(R.string.swap_asset_name_pol);
            case FTM:
                return getApplication().getString(R.string.swap_asset_name_ftm);
            case MATS:
            default:
                return getApplication().getString(R.string.swap_asset_name_mats);
        }
    }

    public String getTokenToEthLabel() {
        return getAssetSymbol() + " ke ETH";
    }

    public String getEthToTokenLabel() {
        return "ETH ke " + getAssetSymbol();
    }

    public String getApproveLabel() {
        return getApplication().getString(R.string.swap_approve_action, getAssetSymbol());
    }

    public String getExecuteLabel() {
        return getApplication().getString(R.string.swap_execute_action, getAssetSymbol());
    }

    public boolean isSelectedAssetReady() {
        return isSwapReady(selectedAsset);
    }

    public void refreshQuote(Direction direction, String rawAmount) {
        AppExecutors.io().execute(() -> {
            SwapAssetConfig config = getConfig(selectedAsset);
            if (!hasSwapConfig(config)) {
                quoteState.postValue(getApplication().getString(R.string.swap_asset_coming_soon, config.symbol));
                approvalRequired.postValue(false);
                return;
            }

            if (rawAmount == null || rawAmount.trim().isEmpty()) {
                quoteState.postValue(getApplication().getString(R.string.swap_quote_placeholder));
                approvalRequired.postValue(false);
                return;
            }

            try {
                BigDecimal amount = new BigDecimal(rawAmount.trim());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    quoteState.postValue(getApplication().getString(R.string.swap_quote_placeholder));
                    approvalRequired.postValue(false);
                    return;
                }

                EthereumNetwork network = walletRepository.getSelectedNetwork();
                if (direction == Direction.TOKEN_TO_ETH) {
                    BigDecimal ethOut = ethereumService.quoteTokenToEth(amount, config.decimals, config.poolAddress, network);
                    if (ethOut.compareTo(BigDecimal.ZERO) <= 0) {
                        quoteState.postValue(getApplication().getString(R.string.swap_pool_empty, config.symbol));
                        approvalRequired.postValue(false);
                        return;
                    }
                    BigDecimal allowance = ethereumService.getErc20Allowance(
                            walletRepository.getWalletAddress(),
                            config.tokenAddress,
                            config.poolAddress,
                            config.decimals,
                            network
                    );
                    approvalRequired.postValue(allowance.compareTo(amount) < 0);
                    quoteState.postValue(
                            getApplication().getString(R.string.swap_quote_receive, FormatUtils.formatEth(ethOut))
                    );
                } else {
                    BigDecimal tokenOut = ethereumService.quoteEthToToken(amount, config.decimals, config.poolAddress, network);
                    if (tokenOut.compareTo(BigDecimal.ZERO) <= 0) {
                        quoteState.postValue(getApplication().getString(R.string.swap_pool_empty, config.symbol));
                        approvalRequired.postValue(false);
                        return;
                    }
                    approvalRequired.postValue(false);
                    quoteState.postValue(
                            getApplication().getString(R.string.swap_quote_receive, FormatUtils.formatToken(tokenOut, config.symbol))
                    );
                }
            } catch (Exception exception) {
                quoteState.postValue(getApplication().getString(R.string.swap_quote_failed));
                approvalRequired.postValue(false);
            }
        });
    }

    public void approveSelectedToken(String rawAmount) {
        AppExecutors.io().execute(() -> {
            SwapAssetConfig config = getConfig(selectedAsset);
            try {
                if (!hasSwapConfig(config)) {
                    eventState.postValue(getApplication().getString(R.string.swap_asset_coming_soon, config.symbol));
                    return;
                }
                BigDecimal amount = new BigDecimal(rawAmount.trim());
                Credentials credentials = walletRepository.getWalletManager().getCredentials();
                ethereumService.approveErc20(
                        credentials,
                        config.tokenAddress,
                        config.poolAddress,
                        amount,
                        config.decimals,
                        walletRepository.getSelectedNetwork()
                );
                approvalRequired.postValue(false);
                eventState.postValue(getApplication().getString(
                        R.string.swap_approve_success,
                        config.symbol,
                        shortenHash(config.poolAddress)
                ));
            } catch (Exception exception) {
                eventState.postValue(getApplication().getString(R.string.swap_approve_failed, config.symbol));
            }
        });
    }

    public void executeSwap(Direction direction, String rawAmount) {
        AppExecutors.io().execute(() -> {
            SwapAssetConfig config = getConfig(selectedAsset);
            try {
                if (!hasSwapConfig(config)) {
                    eventState.postValue(getApplication().getString(R.string.swap_asset_coming_soon, config.symbol));
                    return;
                }
                BigDecimal amount = new BigDecimal(rawAmount.trim());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    eventState.postValue(getApplication().getString(R.string.swap_invalid_amount));
                    return;
                }

                Credentials credentials = walletRepository.getWalletManager().getCredentials();
                EthereumNetwork network = walletRepository.getSelectedNetwork();
                String transactionHash;
                if (direction == Direction.TOKEN_TO_ETH) {
                    BigDecimal ethOut = ethereumService.quoteTokenToEth(amount, config.decimals, config.poolAddress, network);
                    if (ethOut.compareTo(BigDecimal.ZERO) <= 0) {
                        eventState.postValue(getApplication().getString(R.string.swap_pool_empty, config.symbol));
                        return;
                    }
                    BigDecimal minEthOut = applySlippage(ethOut);
                    transactionHash = ethereumService.swapTokenForEth(
                            credentials,
                            config.poolAddress,
                            amount,
                            config.decimals,
                            minEthOut,
                            network
                    );
                } else {
                    BigDecimal tokenOut = ethereumService.quoteEthToToken(amount, config.decimals, config.poolAddress, network);
                    if (tokenOut.compareTo(BigDecimal.ZERO) <= 0) {
                        eventState.postValue(getApplication().getString(R.string.swap_pool_empty, config.symbol));
                        return;
                    }
                    BigDecimal minTokenOut = applySlippage(tokenOut);
                    transactionHash = ethereumService.swapEthForToken(
                            credentials,
                            config.poolAddress,
                            amount,
                            minTokenOut,
                            config.decimals,
                            network
                    );
                }
                swapSuccessState.postValue(new SwapSuccessEvent(
                        config.symbol,
                        transactionHash,
                        shortenHash(transactionHash)
                ));
            } catch (Exception exception) {
                eventState.postValue(getApplication().getString(R.string.swap_failed));
            }
        });
    }

    public String getPoolNote() {
        if (!isSelectedAssetReady()) {
            return getApplication().getString(R.string.swap_asset_coming_soon_note, getAssetSymbol());
        }
        return getApplication().getString(R.string.swap_pool_note, getAssetSymbol());
    }

    private BigDecimal applySlippage(BigDecimal amountOut) {
        return amountOut.multiply(new BigDecimal("0.97"));
    }

    private boolean hasTokenFor(Asset asset) {
        if (asset == Asset.ETH
                || asset == Asset.BNB
                || asset == Asset.AVAX
                || asset == Asset.POL
                || asset == Asset.FTM) {
            return true;
        }
        SwapAssetConfig config = getConfig(asset);
        return config.tokenAddress != null && !config.tokenAddress.isEmpty();
    }

    private boolean hasSwapConfig(SwapAssetConfig config) {
        return config.tokenAddress != null
                && !config.tokenAddress.isEmpty()
                && config.poolAddress != null
                && !config.poolAddress.isEmpty();
    }

    private SwapAssetConfig getConfig(Asset asset) {
        switch (asset) {
            case IDRX:
                return new SwapAssetConfig(
                        Asset.IDRX,
                        "IDRX",
                        BuildConfig.IDRX_TOKEN_ADDRESS,
                        BuildConfig.IDRX_SWAP_POOL_ADDRESS,
                        18
                );
            case ETH:
                return new SwapAssetConfig(Asset.ETH, "ETH", "", "", 18);
            case BNB:
                return new SwapAssetConfig(Asset.BNB, "BNB", "", "", 18);
            case AVAX:
                return new SwapAssetConfig(Asset.AVAX, "AVAX", "", "", 18);
            case POL:
                return new SwapAssetConfig(Asset.POL, "POL", "", "", 18);
            case FTM:
                return new SwapAssetConfig(Asset.FTM, "FTM", "", "", 18);
            case MATS:
            default:
                return new SwapAssetConfig(
                        Asset.MATS,
                        "MATS",
                        BuildConfig.MATS_TOKEN_ADDRESS,
                        BuildConfig.MATS_SWAP_POOL_ADDRESS,
                        18
                );
        }
    }

    private String shortenHash(String value) {
        if (value == null || value.length() < 12) {
            return value == null ? "" : value;
        }
        return value.substring(0, 6) + "..." + value.substring(value.length() - 4);
    }
}
