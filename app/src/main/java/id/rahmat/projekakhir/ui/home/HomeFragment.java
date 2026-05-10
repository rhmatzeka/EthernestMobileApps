package id.rahmat.projekakhir.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.browser.customtabs.CustomTabsIntent;

import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import id.rahmat.projekakhir.MainActivity;
import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.databinding.FragmentHomeBinding;
import id.rahmat.projekakhir.ui.base.BaseFragment;
import id.rahmat.projekakhir.ui.receive.ReceiveActivity;
import id.rahmat.projekakhir.ui.send.SendActivity;
import id.rahmat.projekakhir.utils.AppPreferences;

public class HomeFragment extends BaseFragment {

    private static final int ASSET_TAB_CRYPTO = 0;
    private static final int ASSET_TAB_NFTS = 1;

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private TokenAdapter tokenAdapter;
    private NftAdapter nftAdapter;
    private AppPreferences appPreferences;
    private String currentWalletAddress = "";
    private int selectedAssetTab = ASSET_TAB_CRYPTO;
    private boolean balancesHidden = false;
    private boolean zeroBalanceTokensHidden = true;
    private HomeViewModel.HomeUiState currentState;
    private java.util.List<TokenItem> currentAssets = new java.util.ArrayList<>();
    private java.util.List<NftItem> currentNfts = new java.util.ArrayList<>();
    private java.util.List<HomeViewModel.ChartItem> currentChartItems = new java.util.ArrayList<>();
    private final java.util.Set<String> loadingChartIds = new java.util.HashSet<>();
    private final java.util.Set<String> loadedChartIds = new java.util.HashSet<>();
    private int selectedChartIndex = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        appPreferences = new AppPreferences(requireContext());
        balancesHidden = appPreferences.isBalanceHidden();
        zeroBalanceTokensHidden = appPreferences.isZeroBalanceTokensHidden();
        setupChart();
        setupAssetList();
        setupActions();
        setupAssetTabs();
        observeState();
        observeChartState();
        renderAssetFilterStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    private void setupActions() {
        binding.buttonSend.setOnClickListener(v -> startActivity(new Intent(requireContext(), SendActivity.class)));
        binding.buttonReceive.setOnClickListener(v -> startActivity(new Intent(requireContext(), ReceiveActivity.class)));
        binding.buttonQuickSwap.setOnClickListener(v -> openSwapTab());
        binding.buttonRefresh.setOnClickListener(v -> {
            binding.homeSwipeRefresh.setRefreshing(true);
            viewModel.refresh();
        });
        binding.buttonPromo.setOnClickListener(v -> {
            CustomTabsIntent intent = new CustomTabsIntent.Builder().build();
            intent.launchUrl(requireContext(), android.net.Uri.parse("https://ethereum.org/en/wallets/"));
        });
        binding.buttonCopyAddress.setOnClickListener(v -> copyWalletAddress());
        binding.buttonShowQr.setOnClickListener(v -> startActivity(new Intent(requireContext(), ReceiveActivity.class)));
        binding.homeSwipeRefresh.setOnRefreshListener(() -> binding.buttonRefresh.performClick());
        binding.buttonAssetHistory.setOnClickListener(v -> openHistoryTab());
        binding.buttonToggleBalance.setOnClickListener(v -> toggleBalanceVisibility());
        binding.buttonAssetFilter.setOnClickListener(v -> toggleTokenFilter());
    }

    private void copyWalletAddress() {
        if (currentWalletAddress == null || currentWalletAddress.isEmpty()) {
            showMessage(getString(R.string.wallet_not_ready));
            return;
        }
        ClipboardManager clipboardManager = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText("wallet_address", currentWalletAddress);
        clipboardManager.setPrimaryClip(clipData);
        showMessage(getString(R.string.copy_action));
    }

