package id.rahmat.projekakhir.ui.market;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.data.repository.PriceRepository;
import id.rahmat.projekakhir.databinding.FragmentMarketBinding;
import id.rahmat.projekakhir.di.ServiceLocator;
import id.rahmat.projekakhir.ui.base.BaseFragment;
import id.rahmat.projekakhir.utils.AppExecutors;
import id.rahmat.projekakhir.utils.FormatUtils;

public class MarketFragment extends BaseFragment {

    private static final BigDecimal USD_TO_IDR_FALLBACK = new BigDecimal("15500");

    private static class MarketAsset {
        final String symbol;
        final String name;
        final String assetId;

        MarketAsset(String symbol, String name, String assetId) {
            this.symbol = symbol;
            this.name = name;
            this.assetId = assetId;
        }
    }

    private static final MarketAsset[] MARKET_ASSETS = new MarketAsset[]{
            new MarketAsset("ETH", "Ethereum Mainnet", "ethereum"),
            new MarketAsset("BNB", "BNB Smart Chain", "binancecoin"),
            new MarketAsset("AVAX", "Avalanche", "avalanche-2"),
            new MarketAsset("POL", "Polygon", "polygon-ecosystem-token"),
            new MarketAsset("FTM", "Fantom", "fantom"),
            new MarketAsset("USDT", "Tether USD", "tether"),
            new MarketAsset("USDC", "USD Coin", "usd-coin"),
            new MarketAsset("DAI", "Dai", "dai"),
            new MarketAsset("WBTC", "Wrapped Bitcoin", "wrapped-bitcoin"),
            new MarketAsset("LINK", "Chainlink", "chainlink"),
            new MarketAsset("UNI", "Uniswap", "uniswap"),
            new MarketAsset("AAVE", "Aave", "aave"),
            new MarketAsset("SHIB", "Shiba Inu", "shiba-inu"),
            new MarketAsset("PEPE", "Pepe", "pepe"),
            new MarketAsset("ARB", "Arbitrum", "arbitrum"),
            new MarketAsset("OP", "Optimism", "optimism")
    };

