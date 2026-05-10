package id.rahmat.projekakhir.ui.ai;

import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.lifecycle.ViewModelProvider;

import java.math.RoundingMode;

import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.data.repository.NewsRepository;
import id.rahmat.projekakhir.databinding.FragmentAiAdvisorBinding;
import id.rahmat.projekakhir.ui.base.BaseFragment;

public class AiAdvisorFragment extends BaseFragment {

    private FragmentAiAdvisorBinding binding;
    private AiAdvisorViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAiAdvisorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AiAdvisorViewModel.class);
        binding.buttonRefreshAi.setOnClickListener(v -> viewModel.load());
        binding.buttonAskAi.setOnClickListener(v -> askCurrentQuestion());
        binding.chipQuestionWar.setOnClickListener(v -> askQuickQuestion(getString(R.string.ai_quick_war_question)));
        binding.chipQuestionSummary.setOnClickListener(v -> askQuickQuestion(getString(R.string.ai_quick_summary_question)));
        binding.chipQuestionStablecoin.setOnClickListener(v -> askQuickQuestion(getString(R.string.ai_quick_stablecoin_question)));
        binding.inputAiQuestion.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                askCurrentQuestion();
                return true;
            }
            return false;
        });
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.getAnswerState().observe(getViewLifecycleOwner(), this::renderAnswer);
        viewModel.load();
    }

    private void renderState(AiAdvisorViewModel.AiUiState state) {
        if (binding == null || state == null) {
            return;
        }
        binding.aiProgress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
        binding.textAiRiskBadge.setText(state.riskLabel);
        binding.textAiRecommendationTitle.setText(state.recommendationTitle);
        binding.textAiRecommendationBody.setText(state.recommendationBody);
        binding.textAiDisclaimer.setVisibility(state.loading ? View.GONE : View.VISIBLE);
        renderStableSignals(state);
        renderNews(state);
        if (!state.errorMessage.isEmpty()) {
            showMessage(state.errorMessage);
        }
    }

    private void renderStableSignals(AiAdvisorViewModel.AiUiState state) {
        binding.layoutStableSignals.removeAllViews();
        for (AiAdvisorViewModel.StableAssetSignal signal : state.stableAssets) {
            TextView view = new TextView(requireContext());
            view.setText(getString(
                    R.string.ai_stable_asset_item,
                    signal.symbol,
                    signal.priceUsd.setScale(4, RoundingMode.HALF_UP).toPlainString(),
                    signal.deviationPercent.toPlainString()
            ));
            view.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.mw_text_primary));
            view.setTextSize(12f);
            view.setTypeface(view.getTypeface(), Typeface.BOLD);
            view.setBackgroundResource(R.drawable.bg_pill_highlight_ghost);
            view.setPadding(dp(14), dp(10), dp(14), dp(10));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(dp(8));
            binding.layoutStableSignals.addView(view, params);
        }
    }

    private void renderNews(AiAdvisorViewModel.AiUiState state) {
        binding.layoutNewsList.removeAllViews();
        if (state.newsItems.isEmpty() && !state.loading) {
            TextView empty = createBodyText(getString(R.string.ai_news_empty));
            binding.layoutNewsList.addView(empty);
            return;
        }
        for (NewsRepository.NewsItem item : state.newsItems) {
            binding.layoutNewsList.addView(createNewsCard(item));
        }
    }

    private View createNewsCard(NewsRepository.NewsItem item) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_surface);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openNews(item.url));

        TextView title = createTitleText(item.title);
        card.addView(title);

        String meta = item.source.isEmpty() ? item.publishedAt : item.source + "  |  " + item.publishedAt;
        TextView source = createMetaText(meta);
        card.addView(source);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private TextView createTitleText(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.mw_text_primary));
        view.setTextSize(14f);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private TextView createMetaText(String text) {
        TextView view = createBodyText(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(6), 0, 0);
        view.setLayoutParams(params);
        return view;
    }

    private TextView createBodyText(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.mw_text_secondary));
        view.setTextSize(12f);
        return view;
    }

    private void renderAnswer(String answer) {
        if (binding == null || answer == null) {
            return;
        }
        binding.textAiAnswer.setText(answer);
        binding.textAiAnswer.setVisibility(View.VISIBLE);
    }

    private void askQuickQuestion(String question) {
        binding.inputAiQuestion.setText(question);
        binding.inputAiQuestion.setSelection(question.length());
        viewModel.ask(question);
    }

    private void askCurrentQuestion() {
        viewModel.ask(getQuestionText());
    }

    private String getQuestionText() {
        return binding.inputAiQuestion.getText() == null
                ? ""
                : binding.inputAiQuestion.getText().toString();
    }

    private void openNews(String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        new CustomTabsIntent.Builder().build().launchUrl(requireContext(), Uri.parse(url));
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
