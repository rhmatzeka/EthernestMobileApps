package id.rahmat.projekakhir.ui.ai;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.data.repository.NewsRepository;
import id.rahmat.projekakhir.data.repository.OpenAiRepository;
import id.rahmat.projekakhir.data.repository.PriceRepository;
import id.rahmat.projekakhir.di.ServiceLocator;
import id.rahmat.projekakhir.utils.AppExecutors;

public class AiAdvisorViewModel extends AndroidViewModel {

    private static final BigDecimal ONE_USD = BigDecimal.ONE;

    public static class StableAssetSignal {
        public final String symbol;
        public final String name;
        public final String assetId;
        public final BigDecimal priceUsd;
        public final BigDecimal deviationPercent;

        StableAssetSignal(String symbol, String name, String assetId,
                          BigDecimal priceUsd, BigDecimal deviationPercent) {
            this.symbol = symbol;
            this.name = name;
            this.assetId = assetId;
            this.priceUsd = priceUsd;
            this.deviationPercent = deviationPercent;
        }
    }

    public static class AiUiState {
        public final boolean loading;
        public final String riskLabel;
        public final String recommendationTitle;
        public final String recommendationBody;
        public final String bestAsset;
        public final List<StableAssetSignal> stableAssets;
        public final List<NewsRepository.NewsItem> newsItems;
        public final String errorMessage;

        AiUiState(boolean loading, String riskLabel, String recommendationTitle,
                  String recommendationBody, String bestAsset,
                  List<StableAssetSignal> stableAssets,
                  List<NewsRepository.NewsItem> newsItems, String errorMessage) {
            this.loading = loading;
            this.riskLabel = riskLabel;
            this.recommendationTitle = recommendationTitle;
            this.recommendationBody = recommendationBody;
            this.bestAsset = bestAsset;
            this.stableAssets = stableAssets == null ? new ArrayList<>() : stableAssets;
            this.newsItems = newsItems == null ? new ArrayList<>() : newsItems;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }
    }

    private final NewsRepository newsRepository;
    private final OpenAiRepository openAiRepository;
    private final PriceRepository priceRepository;
    private final MutableLiveData<AiUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<String> answerState = new MutableLiveData<>();
    private AiUiState latestState;

    public AiAdvisorViewModel(@NonNull Application application) {
        super(application);
        newsRepository = ServiceLocator.getNewsRepository(application);
        openAiRepository = ServiceLocator.getOpenAiRepository(application);
        priceRepository = ServiceLocator.getPriceRepository(application);
    }

    public LiveData<AiUiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getAnswerState() {
        return answerState;
    }

    public void load() {
        latestState = buildInstantFallbackState();
        uiState.postValue(new AiUiState(
                true,
                getApplication().getString(R.string.ai_loading),
                getApplication().getString(R.string.ai_loading),
                getApplication().getString(R.string.ai_loading),
                latestState.bestAsset,
                latestState.stableAssets,
                latestState.newsItems,
                ""
        ));

        AppExecutors.io().execute(() -> {
            List<NewsRepository.NewsItem> newsItems = new ArrayList<>();
            String errorMessage = "";
            try {
                newsItems = newsRepository.getRiskNews();
            } catch (Exception exception) {
                errorMessage = getApplication().getString(R.string.ai_news_error);
            }

            List<StableAssetSignal> stableAssets = buildStableSignals();
            StableAssetSignal bestSignal = stableAssets.isEmpty() ? null : stableAssets.get(0);
            int riskScore = calculateRiskScore(newsItems);
            String riskLabel = riskScore >= 5
                    ? getApplication().getString(R.string.ai_risk_high)
                    : riskScore >= 2
                    ? getApplication().getString(R.string.ai_risk_medium)
                    : getApplication().getString(R.string.ai_risk_normal);

            String bestAsset = bestSignal == null ? "USDC" : bestSignal.symbol;
            String title = getApplication().getString(R.string.ai_recommendation_title, bestAsset);
            String body = buildRecommendationBody(bestSignal, riskScore, newsItems);
            latestState = new AiUiState(false, riskLabel, title, body, bestAsset, stableAssets, newsItems, errorMessage);
            uiState.postValue(latestState);
        });
    }

