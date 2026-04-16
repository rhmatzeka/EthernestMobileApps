package id.rahmat.projekakhir.data.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import id.rahmat.projekakhir.BuildConfig;
import id.rahmat.projekakhir.utils.FormatUtils;
import id.rahmat.projekakhir.utils.AppPreferences;
import id.rahmat.projekakhir.wallet.TokenBalance;
import id.rahmat.projekakhir.wallet.EthereumNetwork;
import id.rahmat.projekakhir.wallet.EthereumService;
import id.rahmat.projekakhir.wallet.WalletManager;
import id.rahmat.projekakhir.wallet.WalletSnapshot;

public class WalletRepository {

    private final WalletManager walletManager;
    private final EthereumService ethereumService;
    private final PriceRepository priceRepository;
    private final AppPreferences appPreferences;

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
        return EthereumNetwork.fromKey(appPreferences.getSelectedNetwork());
    }

    public void clearSession() {
        walletManager.clearWallet();
        appPreferences.clearSession();
    }

    public WalletSnapshot loadWalletSnapshot() throws Exception {
        if (!walletManager.hasWallet()) {
            return new WalletSnapshot("", getSelectedNetwork().getDisplayName(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        EthereumNetwork network = getSelectedNetwork();
        BigDecimal balance = ethereumService.getBalance(walletManager.getWalletAddress(), network);
        PriceRepository.PriceSnapshot priceSnapshot = priceRepository.getLatestEthPrice();
        return new WalletSnapshot(
                walletManager.getWalletAddress(),
                network.getDisplayName(),
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

        EthereumNetwork network = getSelectedNetwork();
        if (network != EthereumNetwork.SEPOLIA) {
            return Collections.emptyList();
        }

        List<TokenBalance> tokens = new ArrayList<>();
        PriceRepository.PriceSnapshot ethPrice = priceRepository.getLatestEthPrice();

        if (BuildConfig.MATS_TOKEN_ADDRESS != null && !BuildConfig.MATS_TOKEN_ADDRESS.isEmpty()) {
            int matsDecimals = 18;
            BigDecimal matsBalance = ethereumService.getErc20Balance(walletAddress, BuildConfig.MATS_TOKEN_ADDRESS, matsDecimals, network);
            BigDecimal matsUnitPriceEth = BigDecimal.ZERO;
            if (BuildConfig.MATS_SWAP_POOL_ADDRESS != null && !BuildConfig.MATS_SWAP_POOL_ADDRESS.isEmpty()) {
                matsUnitPriceEth = ethereumService.quoteMatsToEth(BigDecimal.ONE, network);
            }
            tokens.add(new TokenBalance(
                    "Mats Token",
                    "MATS",
                    matsBalance,
                    "https://assets.coingecko.com/coins/images/279/small/ethereum.png",
                    FormatUtils.safeMultiply(matsUnitPriceEth, ethPrice.idr),
                    FormatUtils.safeMultiply(matsUnitPriceEth, ethPrice.usd)
            ));
        }

        if (BuildConfig.IDRX_TOKEN_ADDRESS != null && !BuildConfig.IDRX_TOKEN_ADDRESS.isEmpty()) {
            int idrxDecimals = ethereumService.getErc20Decimals(BuildConfig.IDRX_TOKEN_ADDRESS, network);
            String idrxSymbol = ethereumService.getErc20Symbol(BuildConfig.IDRX_TOKEN_ADDRESS, network);
            BigDecimal idrxBalance = ethereumService.getErc20Balance(walletAddress, BuildConfig.IDRX_TOKEN_ADDRESS, idrxDecimals, network);
            BigDecimal idrxUnitPriceIdr = "IDRX".equalsIgnoreCase(idrxSymbol) ? BigDecimal.ONE : BigDecimal.ZERO;
            tokens.add(new TokenBalance(
                    "IDRX Token",
                    idrxSymbol == null || idrxSymbol.isEmpty() ? "IDRX" : idrxSymbol,
                    idrxBalance,
                    null,
                    idrxUnitPriceIdr,
                    BigDecimal.ZERO
            ));
        }
        return tokens;
    }
}
