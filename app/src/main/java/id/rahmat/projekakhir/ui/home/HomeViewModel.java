package id.rahmat.projekakhir.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.github.mikephil.charting.data.CandleEntry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import id.rahmat.projekakhir.data.repository.PriceRepository;
import id.rahmat.projekakhir.data.repository.NftRepository;
import id.rahmat.projekakhir.data.repository.WalletRepository;
import id.rahmat.projekakhir.di.ServiceLocator;
import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.utils.AppExecutors;
import id.rahmat.projekakhir.utils.FormatUtils;
import id.rahmat.projekakhir.wallet.NftAsset;
import id.rahmat.projekakhir.wallet.TokenBalance;
import id.rahmat.projekakhir.wallet.WalletSnapshot;

public class HomeViewModel extends AndroidViewModel {

    public static class HomeUiState {
        public final String address;
        public final String shortAddress;
        public final String networkName;
        public final String nativeAssetName;
        public final String nativeAssetSymbol;
        public final String balancePrimary;
        public final String balanceIdr;
        public final String balanceUsd;
        public final String chartTitle;
        public final String chartSubtitle;
        public final List<TokenItem> assets;
        public final List<NftItem> nfts;
        public final List<CandleEntry> chartEntries;
        public final String errorMessage;

        public HomeUiState(String address, String shortAddress, String networkName,
                           String nativeAssetName, String nativeAssetSymbol, String balancePrimary,
                           String balanceIdr, String balanceUsd, String chartTitle, String chartSubtitle,
                           List<TokenItem> assets, List<NftItem> nfts,
                           List<CandleEntry> chartEntries, String errorMessage) {
            this.address = address;
            this.shortAddress = shortAddress;
            this.networkName = networkName;
            this.nativeAssetName = nativeAssetName;
            this.nativeAssetSymbol = nativeAssetSymbol;
            this.balancePrimary = balancePrimary;
            this.balanceIdr = balanceIdr;
            this.balanceUsd = balanceUsd;
            this.chartTitle = chartTitle;
            this.chartSubtitle = chartSubtitle;
            this.assets = assets;
            this.nfts = nfts;
            this.chartEntries = chartEntries;
            this.errorMessage = errorMessage;
        }
    }

    private final WalletRepository walletRepository;
    private final PriceRepository priceRepository;
    private final NftRepository nftRepository;
    private final MutableLiveData<HomeUiState> uiState = new MutableLiveData<>();

    public HomeViewModel(@NonNull Application application) {
        super(application);
        walletRepository = ServiceLocator.getWalletRepository(application);
        priceRepository = ServiceLocator.getPriceRepository(application);
        nftRepository = ServiceLocator.getNftRepository(application);
    }

    public LiveData<HomeUiState> getUiState() {
        return uiState;
    }

    public void refresh() {
        AppExecutors.io().execute(() -> {
            try {
                WalletSnapshot snapshot = walletRepository.loadWalletSnapshot();
                List<CandleEntry> chartEntries = priceRepository.getSevenDayCandleEntries(walletRepository.getSelectedNetwork());
                List<TokenBalance> tokenBalances = walletRepository.loadTokenBalances();
                List<TokenItem> tokenItems = buildAssetList(snapshot, tokenBalances);
                List<NftItem> nftItems = buildNftList(safeLoadNfts(snapshot.getAddress()));

                BigDecimal totalIdr = calculateTotalValue(tokenBalances, snapshot, true);
                BigDecimal totalUsd = calculateTotalValue(tokenBalances, snapshot, false);

                uiState.postValue(new HomeUiState(
                        snapshot.getAddress(),
                        shortenAddress(snapshot.getAddress()),
                        snapshot.getNetworkName(),
                        snapshot.getNativeAssetName(),
                        snapshot.getNativeAssetSymbol(),
                        FormatUtils.formatUsd(totalUsd),
                        FormatUtils.formatIdr(totalIdr),
                        FormatUtils.formatToken(snapshot.getNativeBalance(), snapshot.getNativeAssetSymbol()),
                        getApplication().getString(R.string.native_chart_title, snapshot.getNativeAssetSymbol()),
                        getApplication().getString(R.string.native_chart_subtitle, snapshot.getNativeAssetSymbol()),
                        tokenItems,
                        nftItems,
                        chartEntries,
                        null
                ));
            } catch (Exception exception) {
                String networkName = walletRepository.getSelectedNetwork().getDisplayName();
                String nativeAssetName = walletRepository.getSelectedNetwork().getNativeAssetName();
                String nativeAssetSymbol = walletRepository.getSelectedNetwork().getNativeSymbol();
                uiState.postValue(new HomeUiState(
                        "", "", networkName,
                        nativeAssetName,
                        nativeAssetSymbol,
                        FormatUtils.formatToken(BigDecimal.ZERO, nativeAssetSymbol),
                        FormatUtils.formatIdr(BigDecimal.ZERO),
                        FormatUtils.formatUsd(BigDecimal.ZERO),
                        getApplication().getString(R.string.native_chart_title, nativeAssetSymbol),
                        getApplication().getString(R.string.native_chart_subtitle, nativeAssetSymbol),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        new ArrayList<>(),
                        exception.getMessage()
                ));
            }
        });
    }