    public void ask(String question) {
        String cleanQuestion = question == null ? "" : question.trim();
        if (cleanQuestion.isEmpty()) {
            answerState.postValue(getApplication().getString(R.string.ai_question_empty));
            return;
        }
        AiUiState state = latestState;
        if (state == null) {
            answerState.postValue(getApplication().getString(R.string.ai_answer_wait));
            return;
        }
        answerState.postValue(getApplication().getString(R.string.ai_answer_thinking));
        AppExecutors.io().execute(() -> {
            String onlineAnswer = "";
            try {
                onlineAnswer = openAiRepository.ask(cleanQuestion, buildAiContext(state));
            } catch (Exception ignored) {
                onlineAnswer = "";
            }
            if (onlineAnswer != null && !onlineAnswer.trim().isEmpty()) {
                answerState.postValue(onlineAnswer.trim());
                return;
            }
            answerState.postValue(generateAnswer(cleanQuestion, state));
        });
    }

    private List<StableAssetSignal> buildStableSignals() {
        List<StableAssetSignal> signals = new ArrayList<>();
        signals.add(loadStableSignal("USDC", "USD Coin", "usd-coin"));
        signals.add(loadStableSignal("USDT", "Tether USD", "tether"));
        signals.add(loadStableSignal("DAI", "Dai", "dai"));
        signals.sort((left, right) -> left.deviationPercent.compareTo(right.deviationPercent));
        return signals;
    }

    private AiUiState buildInstantFallbackState() {
        List<StableAssetSignal> signals = new ArrayList<>();
        signals.add(new StableAssetSignal("USDC", "USD Coin", "usd-coin", ONE_USD, BigDecimal.ZERO));
        signals.add(new StableAssetSignal("USDT", "Tether USD", "tether", ONE_USD, BigDecimal.ZERO));
        signals.add(new StableAssetSignal("DAI", "Dai", "dai", ONE_USD, BigDecimal.ZERO));
        List<NewsRepository.NewsItem> fallbackNews = new ArrayList<>();
        fallbackNews.add(new NewsRepository.NewsItem(
                "Mode defensif dipakai saat berita konflik, inflasi, atau regulasi meningkat",
                "Ethernest AI",
                "",
                "Offline",
                "Stablecoin USD biasanya lebih rendah volatilitas daripada aset crypto besar, tetapi tetap punya risiko depeg dan issuer."
        ));
        return new AiUiState(
                false,
                getApplication().getString(R.string.ai_risk_medium),
                getApplication().getString(R.string.ai_recommendation_title, "USDC"),
                getApplication().getString(
                        R.string.ai_recommendation_body,
                        "USDC",
                        "0.000%",
                        getApplication().getString(R.string.ai_reason_medium),
                        getApplication().getString(R.string.ai_news_empty_context)
                ),
                "USDC",
                signals,
                fallbackNews,
                ""
        );
    }

    private StableAssetSignal loadStableSignal(String symbol, String name, String assetId) {
        BigDecimal priceUsd = ONE_USD;
        try {
            PriceRepository.PriceSnapshot snapshot = priceRepository.getLatestPrice(assetId);
            if (snapshot != null && snapshot.usd.compareTo(BigDecimal.ZERO) > 0) {
                priceUsd = snapshot.usd;
            }
        } catch (Exception ignored) {
            priceUsd = ONE_USD;
        }
        BigDecimal deviation = priceUsd.subtract(ONE_USD).abs()
                .multiply(new BigDecimal("100"))
                .setScale(3, RoundingMode.HALF_UP);
        return new StableAssetSignal(symbol, name, assetId, priceUsd, deviation);
    }

    private int calculateRiskScore(List<NewsRepository.NewsItem> newsItems) {
        int score = 0;
        String[] riskWords = new String[]{
                "war", "conflict", "attack", "inflation", "recession", "sanction",
                "regulation", "hack", "crash", "rate", "fed", "risk",
                "perang", "konflik", "inflasi", "resesi", "regulasi", "risiko"
        };
        for (NewsRepository.NewsItem item : newsItems) {
            String text = (item.title + " " + item.summary).toLowerCase(Locale.US);
            for (String word : riskWords) {
                if (text.contains(word)) {
                    score++;
                }
            }
        }
        return score;
    }

