package id.rahmat.projekakhir.ui.send;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.math.BigDecimal;

import id.rahmat.projekakhir.data.local.TransactionEntity;
import id.rahmat.projekakhir.data.repository.PriceRepository;
import id.rahmat.projekakhir.data.repository.TransactionRepository;
import id.rahmat.projekakhir.data.repository.WalletRepository;
import id.rahmat.projekakhir.di.ServiceLocator;
import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.utils.AppExecutors;
import id.rahmat.projekakhir.utils.FormatUtils;
import id.rahmat.projekakhir.wallet.EthereumNetwork;
import id.rahmat.projekakhir.wallet.EthereumService;
import id.rahmat.projekakhir.wallet.GasEstimation;
import id.rahmat.projekakhir.wallet.WalletSnapshot;
import org.web3j.crypto.Credentials;

public class SendViewModel extends AndroidViewModel {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final EthereumService ethereumService;
    private final PriceRepository priceRepository;
    private final MutableLiveData<GasEstimation> gasState = new MutableLiveData<>();
    private final MutableLiveData<String> fiatState = new MutableLiveData<>();
    private final MutableLiveData<String> sendResult = new MutableLiveData<>();
    private final MutableLiveData<String> maxAmountState = new MutableLiveData<>();

    public SendViewModel(@NonNull Application application) {
        super(application);
        walletRepository = ServiceLocator.getWalletRepository(application);
        transactionRepository = ServiceLocator.getTransactionRepository(application);
        priceRepository = ServiceLocator.getPriceRepository(application);
        ethereumService = new EthereumService();
    }

    public LiveData<GasEstimation> getGasState() {
        return gasState;
    }

    public LiveData<String> getFiatState() {
        return fiatState;
    }

    public LiveData<String> getSendResult() {
        return sendResult;
    }

    public LiveData<String> getMaxAmountState() {
        return maxAmountState;
    }

    public String getSelectedNetworkName() {
        return walletRepository.getSelectedNetwork().getDisplayName();
    }

    public String getSelectedNetworkSymbol() {
        return walletRepository.getSelectedNetwork().getNativeSymbol();
    }

    public int getSelectedNetworkIconRes() {
        EthereumNetwork network = walletRepository.getSelectedNetwork();
        if (EthereumNetwork.BSC.isSameNetwork(network)) {
            return R.drawable.ic_token_bnb_real;
        }
        if (EthereumNetwork.AVALANCHE.isSameNetwork(network)) {
            return R.drawable.ic_token_avax_real;
        }
        if ("POL".equalsIgnoreCase(network.getNativeSymbol()) || "MATIC".equalsIgnoreCase(network.getNativeSymbol())) {
            return R.drawable.ic_token_polygon_real;
        }
        if ("FTM".equalsIgnoreCase(network.getNativeSymbol())) {
            return R.drawable.ic_token_fantom;
        }
        return R.drawable.ic_token_eth_real;
    }

    public void estimateGas(String toAddress, String amountEth) {
        AppExecutors.io().execute(() -> {
            try {
                BigDecimal amount = new BigDecimal(amountEth);
                GasEstimation estimation = ethereumService.estimateNativeTransfer(
                        walletRepository.getWalletAddress(),
                        toAddress,
                        amount,
                        walletRepository.getSelectedNetwork()
                );
                gasState.postValue(estimation);
            } catch (Exception exception) {
                gasState.postValue(null);
            }
        });
    }

    public void estimateFiat(String amountEth) {
        AppExecutors.io().execute(() -> {
            try {
                BigDecimal amount = new BigDecimal(amountEth);
                PriceRepository.PriceSnapshot priceSnapshot = priceRepository.getLatestPrice(walletRepository.getSelectedNetwork());
                if (priceSnapshot == null
                        || BigDecimal.ZERO.compareTo(priceSnapshot.idr) == 0
                        || BigDecimal.ZERO.compareTo(priceSnapshot.usd) == 0) {
                    fiatState.postValue(getApplication().getString(R.string.fiat_unavailable));
                    return;
                }
                BigDecimal totalIdr = FormatUtils.safeMultiply(amount, priceSnapshot.idr);
                BigDecimal totalUsd = FormatUtils.safeMultiply(amount, priceSnapshot.usd);
                String result = FormatUtils.formatIdr(totalIdr) + " | " + FormatUtils.formatUsd(totalUsd);
                fiatState.postValue(result);
            } catch (Exception exception) {
                fiatState.postValue(getApplication().getString(R.string.fiat_unavailable));
            }
        });
    }

    public void send(String toAddress, String amountEth) {
        AppExecutors.io().execute(() -> {
            try {
                Credentials credentials = walletRepository.getWalletManager().getCredentials();
                String hash = ethereumService.sendNativeTransfer(
                        credentials,
                        toAddress,
                        new BigDecimal(amountEth),
                        walletRepository.getSelectedNetwork()
                );

                TransactionEntity entity = new TransactionEntity();
                entity.hash = hash;
                entity.walletAddress = walletRepository.getWalletAddress();
                entity.fromAddress = walletRepository.getWalletAddress();
                entity.toAddress = toAddress;
                entity.amountEth = amountEth;
                entity.gasFeeEth = gasState.getValue() != null ? gasState.getValue().getGasFeeEth().toPlainString() : "0";
                entity.timestamp = System.currentTimeMillis();
                entity.status = "Success";
                entity.direction = TransactionRepository.DIRECTION_SENT;
                entity.network = walletRepository.getSelectedNetwork().getKey();
                entity.source = "wallet";
                transactionRepository.insert(entity);
                sendResult.postValue(hash);
            } catch (Exception exception) {
                sendResult.postValue("");
            }
        });
    }

    public void loadMaxAmount() {
        AppExecutors.io().execute(() -> {
            try {
                WalletSnapshot snapshot = walletRepository.loadWalletSnapshot();
                BigDecimal reserveForGas = new BigDecimal("0.0005");
                BigDecimal maxAmount = snapshot.getEthBalance().subtract(reserveForGas);
                if (maxAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    maxAmountState.postValue("");
                    return;
                }
                maxAmountState.postValue(maxAmount.stripTrailingZeros().toPlainString());
            } catch (Exception exception) {
                maxAmountState.postValue("");
            }
        });
    }
}
