package id.rahmat.projekakhir.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import id.rahmat.projekakhir.data.remote.GdeltApi;
import retrofit2.Response;

public class NewsRepository {

    private static final String RISK_QUERY =
            "(crypto OR bitcoin OR ethereum OR stablecoin OR dollar OR inflation OR recession OR war OR conflict OR regulation)";

    public static class NewsItem {
        public final String title;
        public final String source;
        public final String url;
        public final String publishedAt;
        public final String summary;

        public NewsItem(String title, String source, String url, String publishedAt, String summary) {
            this.title = safe(title);
            this.source = safe(source);
            this.url = safe(url);
            this.publishedAt = safe(publishedAt);
            this.summary = safe(summary);
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private final GdeltApi gdeltApi;
    private List<NewsItem> cachedNews = buildFallbackNews();

    public NewsRepository(GdeltApi gdeltApi) {
        this.gdeltApi = gdeltApi;
    }

    public List<NewsItem> getRiskNews() throws IOException {
        Response<JsonObject> response = gdeltApi
                .searchNews(RISK_QUERY, "artlist", "json", 10, "hybridrel")
                .execute();
        if (response == null || !response.isSuccessful() || response.body() == null) {
            return cachedNews;
        }
        JsonArray articles = response.body().getAsJsonArray("articles");
        if (articles == null || articles.size() == 0) {
            return cachedNews;
        }

        List<NewsItem> items = new ArrayList<>();
        for (JsonElement element : articles) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject article = element.getAsJsonObject();
            String title = getString(article, "title");
            String url = getString(article, "url");
            if (title.isEmpty() || url.isEmpty()) {
                continue;
            }
            items.add(new NewsItem(
                    title,
                    firstNonEmpty(getString(article, "domain"), getString(article, "sourcecountry")),
                    url,
                    getString(article, "seendate"),
                    firstNonEmpty(getString(article, "snippet"), getString(article, "socialimage"))
            ));
        }
        if (!items.isEmpty()) {
            cachedNews = items;
        }
        return cachedNews;
    }

    private String getString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception exception) {
            return "";
        }
    }

    private String firstNonEmpty(String first, String second) {
        return first == null || first.trim().isEmpty() ? second : first;
    }

    private static List<NewsItem> buildFallbackNews() {
        List<NewsItem> items = new ArrayList<>();
        items.add(new NewsItem(
                "Pasar global biasanya mencari aset defensif saat konflik geopolitik meningkat",
                "Ethernest Risk Brief",
                "",
                "Fallback",
                "Saat berita perang atau konflik meningkat, aset crypto volatil seperti BTC dan ETH biasanya lebih berisiko daripada stablecoin USD."
        ));
        items.add(new NewsItem(
                "Stablecoin USD perlu dicek dari sisi depeg, issuer, likuiditas, dan regulasi",
                "Ethernest Risk Brief",
                "",
                "Fallback",
                "USDC, USDT, dan DAI bisa lebih stabil terhadap dolar, tetapi masing-masing tetap punya risiko operasional dan smart contract."
        ));
        items.add(new NewsItem(
                "Suku bunga, inflasi, dan regulasi dapat memicu volatilitas crypto",
                "Ethernest Risk Brief",
                "",
                "Fallback",
                "Berita makro seperti inflasi, keputusan bank sentral, dan aturan crypto dapat memengaruhi minat risiko investor."
        ));
        return items;
    }
}