    private void setupAssetList() {
        binding.recyclerTokens.setLayoutManager(new LinearLayoutManager(requireContext()));
        tokenAdapter = new TokenAdapter(new java.util.ArrayList<>(), balancesHidden);
        binding.recyclerTokens.setAdapter(tokenAdapter);

        binding.recyclerNfts.setLayoutManager(new LinearLayoutManager(
                requireContext(),
                RecyclerView.HORIZONTAL,
                false
        ));
        nftAdapter = new NftAdapter(new java.util.ArrayList<>());
        binding.recyclerNfts.setAdapter(nftAdapter);
    }

    private void setupAssetTabs() {
        binding.tabCrypto.setOnClickListener(v -> selectAssetTab(ASSET_TAB_CRYPTO));
        binding.tabNfts.setOnClickListener(v -> selectAssetTab(ASSET_TAB_NFTS));
        selectAssetTab(ASSET_TAB_CRYPTO);
    }

    private void selectAssetTab(int tab) {
        selectedAssetTab = tab;
        if (binding == null) {
            return;
        }
        renderAssetTabs();
        renderAssetContent();
    }

    private void renderAssetTabs() {
        boolean cryptoSelected = selectedAssetTab == ASSET_TAB_CRYPTO;
        boolean nftsSelected = selectedAssetTab == ASSET_TAB_NFTS;

        binding.textTabCrypto.setTextColor(androidx.core.content.ContextCompat.getColor(
                requireContext(),
                cryptoSelected ? R.color.black : R.color.mw_text_secondary));
        binding.textTabNfts.setTextColor(androidx.core.content.ContextCompat.getColor(
                requireContext(),
                nftsSelected ? R.color.black : R.color.mw_text_secondary));

        binding.textTabCrypto.setBackgroundResource(
                cryptoSelected ? R.drawable.bg_pill_accent : android.R.color.transparent);
        binding.textTabNfts.setBackgroundResource(
                nftsSelected ? R.drawable.bg_pill_accent : android.R.color.transparent);
    }

    private void renderAssetContent() {
        if (binding == null) {
            return;
        }

        boolean showCrypto = selectedAssetTab == ASSET_TAB_CRYPTO;
        boolean showNfts = selectedAssetTab == ASSET_TAB_NFTS;
        boolean hasNfts = currentNfts != null && !currentNfts.isEmpty();

        binding.recyclerTokens.setVisibility(showCrypto ? View.VISIBLE : View.GONE);
        binding.recyclerNfts.setVisibility(showNfts && hasNfts ? View.VISIBLE : View.GONE);
        binding.layoutNftEmpty.setVisibility(showNfts && !hasNfts ? View.VISIBLE : View.GONE);
    }

    private void toggleBalanceVisibility() {
        balancesHidden = !balancesHidden;
        if (appPreferences != null) {
            appPreferences.setBalanceHidden(balancesHidden);
        }
        renderBalances();
        renderTokenList();
    }

    private void toggleTokenFilter() {
        zeroBalanceTokensHidden = !zeroBalanceTokensHidden;
        if (appPreferences != null) {
            appPreferences.setZeroBalanceTokensHidden(zeroBalanceTokensHidden);
        }
        renderTokenList();
        renderAssetFilterStatus();
    }