    private String buildRecommendationBody(StableAssetSignal bestSignal, int riskScore,
                                           List<NewsRepository.NewsItem> newsItems) {
        String best = bestSignal == null ? "USDC" : bestSignal.symbol;
        String deviation = bestSignal == null ? "0.000%" : bestSignal.deviationPercent.toPlainString() + "%";
        String reason = riskScore >= 5
                ? getApplication().getString(R.string.ai_reason_high)
                : riskScore >= 2
                ? getApplication().getString(R.string.ai_reason_medium)
                : getApplication().getString(R.string.ai_reason_normal);
        String newsContext = newsItems.isEmpty()
                ? getApplication().getString(R.string.ai_news_empty_context)
                : getApplication().getString(R.string.ai_news_context, Math.min(newsItems.size(), 10));
        return getApplication().getString(R.string.ai_recommendation_body, best, deviation, reason, newsContext);
    }

    private String generateAnswer(String question, AiUiState state) {
        String lowerQuestion = question.toLowerCase(Locale.US);
        if (containsAny(lowerQuestion, "halo", "hallo", "hello", "hai", "hi", "pagi", "siang", "malam")) {
            return "Halo. Saya AI Ethernest. Kamu bisa tanya soal berita crypto, risiko market, stablecoin, swap, wallet, atau minta rekomendasi defensif saat kondisi tidak stabil.";
        }
        if (containsAny(lowerQuestion, "usdc", "usdt", "dai", "stablecoin", "depeg")) {
            return buildStablecoinAnswer(state, findRelevantNews(lowerQuestion, state.newsItems));
        }
        if (containsAny(lowerQuestion, "perang", "konflik", "krisis", "war", "conflict", "crisis", "attack")) {
            return buildCrisisAnswer(state, findRelevantNews(lowerQuestion, state.newsItems));
        }
        if (containsAny(lowerQuestion, "ringkas", "summary", "berita", "news", "jelaskan")) {
            return buildNewsSummaryAnswer(state, findRelevantNews(lowerQuestion, state.newsItems));
        }
        if (containsAny(lowerQuestion, "btc", "bitcoin", "eth", "ethereum", "altcoin", "crypto turun", "market turun")) {
            return buildVolatileAssetAnswer(state, findRelevantNews(lowerQuestion, state.newsItems));
        }
        if (containsAny(lowerQuestion, "stabil", "aman", "pindah", "hold", "risiko", "rekomendasi", "aset")) {
            return buildStabilityAnswer(state, findRelevantNews(lowerQuestion, state.newsItems));
        }
        List<NewsRepository.NewsItem> matches = findMatchingNews(lowerQuestion, state.newsItems);
        if (!matches.isEmpty()) {
            return buildNewsQuestionAnswer(state, matches);
        }
        return buildStabilityAnswer(state, findRelevantNews(lowerQuestion, state.newsItems));
    }