    private FragmentMarketBinding binding;
    private PriceRepository priceRepository;
    private final List<Integer> visibleAssetIndexes = new ArrayList<>();
    private int selectedAssetIndex = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMarketBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        priceRepository = ServiceLocator.getPriceRepository(requireContext());
        setupChart();
        setupSearch();
        renderMarketSelector();
        loadSelectedMarket();
    }

    private void setupChart() {
        binding.chartMarketPrice.setDrawGridBackground(false);
        binding.chartMarketPrice.setTouchEnabled(true);
        binding.chartMarketPrice.setDragEnabled(true);
        binding.chartMarketPrice.setScaleEnabled(false);
        binding.chartMarketPrice.setPinchZoom(false);
        binding.chartMarketPrice.getAxisRight().setEnabled(false);
        binding.chartMarketPrice.getLegend().setForm(Legend.LegendForm.NONE);
        binding.chartMarketPrice.getLegend().setEnabled(false);
        binding.chartMarketPrice.setNoDataText(getString(R.string.market_chart_empty));

        Description description = new Description();
        description.setText("");
        binding.chartMarketPrice.setDescription(description);

        XAxis xAxis = binding.chartMarketPrice.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#B0B0B0"));
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(false);
        xAxis.setGranularity(1f);
        xAxis.setAxisLineColor(Color.TRANSPARENT);

        YAxis leftAxis = binding.chartMarketPrice.getAxisLeft();
        leftAxis.setTextColor(Color.parseColor("#B0B0B0"));
        leftAxis.setGridColor(Color.parseColor("#1FFFFFFF"));
        leftAxis.setAxisLineColor(Color.TRANSPARENT);
    }

    private void setupSearch() {
        binding.inputMarketSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                renderMarketSelector();
                if (visibleAssetIndexes.size() == 1) {
                    int onlyIndex = visibleAssetIndexes.get(0);
                    if (onlyIndex != selectedAssetIndex) {
                        selectedAssetIndex = onlyIndex;
                        renderMarketSelector();
                        loadSelectedMarket();
                    }
                }
            }
        });
    }

    private void renderMarketSelector() {
        binding.layoutMarketSelector.removeAllViews();
        visibleAssetIndexes.clear();
        String query = getSearchQuery();
        for (int i = 0; i < MARKET_ASSETS.length; i++) {
            if (!matchesQuery(MARKET_ASSETS[i], query)) {
                continue;
            }
            visibleAssetIndexes.add(i);
            final int index = i;
            TextView chip = new TextView(requireContext());
            chip.setText(MARKET_ASSETS[i].symbol);
            chip.setTextSize(12f);
            chip.setTextColor(androidx.core.content.ContextCompat.getColor(
                    requireContext(),
                    index == selectedAssetIndex ? R.color.black : R.color.mw_text_secondary
            ));
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setMinWidth(dp(58));
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setBackgroundResource(index == selectedAssetIndex
                    ? R.drawable.bg_pill_accent
                    : R.drawable.bg_section_pill);
            chip.setOnClickListener(v -> {
                selectedAssetIndex = index;
                renderMarketSelector();
                loadSelectedMarket();
            });

            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(dp(8));
            binding.layoutMarketSelector.addView(chip, params);
        }
        boolean hasResults = !visibleAssetIndexes.isEmpty();
        binding.marketSelectorScroll.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        binding.textMarketSearchEmpty.setVisibility(hasResults ? View.GONE : View.VISIBLE);
    }

    private String getSearchQuery() {
        if (binding.inputMarketSearch.getText() == null) {
            return "";
        }
        return binding.inputMarketSearch.getText().toString().trim().toLowerCase(Locale.US);
    }

    private boolean matchesQuery(MarketAsset asset, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return asset.symbol.toLowerCase(Locale.US).contains(query)
                || asset.name.toLowerCase(Locale.US).contains(query)
                || asset.assetId.toLowerCase(Locale.US).contains(query);
    }

    private void loadSelectedMarket() {
        MarketAsset asset = MARKET_ASSETS[selectedAssetIndex];
        binding.textMarketNetwork.setText(asset.name);
        binding.textMarketPriceLabel.setText(getString(R.string.market_price_label) + " " + asset.symbol);
        binding.textMarketChartTitle.setText(getString(R.string.native_chart_title, asset.symbol));
        binding.textMarketPrice.setText(getString(R.string.native_chart_loading));
        binding.textMarketPriceUsd.setText(asset.name);
        binding.chartMarketPrice.clear();
        binding.chartMarketPrice.setNoDataText(getString(R.string.native_chart_loading));
        binding.chartMarketPrice.invalidate();

        AppExecutors.io().execute(() -> {
            PriceRepository.PriceSnapshot price;
            List<CandleEntry> entries;
            try {
                price = priceRepository.getLatestPrice(asset.assetId);
                entries = priceRepository.getSevenDayCandleEntries(asset.assetId);
                price = withChartFallback(price, entries);
            } catch (Exception exception) {
                price = new PriceRepository.PriceSnapshot(BigDecimal.ZERO, BigDecimal.ZERO);
                entries = new java.util.ArrayList<>();
            }
            PriceRepository.PriceSnapshot finalPrice = price;
            List<CandleEntry> finalEntries = entries;
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (binding == null || selectedAssetIndex >= MARKET_ASSETS.length
                        || !MARKET_ASSETS[selectedAssetIndex].assetId.equals(asset.assetId)) {
                    return;
                }
                renderMarket(asset, finalPrice, finalEntries);
            });
        });
    }

    private PriceRepository.PriceSnapshot withChartFallback(PriceRepository.PriceSnapshot price,
                                                           List<CandleEntry> entries) {
        if (price != null && price.usd.compareTo(BigDecimal.ZERO) > 0
                && price.idr.compareTo(BigDecimal.ZERO) > 0) {
            return price;
        }
        if (entries == null || entries.isEmpty()) {
            return price == null
                    ? new PriceRepository.PriceSnapshot(BigDecimal.ZERO, BigDecimal.ZERO)
                    : price;
        }
        CandleEntry last = entries.get(entries.size() - 1);
        BigDecimal usd = BigDecimal.valueOf(last.getClose());
        return new PriceRepository.PriceSnapshot(usd, usd.multiply(USD_TO_IDR_FALLBACK));
    }

    private void renderMarket(MarketAsset asset, PriceRepository.PriceSnapshot price,
                              List<CandleEntry> entries) {
        if (binding == null) {
            return;
        }

        binding.textMarketNetwork.setText(asset.name);
        binding.textMarketPriceLabel.setText(getString(R.string.market_price_label) + " " + asset.symbol);
        binding.textMarketChartTitle.setText(getString(R.string.native_chart_title, asset.symbol));
        binding.textMarketPrice.setText(FormatUtils.formatIdr(price.idr));
        binding.textMarketPriceUsd.setText(FormatUtils.formatUsd(price.usd));

        if (entries == null || entries.isEmpty()) {
            binding.chartMarketPrice.clear();
            binding.chartMarketPrice.setNoDataText(getString(R.string.market_chart_empty));
            binding.chartMarketPrice.invalidate();
            return;
        }

        CandleDataSet dataSet = new CandleDataSet(entries, asset.symbol);
        dataSet.setShadowColorSameAsCandle(true);
        dataSet.setIncreasingColor(Color.parseColor("#C3F53C"));
        dataSet.setIncreasingPaintStyle(android.graphics.Paint.Style.FILL);
        dataSet.setDecreasingColor(Color.parseColor("#FF4D6A"));
        dataSet.setDecreasingPaintStyle(android.graphics.Paint.Style.FILL);
        dataSet.setNeutralColor(Color.parseColor("#8A8A90"));
        dataSet.setShadowWidth(0.9f);
        dataSet.setBarSpace(0.26f);
        dataSet.setDrawValues(false);
        binding.chartMarketPrice.setData(new CandleData(dataSet));
        binding.chartMarketPrice.animateY(450);
        binding.chartMarketPrice.invalidate();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
