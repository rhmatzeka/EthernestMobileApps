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
import id.rahmat.projekakhir.data.repository.WalletRepository;
import id.rahmat.projekakhir.di.ServiceLocator;
import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.utils.AppExecutors;
import id.rahmat.projekakhir.utils.FormatUtils;
import id.rahmat.projekakhir.wallet.TokenBalance;
import id.rahmat.projekakhir.wallet.WalletSnapshot;

public class HomeViewModel extends AndroidViewModel {

    public static class HomeUiState {
        public final String address;
        public final String shortAddress;
        public final String networkName;
        public final String balanceEth;
        public final String balanceIdr;
        public final String balanceUsd;
        public final List<TokenItem> assets;
        public final List<CandleEntry> chartEntries;
        public final String errorMessage;

        public HomeUiState(String address, String shortAddress, String networkName,
                           String balanceEth, String balanceIdr, String balanceUsd,
                           List<TokenItem> assets, List<CandleEntry> chartEntries, String errorMessage) {
            this.address = address;
            this.shortAddress = shortAddress;
            this.networkName = networkName;
            this.balanceEth = balanceEth;
            this.balanceIdr = balanceIdr;
            this.balanceUsd = balanceUsd;
            this.assets = assets;
            this.chartEntries = chartEntries;
            this.errorMessage = errorMessage;
        }
    }

    private final WalletRepository walletRepository;
    private final PriceRepository priceRepository;
    private final MutableLiveData<HomeUiState> uiState = new MutableLiveData<>();

    public HomeViewModel(@NonNull Application application) {
        super(application);
        walletRepository = ServiceLocator.getWalletRepository(application);
        priceRepository = ServiceLocator.getPriceRepository(application);
    }

    public LiveData<HomeUiState> getUiState() {
        return uiState;
    }

    public void refresh() {
        AppExecutors.io().execute(() -> {
            try {
                WalletSnapshot snapshot = walletRepository.loadWalletSnapshot();
                List<CandleEntry> chartEntries = priceRepository.getSevenDayCandleEntries();
                List<TokenBalance> tokenBalances = walletRepository.loadTokenBalances();
                List<TokenItem> tokenItems = buildAssetList(snapshot, tokenBalances);

                BigDecimal totalIdr = FormatUtils.safeMultiply(snapshot.getEthBalance(), snapshot.getEthPriceIdr());
                BigDecimal totalUsd = FormatUtils.safeMultiply(snapshot.getEthBalance(), snapshot.getEthPriceUsd());

                uiState.postValue(new HomeUiState(
                        snapshot.getAddress(),
                        shortenAddress(snapshot.getAddress()),
                        snapshot.getNetworkName(),
                        FormatUtils.formatEth(snapshot.getEthBalance()),
                        FormatUtils.formatIdr(totalIdr),
                        FormatUtils.formatUsd(totalUsd),
                        tokenItems,
                        chartEntries,
                        null
                ));
            } catch (Exception exception) {
                uiState.postValue(new HomeUiState(
                        "", "", walletRepository.getSelectedNetwork().getDisplayName(),
                        FormatUtils.formatEth(BigDecimal.ZERO),
                        FormatUtils.formatIdr(BigDecimal.ZERO),
                        FormatUtils.formatUsd(BigDecimal.ZERO),
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

        BigDecimal assetIdr = FormatUtils.safeMultiply(snapshot.getEthBalance(), snapshot.getEthPriceIdr());
        List<TokenItem> items = new ArrayList<>();
        items.add(new TokenItem(
                "Ethereum",
                snapshot.getNetworkName(),
                FormatUtils.formatEth(snapshot.getEthBalance()),
                FormatUtils.formatIdr(assetIdr),
                null,
                resolveTokenIconRes("ETH")
        ));

        if (tokenBalances != null) {
            for (TokenBalance token : tokenBalances) {
                BigDecimal tokenTotalIdr = FormatUtils.safeMultiply(token.balance, token.unitPriceIdr);
                String subtitle = snapshot.getNetworkName();
                String fiatValue = token.unitPriceIdr.compareTo(BigDecimal.ZERO) > 0
                        ? FormatUtils.formatIdr(tokenTotalIdr)
                        : getApplication().getString(R.string.token_value_unavailable);
                items.add(new TokenItem(
                        token.name,
                        subtitle,
                        FormatUtils.formatToken(token.balance, token.symbol),
                        fiatValue,
                        token.imageUrl,
                        resolveTokenIconRes(token.symbol)
                ));
            }
        }
        return items;
    }

    private int resolveTokenIconRes(String symbol) {
        if (symbol == null) {
            return 0;
        }
        if ("ETH".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_eth;
        }
        if ("MATS".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_mats;
        }
        if ("IDRX".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_idrx;
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
