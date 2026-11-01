package id.rahmat.projekakhir.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayoutMediator;

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

    private ActivityOnboardingBinding binding;
    private List<OnboardingSlide> slides;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.applySystemBarPadding(binding.onboardingRoot, true, true);

        slides = Arrays.asList(
                new OnboardingSlide("01", getString(R.string.onboarding_one_title), getString(R.string.onboarding_one_desc)),
                new OnboardingSlide("02", getString(R.string.onboarding_two_title), getString(R.string.onboarding_two_desc)),
                new OnboardingSlide("03", getString(R.string.onboarding_three_title), getString(R.string.onboarding_three_desc))
        );

        binding.viewPagerOnboarding.setAdapter(new OnboardingPagerAdapter(slides));
        new TabLayoutMediator(binding.tabIndicator, binding.viewPagerOnboarding,
                (tab, position) -> { }).attach();

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
            binding.buttonNext.setVisibility(isLastPage ? View.GONE : View.VISIBLE);
            binding.walletActionContainer.setVisibility(isLastPage ? View.VISIBLE : View.GONE);
            binding.onboardingRoot.requestLayout();
        });
    }

    private void openPinSetup(@NonNull String mode) {
        Intent intent = new Intent(this, PinSetupActivity.class);
        intent.putExtra(EXTRA_AUTH_MODE, mode);
        startActivity(intent);
        finish();
    }
}
