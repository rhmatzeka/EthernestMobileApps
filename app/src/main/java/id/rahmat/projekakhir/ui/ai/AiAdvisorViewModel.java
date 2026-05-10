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
    private final PriceRepository priceRepository;
    private final MutableLiveData<AiUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<String> answerState = new MutableLiveData<>();
    private AiUiState latestState;

    public AiAdvisorViewModel(@NonNull Application application) {
        super(application);
        newsRepository = ServiceLocator.getNewsRepository(application);
        priceRepository = ServiceLocator.getPriceRepository(application);
    }

    public LiveData<AiUiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getAnswerState() {
        return answerState;
    }

    public void load() {
        uiState.postValue(new AiUiState(
                true,
                getApplication().getString(R.string.ai_loading),
                getApplication().getString(R.string.ai_loading),
                getApplication().getString(R.string.ai_loading),
                "",
                new ArrayList<>(),
                new ArrayList<>(),
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
        answerState.postValue(generateAnswer(cleanQuestion, state));
    }

    private List<StableAssetSignal> buildStableSignals() {
        List<StableAssetSignal> signals = new ArrayList<>();
        signals.add(loadStableSignal("USDC", "USD Coin", "usd-coin"));
        signals.add(loadStableSignal("USDT", "Tether USD", "tether"));
        signals.add(loadStableSignal("DAI", "Dai", "dai"));
        signals.sort((left, right) -> left.deviationPercent.compareTo(right.deviationPercent));
        return signals;
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
        if (containsAny(lowerQuestion, "stabil", "aman", "pindah", "hold", "risiko", "perang", "konflik", "krisis")) {
            return getApplication().getString(
                    R.string.ai_answer_stability,
                    state.bestAsset,
                    state.riskLabel,
                    state.recommendationBody
            );
        }
        if (containsAny(lowerQuestion, "ringkas", "summary", "berita", "news", "jelaskan")) {
            return summarizeNews(state);
        }
        if (containsAny(lowerQuestion, "usdc", "usdt", "dai", "stablecoin", "depeg")) {
            return explainStablecoins(state);
        }
        List<NewsRepository.NewsItem> matches = findMatchingNews(lowerQuestion, state.newsItems);
        if (!matches.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            builder.append(getApplication().getString(R.string.ai_answer_news_intro)).append("\n\n");
            for (int i = 0; i < Math.min(2, matches.size()); i++) {
                NewsRepository.NewsItem item = matches.get(i);
                builder.append(i + 1)
                        .append(". ")
                        .append(item.title);
                if (!item.source.isEmpty()) {
                    builder.append(" (").append(item.source).append(")");
                }
                builder.append("\n");
            }
            builder.append("\n").append(getApplication().getString(R.string.ai_answer_news_footer));
            return builder.toString();
        }
        return getApplication().getString(
                R.string.ai_answer_general,
                state.bestAsset,
                state.riskLabel
        );
    }

    private String summarizeNews(AiUiState state) {
        if (state.newsItems.isEmpty()) {
            return getApplication().getString(R.string.ai_answer_no_news, state.bestAsset, state.riskLabel);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(getApplication().getString(R.string.ai_answer_summary_intro, state.riskLabel)).append("\n\n");
        for (int i = 0; i < Math.min(3, state.newsItems.size()); i++) {
            NewsRepository.NewsItem item = state.newsItems.get(i);
            builder.append(i + 1).append(". ").append(item.title).append("\n");
        }
        builder.append("\n").append(getApplication().getString(R.string.ai_answer_summary_footer, state.bestAsset));
        return builder.toString();
    }

    private String explainStablecoins(AiUiState state) {
        StringBuilder builder = new StringBuilder();
        builder.append(getApplication().getString(R.string.ai_answer_stablecoin_intro)).append("\n\n");
        for (StableAssetSignal signal : state.stableAssets) {
            builder.append("- ")
                    .append(signal.symbol)
                    .append(": $")
                    .append(signal.priceUsd.setScale(4, RoundingMode.HALF_UP).toPlainString())
                    .append(", deviasi ")
                    .append(signal.deviationPercent.toPlainString())
                    .append("%\n");
        }
        builder.append("\n").append(getApplication().getString(R.string.ai_answer_stablecoin_footer, state.bestAsset));
        return builder.toString();
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