    private List<TokenItem> buildAssetList(WalletSnapshot snapshot, List<TokenBalance> tokenBalances) {
        if (snapshot.getAddress() == null || snapshot.getAddress().isEmpty()) {
            return Collections.emptyList();
        }

        List<TokenItem> items = new ArrayList<>();
        if (tokenBalances == null || tokenBalances.isEmpty()) {
            BigDecimal assetIdr = FormatUtils.safeMultiply(snapshot.getNativeBalance(), snapshot.getNativePriceIdr());
            items.add(new TokenItem(
                    snapshot.getNativeAssetSymbol(),
                    snapshot.getNetworkName(),
                    snapshot.getNativeAssetName(),
                    FormatUtils.formatToken(snapshot.getNativeBalance(), snapshot.getNativeAssetSymbol()),
                    FormatUtils.formatIdr(assetIdr),
                    buildUnitPriceLabel(snapshot.getNativeAssetSymbol(), snapshot.getNativePriceUsd(), snapshot.getNativePriceIdr()),
                    null,
                    resolveTokenIconRes(snapshot.getNativeAssetSymbol()),
                    snapshot.getNativeBalance()
            ));
            return items;
        }

        for (TokenBalance token : tokenBalances) {
            BigDecimal tokenTotalIdr = FormatUtils.safeMultiply(token.balance, token.unitPriceIdr);
            String fiatValue = token.unitPriceIdr.compareTo(BigDecimal.ZERO) > 0
                    ? FormatUtils.formatIdr(tokenTotalIdr)
                    : getApplication().getString(R.string.token_value_unavailable);
            items.add(new TokenItem(
                    token.symbol,
                    token.networkName,
                    token.name,
                    FormatUtils.formatToken(token.balance, token.symbol),
                    fiatValue,
                    token.unitPriceLabel,
                    token.imageUrl,
                    resolveTokenIconRes(token.symbol),
                    token.balance
            ));
        }
        return items;
    }

    private BigDecimal calculateTotalValue(List<TokenBalance> tokenBalances, WalletSnapshot snapshot, boolean idr) {
        BigDecimal total = BigDecimal.ZERO;
        if (tokenBalances != null && !tokenBalances.isEmpty()) {
            for (TokenBalance token : tokenBalances) {
                BigDecimal unitPrice = idr ? token.unitPriceIdr : token.unitPriceUsd;
                total = total.add(FormatUtils.safeMultiply(token.balance, unitPrice));
            }
            return total;
        }
        return FormatUtils.safeMultiply(
                snapshot.getNativeBalance(),
                idr ? snapshot.getNativePriceIdr() : snapshot.getNativePriceUsd()
        );
    }

    private String buildUnitPriceLabel(String symbol, BigDecimal unitPriceUsd, BigDecimal unitPriceIdr) {
        if (unitPriceUsd == null || unitPriceUsd.compareTo(BigDecimal.ZERO) <= 0
                || unitPriceIdr == null || unitPriceIdr.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return "1 " + symbol + " ≈ " + FormatUtils.formatUsd(unitPriceUsd)
                + " | " + FormatUtils.formatIdr(unitPriceIdr);
    }

    private List<NftAsset> safeLoadNfts(String walletAddress) {
        try {
            return nftRepository.loadOwnedNfts(walletAddress);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<NftItem> buildNftList(List<NftAsset> nftAssets) {
        if (nftAssets == null || nftAssets.isEmpty()) {
            return Collections.emptyList();
        }

        List<NftItem> items = new ArrayList<>();
        for (NftAsset nft : nftAssets) {
            String symbol = nft.tokenSymbol == null || nft.tokenSymbol.isEmpty() ? "NFT" : nft.tokenSymbol;
            if (symbol.length() > 4) {
                symbol = symbol.substring(0, 4);
            }
            items.add(new NftItem(
                    nft.collectionName,
                    getApplication().getString(R.string.nft_token_id, nft.tokenId),
                    nft.networkName,
                    shortenAddress(nft.contractAddress),
                    symbol,
                    nft.imageUrl
            ));
        }
        return items;
    }

    private int resolveTokenIconRes(String symbol) {
        if (symbol == null) {
            return 0;
        }
        if ("ETH".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_eth_real;
        }
        if ("MATS".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_mats;
        }
        if ("IDRX".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_idrx_real;
        }
        if ("USDT".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_usdt_real;
        }
        if ("USDC".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_usdc_real;
        }
        if ("DAI".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_dai_real;
        }
        if ("WBTC".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_wbtc_real;
        }
        if ("WETH".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_eth_real;
        }
        if ("LINK".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_link_real;
        }
        if ("UNI".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_uni_real;
        }
        if ("AAVE".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_aave_real;
        }
        if ("SHIB".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_shib_real;
        }
        if ("PEPE".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_pepe_real;
        }
        if ("ARB".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_arb_real;
        }
        if ("OP".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_op_real;
        }
        if ("BNB".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_bnb_real;
        }
        if ("AVAX".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_avax_real;
        }
        if ("POL".equalsIgnoreCase(symbol) || "MATIC".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_polygon_real;
        }
        if ("FTM".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_fantom;
        }
        return 0;
    }

    private String shortenAddress(String address) {
        if (address == null || address.length() < 12) {
            return address == null ? "" : address;
        }
        return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
    }
}
