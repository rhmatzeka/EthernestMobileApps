package id.rahmat.projekakhir.di;

import android.content.Context;

import id.rahmat.projekakhir.BuildConfig;
import id.rahmat.projekakhir.data.local.AppDatabase;
import id.rahmat.projekakhir.data.remote.CoinGeckoApi;
import id.rahmat.projekakhir.data.remote.EtherscanApi;
import id.rahmat.projekakhir.data.remote.GdeltApi;
import id.rahmat.projekakhir.data.remote.OpenAiApi;
import id.rahmat.projekakhir.data.repository.NewsRepository;
import id.rahmat.projekakhir.data.repository.OpenAiRepository;
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
    private static NewsRepository newsRepository;
    private static OpenAiRepository openAiRepository;
    private static EthereumService ethereumService;
    private static EtherscanApi etherscanApi;
    private static OkHttpClient baseHttpClient;

    private ServiceLocator() {
    }

    public static synchronized WalletRepository getWalletRepository(Context context) {
        if (walletRepository == null) {
            Context appContext = context.getApplicationContext();
            walletRepository = new WalletRepository(
                    new WalletManager(appContext),
                    getEthereumService(),
                    getPriceRepository(appContext),
                    new AppPreferences(appContext)
            );
        }
        return walletRepository;
    }

    public static synchronized TransactionRepository getTransactionRepository(Context context) {
        if (transactionRepository == null) {
            Context appContext = context.getApplicationContext();
            transactionRepository = new TransactionRepository(
                    AppDatabase.getInstance(appContext).transactionDao(),
                    getEtherscanApi(),
                    new AppPreferences(appContext)
            );
        }
        return transactionRepository;
    }

    public static synchronized PriceRepository getPriceRepository(Context context) {
        if (priceRepository == null) {
            priceRepository = new PriceRepository(
                    createRetrofit(BuildConfig.COINGECKO_BASE_URL).create(CoinGeckoApi.class)
            );
        }
        return priceRepository;
    }

    public static synchronized NftRepository getNftRepository(Context context) {
        if (nftRepository == null) {
            Context appContext = context.getApplicationContext();
            nftRepository = new NftRepository(
                    getEtherscanApi(),
                    new AppPreferences(appContext),
                    getEthereumService(),
                    getHttpClient()
            );
        }
        return nftRepository;
    }

    public static synchronized NewsRepository getNewsRepository(Context context) {
        if (newsRepository == null) {
            newsRepository = new NewsRepository(
                    createRetrofit("https://api.gdeltproject.org/").create(GdeltApi.class)
            );
        }
        return newsRepository;
    }

    public static synchronized OpenAiRepository getOpenAiRepository(Context context) {
        if (openAiRepository == null) {
            openAiRepository = new OpenAiRepository(
                    createRetrofit("https://api.openai.com/", 30).create(OpenAiApi.class)
            );
        }
        return openAiRepository;
    }

    private static synchronized EtherscanApi getEtherscanApi() {
        if (etherscanApi == null) {
            etherscanApi = createRetrofit(BuildConfig.ETHERSCAN_BASE_URL).create(EtherscanApi.class);
        }
        return etherscanApi;
    }

    public static synchronized EthereumService getEthereumService() {
        if (ethereumService == null) {
            ethereumService = new EthereumService(getHttpClient());
        }
        return ethereumService;
    }

    /**
     * One shared client for every API and RPC call, so they reuse a single
     * connection pool and thread pool instead of each opening their own.
     */
    public static synchronized OkHttpClient getHttpClient() {
        if (baseHttpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(6, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .writeTimeout(8, TimeUnit.SECONDS);
            if (BuildConfig.DEBUG) {
                // Release builds never log: request URLs carry API keys.
                HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
                loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
                builder.addInterceptor(loggingInterceptor);
            }
            baseHttpClient = builder.build();
        }
        return baseHttpClient;
    }

    private static Retrofit createRetrofit(String baseUrl) {
        return createRetrofit(baseUrl, 8);
    }

    private static Retrofit createRetrofit(String baseUrl, int readTimeoutSeconds) {
        OkHttpClient client = getHttpClient().newBuilder()
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .build();

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
