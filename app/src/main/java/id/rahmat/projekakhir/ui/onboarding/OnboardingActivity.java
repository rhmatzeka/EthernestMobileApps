package id.rahmat.projekakhir.ui.onboarding;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Arrays;
import java.util.List;

import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.databinding.ActivityOnboardingBinding;
import id.rahmat.projekakhir.ui.auth.ImportWalletActivity;
import id.rahmat.projekakhir.ui.auth.PinSetupActivity;
import id.rahmat.projekakhir.ui.base.BaseActivity;
import id.rahmat.projekakhir.utils.WindowInsetsHelper;

public class OnboardingActivity extends BaseActivity {

    public static final String EXTRA_AUTH_MODE = "extra_auth_mode";
    public static final String MODE_CREATE = "create";
    public static final String MODE_IMPORT = "import";

    /** How much slower the illustration moves than the page, for a little depth. */
    private static final float ART_PARALLAX = 0.35f;
    private static final int DOT_SIZE_DP = 8;
    private static final int DOT_ACTIVE_WIDTH_DP = 24;
    private static final int DOT_GAP_DP = 6;

    private ActivityOnboardingBinding binding;
    private List<OnboardingSlide> slides;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.applySystemBarPadding(binding.onboardingRoot, true, true);

        slides = Arrays.asList(
                new OnboardingSlide(R.layout.onboarding_art_wallet,
                        R.string.onboarding_one_title, R.string.onboarding_one_desc),
                new OnboardingSlide(R.layout.onboarding_art_security,
                        R.string.onboarding_two_title, R.string.onboarding_two_desc),
                new OnboardingSlide(R.layout.onboarding_art_transfer,
                        R.string.onboarding_three_title, R.string.onboarding_three_desc)
        );

        binding.viewPagerOnboarding.setAdapter(new OnboardingPagerAdapter(slides));
        binding.viewPagerOnboarding.setPageTransformer((page, position) -> {
            View art = page.findViewById(R.id.artHolder);
            if (art != null) {
                art.setTranslationX(-position * page.getWidth() * ART_PARALLAX);
            }
            page.setAlpha(1f - Math.min(1f, Math.abs(position)) * 0.6f);
        });
        buildIndicator();

        binding.viewPagerOnboarding.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateActionState(position);
            }
        });

        binding.buttonNext.setOnClickListener(v -> {
            int nextIndex = binding.viewPagerOnboarding.getCurrentItem() + 1;
            if (nextIndex < slides.size()) {
                binding.viewPagerOnboarding.setCurrentItem(nextIndex, true);
            }
        });

        binding.textSkip.setOnClickListener(v -> binding.viewPagerOnboarding.setCurrentItem(slides.size() - 1, true));
        binding.buttonCreateWallet.setOnClickListener(v -> openPinSetup(MODE_CREATE));
        binding.buttonImportWallet.setOnClickListener(v -> {
            startActivity(new Intent(this, ImportWalletActivity.class));
            finish();
        });

        updateActionState(0);
    }

    private void updateActionState(int position) {
        boolean isLastPage = position == slides.size() - 1;
        // ViewPager2 can report the page change while it is laying out (e.g. when
        // animations are off), and a visibility change made then may never get
        // a layout pass: the Next button vanished but the wallet buttons never
        // appeared, leaving the user stuck. Apply it on the next frame instead.
        binding.onboardingRoot.post(() -> {
            if (binding == null) {
                return;
            }
            updateIndicator(position);
            binding.textSkip.setVisibility(isLastPage ? View.INVISIBLE : View.VISIBLE);
            binding.buttonNext.setVisibility(isLastPage ? View.GONE : View.VISIBLE);
            binding.walletActionContainer.setVisibility(isLastPage ? View.VISIBLE : View.GONE);
            binding.onboardingRoot.requestLayout();
        });
    }

    private void buildIndicator() {
        for (int i = 0; i < slides.size(); i++) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.bg_onboarding_dot);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(DOT_SIZE_DP), dp(DOT_SIZE_DP));
            params.setMarginStart(i == 0 ? 0 : dp(DOT_GAP_DP));
            binding.indicatorContainer.addView(dot, params);
        }
    }

    /** The current slide's dot stretches into a pill; the others stay small and dim. */
    private void updateIndicator(int position) {
        TransitionManager.beginDelayedTransition(binding.indicatorContainer,
                new ChangeBounds().setDuration(220L));
        ColorStateList active = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.mw_highlight));
        ColorStateList idle = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.mw_surface_elevated));
        for (int i = 0; i < binding.indicatorContainer.getChildCount(); i++) {
            View dot = binding.indicatorContainer.getChildAt(i);
            boolean selected = i == position;
            dot.setBackgroundTintList(selected ? active : idle);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) dot.getLayoutParams();
            params.width = dp(selected ? DOT_ACTIVE_WIDTH_DP : DOT_SIZE_DP);
            dot.setLayoutParams(params);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openPinSetup(@NonNull String mode) {
        Intent intent = new Intent(this, PinSetupActivity.class);
        intent.putExtra(EXTRA_AUTH_MODE, mode);
        startActivity(intent);
        finish();
    }
}