    private void openHistoryTab() {
        BottomNavigationView bottomNavigation = requireActivity().findViewById(R.id.bottomNavigation);
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.menu_history);
        }
    }

    private void openSwapTab() {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).openSwap();
        }
    }

    private void setupChart() {
        binding.chartEthPrice.setDrawGridBackground(false);
        binding.chartEthPrice.setTouchEnabled(true);
        binding.chartEthPrice.setDragEnabled(true);
        binding.chartEthPrice.setScaleEnabled(false);
        binding.chartEthPrice.setPinchZoom(false);
        binding.chartEthPrice.getAxisRight().setEnabled(false);
        binding.chartEthPrice.getLegend().setForm(Legend.LegendForm.NONE);
        binding.chartEthPrice.getLegend().setEnabled(false);
        binding.chartEthPrice.setNoDataText(getString(R.string.native_chart_loading));
        binding.chartEthPrice.setMaxVisibleValueCount(16);

        Description description = new Description();
        description.setText("");
        binding.chartEthPrice.setDescription(description);

        XAxis xAxis = binding.chartEthPrice.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#B0B0B0"));
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(false);
        xAxis.setGranularity(1f);
        xAxis.setAxisLineColor(Color.TRANSPARENT);

        YAxis leftAxis = binding.chartEthPrice.getAxisLeft();
        leftAxis.setTextColor(Color.parseColor("#B0B0B0"));
        leftAxis.setGridColor(Color.parseColor("#1FFFFFFF"));
        leftAxis.setAxisLineColor(Color.TRANSPARENT);

        binding.chartEthPrice.invalidate();
    }

    private void observeState() {
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            binding.homeSwipeRefresh.setRefreshing(false);
            currentState = state;
            currentWalletAddress = state.address;
            binding.textWalletAddress.setText(state.shortAddress);
            renderBalances();
            binding.textNetworkBadge.setText(state.networkName);
            binding.imageNetworkBadgeIcon.setImageResource(resolveNetworkIcon(state.nativeAssetSymbol));
            currentChartItems = state.chartItems == null
                    ? new java.util.ArrayList<>()
                    : state.chartItems;
            loadingChartIds.clear();
            loadedChartIds.clear();
            selectedChartIndex = 0;
            renderChartSelector();
            renderSelectedChart();

            currentAssets = state.assets == null ? new java.util.ArrayList<>() : state.assets;
            renderTokenList();

            nftAdapter = new NftAdapter(state.nfts);
            binding.recyclerNfts.setAdapter(nftAdapter);
            currentNfts = state.nfts == null ? new java.util.ArrayList<>() : state.nfts;
            renderAssetContent();
            renderAssetFilterStatus();

            if (state.errorMessage != null && !state.errorMessage.isEmpty()) {
                showMessage(state.errorMessage);
            }
        });
    }

    private void observeChartState() {
        viewModel.getChartState().observe(getViewLifecycleOwner(), chartItem -> {
            if (chartItem == null || binding == null) {
                return;
            }
            loadingChartIds.remove(chartItem.assetId);
            loadedChartIds.add(chartItem.assetId);
            for (int i = 0; i < currentChartItems.size(); i++) {
                HomeViewModel.ChartItem existing = currentChartItems.get(i);
                if (existing.assetId.equals(chartItem.assetId)) {
                    currentChartItems.set(i, chartItem);
                    if (i == selectedChartIndex) {
                        renderSelectedChart();
                    }
                    return;
                }
            }
        });
    }

    private void renderChartSelector() {
        if (binding == null) {
            return;
        }
        binding.layoutChartSelector.removeAllViews();
        binding.chartSelectorScroll.setVisibility(currentChartItems.size() > 1 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < currentChartItems.size(); i++) {
            final int index = i;
            TextView chip = new TextView(requireContext());
            chip.setText(currentChartItems.get(i).symbol);
            chip.setTextSize(12f);
            chip.setTextColor(androidx.core.content.ContextCompat.getColor(
                    requireContext(),
                    index == selectedChartIndex ? R.color.black : R.color.mw_text_secondary
            ));
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setMinWidth(dp(58));
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setBackgroundResource(index == selectedChartIndex
                    ? R.drawable.bg_pill_accent
                    : R.drawable.bg_section_pill);
            chip.setOnClickListener(v -> {
                selectedChartIndex = index;
                renderChartSelector();
                renderSelectedChart();
            });

            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(dp(8));
            binding.layoutChartSelector.addView(chip, params);
        }
    }

    private void renderSelectedChart() {
        if (binding == null) {
            return;
        }
        if (currentChartItems.isEmpty()) {
            binding.textChartTitle.setText(currentState == null ? getString(R.string.native_chart_loading) : currentState.chartTitle);
            binding.textChartSubtitle.setText(getString(R.string.native_chart_loading));
            binding.chartEthPrice.clear();
            binding.chartEthPrice.setNoDataText(getString(R.string.native_chart_loading));
            binding.chartEthPrice.invalidate();
            return;
        }

        int safeIndex = Math.min(selectedChartIndex, currentChartItems.size() - 1);
        HomeViewModel.ChartItem chartItem = currentChartItems.get(safeIndex);
        binding.textChartTitle.setText(chartItem.title);
        binding.textChartSubtitle.setText(chartItem.subtitle);

        if (chartItem.entries == null || chartItem.entries.isEmpty()) {
            binding.chartEthPrice.clear();
            binding.chartEthPrice.setNoDataText(getString(
                    loadedChartIds.contains(chartItem.assetId)
                            ? R.string.native_chart_unavailable
                            : R.string.native_chart_loading
            ));
            binding.chartEthPrice.invalidate();
            if (!loadingChartIds.contains(chartItem.assetId) && !loadedChartIds.contains(chartItem.assetId)) {
                loadingChartIds.add(chartItem.assetId);
                viewModel.loadChart(chartItem);
            }
            return;
        }

        CandleDataSet dataSet = new CandleDataSet(chartItem.entries, chartItem.symbol);
        dataSet.setShadowColorSameAsCandle(true);
        dataSet.setIncreasingColor(Color.parseColor("#C3F53C"));
        dataSet.setIncreasingPaintStyle(android.graphics.Paint.Style.FILL);
        dataSet.setDecreasingColor(Color.parseColor("#FF4D6A"));
        dataSet.setDecreasingPaintStyle(android.graphics.Paint.Style.FILL);
        dataSet.setNeutralColor(Color.parseColor("#8A8A90"));
        dataSet.setShadowWidth(0.9f);
        dataSet.setBarSpace(0.26f);
        dataSet.setDrawValues(false);
        binding.chartEthPrice.setData(new CandleData(dataSet));
        binding.chartEthPrice.animateY(450);
        binding.chartEthPrice.invalidate();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void renderBalances() {
        if (binding == null || currentState == null) {
            return;
        }
        binding.buttonToggleBalance.setText(balancesHidden
                ? getString(R.string.balance_show_action)
                : getString(R.string.balance_hide_action));
        if (balancesHidden) {
            binding.textEthBalance.setText("****");
            binding.textFiatBalance.setText("****");
            return;
        }
        binding.textEthBalance.setText(currentState.balancePrimary);
        binding.textFiatBalance.setText(currentState.balanceIdr);
    }

    private int resolveNetworkIcon(String symbol) {
        if ("BNB".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_bnb_real;
        }
        if ("AVAX".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_avax_real;
        }
        if ("POL".equalsIgnoreCase(symbol) || "MATIC".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_polygon_real;
        }
        if ("FTM".equalsIgnoreCase(symbol)) {
            return R.drawable.ic_token_fantom;
        }
        return R.drawable.ic_token_eth_real;
    }

    private void renderTokenList() {
        if (binding == null) {
            return;
        }
        java.util.List<TokenItem> visibleAssets = new java.util.ArrayList<>();
        for (TokenItem item : currentAssets) {
            if (!zeroBalanceTokensHidden || item.hasPositiveBalance()) {
                visibleAssets.add(item);
            }
        }
        tokenAdapter = new TokenAdapter(visibleAssets, balancesHidden);
        binding.recyclerTokens.setAdapter(tokenAdapter);
    }

    private void renderAssetFilterStatus() {
        if (binding == null) {
            return;
        }
        binding.textAssetFilterStatus.setText(zeroBalanceTokensHidden
                ? getString(R.string.asset_filter_positive_only)
                : getString(R.string.asset_filter_show_all));
        binding.buttonAssetFilter.setContentDescription(zeroBalanceTokensHidden
                ? getString(R.string.asset_filter_show_all_action)
                : getString(R.string.asset_filter_positive_only_action));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
