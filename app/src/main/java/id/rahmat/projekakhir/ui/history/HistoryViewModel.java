package id.rahmat.projekakhir.ui.history;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;

import id.rahmat.projekakhir.data.local.TransactionEntity;
import id.rahmat.projekakhir.data.repository.TransactionRepository;
import id.rahmat.projekakhir.data.repository.WalletRepository;
import id.rahmat.projekakhir.di.ServiceLocator;
import id.rahmat.projekakhir.utils.AppExecutors;
import id.rahmat.projekakhir.utils.AppPreferences;
import id.rahmat.projekakhir.wallet.EthereumNetwork;
import id.rahmat.projekakhir.wallet.EthereumNetworkRegistry;

public class HistoryViewModel extends AndroidViewModel {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final AppPreferences appPreferences;
    private final MutableLiveData<String> filter = new MutableLiveData<>("all");
    private final LiveData<List<TransactionItem>> transactions;

    public HistoryViewModel(@NonNull Application application) {
        super(application);
        walletRepository = ServiceLocator.getWalletRepository(application);
        transactionRepository = ServiceLocator.getTransactionRepository(application);
        appPreferences = new AppPreferences(application);

        transactions = Transformations.switchMap(filter, value -> {
            String address = walletRepository.getWalletAddress();
            LiveData<List<TransactionEntity>> source =
                    "sent".equals(value)
                            ? transactionRepository.observeByDirection(address, TransactionRepository.DIRECTION_SENT)
                            : "received".equals(value)
                            ? transactionRepository.observeByDirection(address, TransactionRepository.DIRECTION_RECEIVED)
                            : transactionRepository.observeAll(address);
            return Transformations.map(source, this::mapTransactions);
        });
    }

    public LiveData<List<TransactionItem>> getTransactions() {
        return transactions;
    }

    public void setFilter(String value) {
        filter.setValue(value);
    }

    public void refresh() {
        AppExecutors.io().execute(() -> {
            try {
                transactionRepository.syncFromEtherscan(walletRepository.getWalletAddress());
            } catch (Exception ignored) {
            }
        });
    }

    private static String formatTimestamp(long timestamp) {
        // e.g. "2 Nov 2026, 14:05" instead of Date.toString()'s "Mon Nov 02 14:05:00 GMT+07:00 2026".
        return new SimpleDateFormat("d MMM yyyy, HH:mm", new Locale("id", "ID")).format(new Date(timestamp));
    }

    private List<TransactionItem> mapTransactions(List<TransactionEntity> entities) {
        List<TransactionItem> items = new ArrayList<>();
        if (entities == null) {
            return items;
        }

        for (TransactionEntity entity : entities) {
            boolean outgoing = TransactionRepository.DIRECTION_SENT.equals(entity.direction);
            String prefix = outgoing ? "-" : "+";
            EthereumNetwork network = EthereumNetworkRegistry.resolve(entity.network, appPreferences);
            String nativeSymbol = network.getNativeSymbol();
            items.add(new TransactionItem(
                    entity.hash,
                    outgoing ? "Kirim ke " + shorten(entity.toAddress) : "Terima dari " + shorten(entity.fromAddress),
                    formatTimestamp(entity.timestamp),
                    prefix + entity.amountEth + " " + nativeSymbol,
                    entity.gasFeeEth + " " + nativeSymbol,
                    entity.status,
                    network.getKey(),
                    outgoing
            ));
        }
        return items;
    }

    private String shorten(String address) {
        if (address == null || address.length() < 10) {
            return address == null ? "" : address;
        }
        return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
    }
}
