package id.rahmat.projekakhir.data.repository;

import com.github.mikephil.charting.data.CandleEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import id.rahmat.projekakhir.data.remote.CoinGeckoApi;
import id.rahmat.projekakhir.wallet.EthereumNetwork;
import retrofit2.Response;

public class PriceRepository {

    private static final BigDecimal USD_TO_IDR_FALLBACK = new BigDecimal("15500");
    /** Prices younger than this are served from memory instead of the network. */
    private static final long PRICE_TTL_MS = 60_000L;

    public static class PriceSnapshot {
        public final BigDecimal usd;
        public final BigDecimal idr;

        public PriceSnapshot(BigDecimal usd, BigDecimal idr) {
            this.usd = usd;
            this.idr = idr;
        }
    }

    private final CoinGeckoApi coinGeckoApi;
    // Used from several background threads at once.
    private final Map<String, PriceSnapshot> lastSnapshots = new ConcurrentHashMap<>();
    private final Map<String, Long> snapshotTimes = new ConcurrentHashMap<>();
    private final Map<String, List<CandleEntry>> chartCache = new ConcurrentHashMap<>();

    public PriceRepository(CoinGeckoApi coinGeckoApi) {
        this.coinGeckoApi = coinGeckoApi;
    }

    public PriceSnapshot getLatestPrice(EthereumNetwork network) throws IOException {
        String assetId = network.getCoinGeckoAssetId();
        if (assetId == null || assetId.trim().isEmpty()) {
            return new PriceSnapshot(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return getLatestPrice(assetId);
    }

    public PriceSnapshot getLatestPrice(String assetId) throws IOException {
        if (!isFresh(assetId)) {
            fetchPrices(Collections.singleton(assetId));
        }
        return getLastSnapshot(assetId);
    }

    /**
     * Fetches every stale price in one CoinGecko request, so a screen that shows
     * many coins makes one call instead of one per coin (the free API is
     * rate-limited). Later getLatestPrice() calls are then served from memory.
     */
    public void prefetchPrices(Collection<String> assetIds) {
        Set<String> stale = new LinkedHashSet<>();
        for (String assetId : assetIds) {
            if (assetId != null && !assetId.trim().isEmpty() && !isFresh(assetId)) {
                stale.add(assetId.trim());
            }
        }
        if (!stale.isEmpty()) {
            fetchPrices(stale);
        }
    }

    static String joinIds(Collection<String> assetIds) {
        StringBuilder joined = new StringBuilder();
        for (String assetId : assetIds) {
            if (joined.length() > 0) {
                joined.append(',');
            }
            joined.append(assetId);
        }
        return joined.toString();
    }

    private boolean isFresh(String assetId) {
        Long fetchedAt = snapshotTimes.get(assetId);
        return fetchedAt != null && System.currentTimeMillis() - fetchedAt < PRICE_TTL_MS;
    }

    private void fetchPrices(Collection<String> assetIds) {
        Response<JsonObject> response;
        try {
            response = coinGeckoApi.getSimplePrice(joinIds(assetIds), "usd,idr").execute();
        } catch (IOException exception) {
            return;
        }
        if (response == null || !response.isSuccessful() || response.body() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String assetId : assetIds) {
            JsonElement element = response.body().get(assetId);
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject asset = element.getAsJsonObject();
            if (asset.get("usd") == null) {
                continue;
            }
            BigDecimal usd = asset.get("usd").getAsBigDecimal();
            BigDecimal idr = asset.get("idr") == null
                    ? usd.multiply(USD_TO_IDR_FALLBACK)
                    : asset.get("idr").getAsBigDecimal();
            lastSnapshots.put(assetId, new PriceSnapshot(usd, idr));
            snapshotTimes.put(assetId, now);
        }
    }

    public List<CandleEntry> getSevenDayCandleEntries(EthereumNetwork network) throws IOException {
        return getSevenDayCandleEntries(network.getCoinGeckoAssetId());
    }

    public List<CandleEntry> getSevenDayCandleEntries(String assetId) throws IOException {
        if (assetId == null || assetId.trim().isEmpty()) {
            return new ArrayList<>();
        }
        Response<JsonArray> response;
        try {
            response = coinGeckoApi.getOhlc(assetId, "usd", 7).execute();
        } catch (IOException exception) {
            return getFallbackChart(assetId);
        }
        if (response == null || !response.isSuccessful()) {
            return getFallbackChart(assetId);
        }
        JsonArray candles = response.body();
        List<CandleEntry> entries = new ArrayList<>();
        if (candles == null) {
            return getFallbackChart(assetId);
        }

        for (int index = 0; index < candles.size(); index++) {
            JsonArray point = candles.get(index).getAsJsonArray();
            if (point.size() < 5) {
                continue;
            }
            float open = point.get(1).getAsFloat();
            float high = point.get(2).getAsFloat();
            float low = point.get(3).getAsFloat();
            float close = point.get(4).getAsFloat();
            entries.add(new CandleEntry(index, high, low, open, close));
        }
        if (!entries.isEmpty()) {
            chartCache.put(assetId, entries);
        } else {
            return getFallbackChart(assetId);
        }
        return entries;
    }

    private List<CandleEntry> getFallbackChart(String assetId) {
        List<CandleEntry> fallbackEntries = fetchMarketChartCandles(assetId);
        if (!fallbackEntries.isEmpty()) {
            chartCache.put(assetId, fallbackEntries);
            return fallbackEntries;
        }
        return getCachedChart(assetId);
    }

    private List<CandleEntry> fetchMarketChartCandles(String assetId) {
        try {
            Response<JsonObject> response = coinGeckoApi
                    .getMarketChart(assetId, "usd", 7, null)
                    .execute();
            if (response == null || !response.isSuccessful() || response.body() == null) {
                return new ArrayList<>();
            }
            JsonArray prices = response.body().getAsJsonArray("prices");
            if (prices == null || prices.size() == 0) {
                return new ArrayList<>();
            }
            return buildCandlesFromPrices(prices);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private List<CandleEntry> buildCandlesFromPrices(JsonArray prices) {
        List<CandleEntry> entries = new ArrayList<>();
        int bucketSize = Math.max(1, prices.size() / 14);

        for (int start = 0; start < prices.size(); start += bucketSize) {
            int end = Math.min(prices.size(), start + bucketSize);
            Float open = null;
            float high = Float.MIN_VALUE;
            float low = Float.MAX_VALUE;
            float close = 0f;

            for (int index = start; index < end; index++) {
                JsonArray point = prices.get(index).getAsJsonArray();
                if (point.size() < 2) {
                    continue;
                }
                float price = point.get(1).getAsFloat();
                if (open == null) {
                    open = price;
                }
                high = Math.max(high, price);
                low = Math.min(low, price);
                close = price;
            }

            if (open == null) {
                continue;
            }
            entries.add(new CandleEntry(entries.size(), high, low, open, close));
        }
        return entries;
    }

    private List<CandleEntry> getCachedChart(String assetId) {
        List<CandleEntry> cached = chartCache.get(assetId);
        return cached == null ? new ArrayList<>() : cached;
    }

    private PriceSnapshot getLastSnapshot(String assetId) {
        PriceSnapshot snapshot = lastSnapshots.get(assetId);
        if (snapshot != null) {
            return snapshot;
        }
        return new PriceSnapshot(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