    private String buildAiContext(AiUiState state) {
        StringBuilder builder = new StringBuilder();
        builder.append("Status risiko: ").append(state.riskLabel).append("\n");
        builder.append("Aset stabil pilihan aplikasi: ").append(state.bestAsset).append("\n");
        builder.append("Rekomendasi awal: ").append(state.recommendationBody).append("\n\n");
        builder.append("Sinyal stablecoin:\n");
        for (StableAssetSignal signal : state.stableAssets) {
            builder.append("- ")
                    .append(signal.symbol)
                    .append(" (")
                    .append(signal.name)
                    .append("): price USD ")
                    .append(signal.priceUsd.setScale(4, RoundingMode.HALF_UP).toPlainString())
                    .append(", deviasi ")
                    .append(signal.deviationPercent.toPlainString())
                    .append("%\n");
        }
        builder.append("\nBerita/konteks yang sedang tampil:\n");
        for (int i = 0; i < Math.min(6, state.newsItems.size()); i++) {
            NewsRepository.NewsItem item = state.newsItems.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(item.title);
            if (!item.source.isEmpty()) {
                builder.append(" - ").append(item.source);
            }
            if (!item.summary.isEmpty()) {
                builder.append("\n   ").append(item.summary);
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private String buildCrisisAnswer(AiUiState state, List<NewsRepository.NewsItem> newsItems) {
        StringBuilder builder = new StringBuilder();
        builder.append("Jawaban singkat:\n");
        builder.append("Kalau ada perang/konflik besar, mode paling defensif adalah mengurangi aset volatil dan parkir sementara di ")
                .append(state.bestAsset)
                .append(" atau stablecoin USD lain.\n\n");
        builder.append("Kenapa:\n");
        builder.append("- BTC, ETH, dan altcoin bisa bergerak tajam saat pasar panik.\n");
        builder.append("- Stablecoin USD biasanya lebih stabil karena targetnya mengikuti $1.\n");
        builder.append("- Tetap cek risiko depeg, issuer, regulasi, dan likuiditas.\n\n");
        appendStablecoinSignals(builder, state);
        appendNewsContext(builder, newsItems);
        builder.append("\nLangkah defensif:\n");
        builder.append("1. Jangan pindahkan 100% aset sekaligus.\n");
        builder.append("2. Pecah risiko antara USDC/USDT/DAI kalau nominal besar.\n");
        builder.append("3. Masuk lagi ke aset volatil hanya setelah berita dan market lebih tenang.");
        return builder.toString();
    }

    private String buildStabilityAnswer(AiUiState state, List<NewsRepository.NewsItem> newsItems) {
        StringBuilder builder = new StringBuilder();
        builder.append("Jawaban singkat:\n");
        builder.append("AI memilih ").append(state.bestAsset).append(" sebagai kandidat paling stabil saat ini.\n\n");
        builder.append("Alasannya:\n");
        builder.append(state.recommendationBody).append("\n\n");
        appendStablecoinSignals(builder, state);
        appendNewsContext(builder, newsItems);
        builder.append("\nCatatan:\nIni bukan jaminan aman 100%. Stablecoin tetap bisa depeg atau terkena masalah issuer/regulasi.");
        return builder.toString();
    }

    private String buildStablecoinAnswer(AiUiState state, List<NewsRepository.NewsItem> newsItems) {
        StringBuilder builder = new StringBuilder();
        builder.append("Penjelasan stablecoin:\n");
        builder.append("USDC, USDT, dan DAI dibuat agar nilainya dekat $1, jadi biasanya lebih stabil daripada ETH/BTC saat market bergejolak.\n\n");
        appendStablecoinSignals(builder, state);
        builder.append("\nRisiko yang harus dipahami:\n");
        builder.append("- Depeg: harga bisa turun dari $1.\n");
        builder.append("- Issuer/cadangan: terutama untuk stablecoin terpusat.\n");
        builder.append("- Smart contract: terutama untuk token on-chain.\n");
        builder.append("- Regulasi dan likuiditas: bisa memengaruhi akses jual/beli.\n");
        appendNewsContext(builder, newsItems);
        return builder.toString();
    }

    private String buildNewsSummaryAnswer(AiUiState state, List<NewsRepository.NewsItem> newsItems) {
        StringBuilder builder = new StringBuilder();
        builder.append("Ringkasan AI:\n");
        builder.append("Status risiko: ").append(state.riskLabel).append(".\n\n");
        appendNewsContext(builder, newsItems);
        builder.append("\nKesimpulan awam:\n");
        builder.append("Kalau berita banyak membahas konflik, inflasi, regulasi, atau market crash, aset defensif seperti ")
                .append(state.bestAsset)
                .append(" lebih masuk akal untuk sementara daripada aset crypto yang volatil.");
        return builder.toString();
    }

    private String buildVolatileAssetAnswer(AiUiState state, List<NewsRepository.NewsItem> newsItems) {
        StringBuilder builder = new StringBuilder();
        builder.append("Tentang aset volatil:\n");
        builder.append("BTC/ETH bisa tetap kuat jangka panjang, tapi saat berita risiko membesar harganya dapat turun cepat karena investor mengurangi risiko.\n\n");
        builder.append("Untuk mode stabil, AI lebih memilih ").append(state.bestAsset).append(".\n\n");
        appendStablecoinSignals(builder, state);
        appendNewsContext(builder, newsItems);
        return builder.toString();
    }

    private String buildNewsQuestionAnswer(AiUiState state, List<NewsRepository.NewsItem> newsItems) {
        StringBuilder builder = new StringBuilder();
        builder.append("Berita yang paling nyambung:\n\n");
        appendNewsList(builder, newsItems);
        builder.append("\nMaknanya untuk wallet:\n");
        builder.append("Kalau berita ini meningkatkan ketidakpastian, jangan tambah posisi agresif dulu. Untuk menjaga nilai, kandidat defensif saat ini: ")
                .append(state.bestAsset)
                .append(".");
        return builder.toString();
    }

    private void appendStablecoinSignals(StringBuilder builder, AiUiState state) {
        builder.append("Sinyal stablecoin:\n");
        for (StableAssetSignal signal : state.stableAssets) {
            builder.append("- ")
                    .append(signal.symbol)
                    .append(": $")
                    .append(signal.priceUsd.setScale(4, RoundingMode.HALF_UP).toPlainString())
                    .append(", deviasi ")
                    .append(signal.deviationPercent.toPlainString())
                    .append("%\n");
        }
    }

    private void appendNewsContext(StringBuilder builder, List<NewsRepository.NewsItem> newsItems) {
        if (newsItems == null || newsItems.isEmpty()) {
            builder.append("\nBerita terkait:\nBelum ada berita yang cocok. AI memakai sinyal harga stablecoin dan risk brief lokal.\n");
            return;
        }
        builder.append("\nBerita terkait:\n");
        appendNewsList(builder, newsItems);
    }

    private void appendNewsList(StringBuilder builder, List<NewsRepository.NewsItem> newsItems) {
        for (int i = 0; i < Math.min(3, newsItems.size()); i++) {
            NewsRepository.NewsItem item = newsItems.get(i);
            builder.append(i + 1).append(". ").append(item.title);
            if (!item.source.isEmpty()) {
                builder.append(" (").append(item.source).append(")");
            }
            if (!item.summary.isEmpty() && !"Fallback".equalsIgnoreCase(item.publishedAt)) {
                builder.append("\n   ").append(item.summary);
            }
            builder.append("\n");
        }
    }

    private List<NewsRepository.NewsItem> findRelevantNews(String lowerQuestion,
                                                           List<NewsRepository.NewsItem> newsItems) {
        List<NewsRepository.NewsItem> matches = findMatchingNews(lowerQuestion, newsItems);
        if (!matches.isEmpty()) {
            return matches;
        }
        List<NewsRepository.NewsItem> fallback = new ArrayList<>();
        for (int i = 0; i < Math.min(3, newsItems.size()); i++) {
            fallback.add(newsItems.get(i));
        }
        return fallback;
    }

    private List<NewsRepository.NewsItem> findMatchingNews(String lowerQuestion,
                                                           List<NewsRepository.NewsItem> newsItems) {
        List<String> tokens = extractUsefulTokens(lowerQuestion);
        List<NewsRepository.NewsItem> matches = new ArrayList<>();
        for (NewsRepository.NewsItem item : newsItems) {
            String text = (item.title + " " + item.summary + " " + item.source).toLowerCase(Locale.US);
            for (String token : tokens) {
                if (text.contains(token)) {
                    matches.add(item);
                    break;
                }
            }
        }
        return matches;
    }

    private List<String> extractUsefulTokens(String value) {
        List<String> ignored = Arrays.asList("apa", "yang", "itu", "ini", "dan", "atau", "buat", "untuk", "kenapa", "gimana", "bagaimana", "kalau", "kalo", "saya");
        List<String> tokens = new ArrayList<>();
        for (String token : value.split("[^a-z0-9]+")) {
            if (token.length() < 4 || ignored.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private boolean containsAny(String value, String... words) {
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
