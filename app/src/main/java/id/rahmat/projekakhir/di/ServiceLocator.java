package id.rahmat.projekakhir.di;

import android.content.Context;

import id.rahmat.projekakhir.BuildConfig;
import id.rahmat.projekakhir.data.local.AppDatabase;
import id.rahmat.projekakhir.data.remote.CoinGeckoApi;
import id.rahmat.projekakhir.data.remote.EtherscanApi;
import id.rahmat.projekakhir.data.repository.PriceRepository;
import id.rahmat.projekakhir.data.repository.NftRepository;
import id.rahmat.projekakhir.data.repository.TransactionRepository;
import id.rahmat.projekakhir.data.repository.WalletRepository;
import id.rahmat.projekakhir.utils.AppPreferences;
import id.rahmat.projekakhir.wallet.EthereumService;
import id.rahmat.projekakhir.wallet.WalletManager;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

public final class ServiceLocator {

    private static WalletRepository walletRepository;
    private static TransactionRepository transactionRepository;
    private static PriceRepository priceRepository;
    private static NftRepository nftRepository;

    private ServiceLocator() {
    }

    public static WalletRepository getWalletRepository(Context context) {
        if (walletRepository == null) {
            Context appContext = context.getApplicationContext();
            walletRepository = new WalletRepository(
                    new WalletManager(appContext),
                    new EthereumService(),
                    getPriceRepository(appContext),
                    new AppPreferences(appContext)
            );
        }
        return walletRepository;
    }

    public static TransactionRepository getTransactionRepository(Context context) {
        if (transactionRepository == null) {
            Context appContext = context.getApplicationContext();
            transactionRepository = new TransactionRepository(
                    AppDatabase.getInstance(appContext).transactionDao(),
                    createRetrofit(BuildConfig.ETHERSCAN_MAINNET_BASE_URL).create(EtherscanApi.class),
                    createRetrofit(BuildConfig.ETHERSCAN_SEPOLIA_BASE_URL).create(EtherscanApi.class),
                    new AppPreferences(appContext)
            );
        }
        return transactionRepository;
    }

    public static PriceRepository getPriceRepository(Context context) {
        if (priceRepository == null) {
            priceRepository = new PriceRepository(
                    createRetrofit(BuildConfig.COINGECKO_BASE_URL).create(CoinGeckoApi.class)
            );
        }
        return priceRepository;
    }

    public static NftRepository getNftRepository(Context context) {
        if (nftRepository == null) {
            Context appContext = context.getApplicationContext();
            nftRepository = new NftRepository(
                    createRetrofit(BuildConfig.ETHERSCAN_MAINNET_BASE_URL).create(EtherscanApi.class),
                    createRetrofit(BuildConfig.ETHERSCAN_SEPOLIA_BASE_URL).create(EtherscanApi.class),
                    new AppPreferences(appContext),
                    new EthereumService()
            );
        }
        return nftRepository;
    }

    private static Retrofit createRetrofit(String baseUrl) {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build();

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
