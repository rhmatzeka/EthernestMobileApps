package id.rahmat.projekakhir.ui.onboarding;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

import id.rahmat.projekakhir.MainActivity;
import id.rahmat.projekakhir.databinding.ActivitySplashBinding;
import id.rahmat.projekakhir.ui.base.BaseActivity;
import id.rahmat.projekakhir.utils.AppPreferences;
import id.rahmat.projekakhir.utils.WindowInsetsHelper;

public class SplashActivity extends BaseActivity {

    /** Long enough for the 720 ms entrance animation to finish, no longer. */
    private static final long SPLASH_DURATION_MS = 1200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Animator> runningAnimators = new ArrayList<>();
    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.applySystemBarPadding(binding.splashRoot, true, true);

        playEntranceAnimation();
        handler.postDelayed(this::openNextScreen, SPLASH_DURATION_MS);
    }

    private void playEntranceAnimation() {
        binding.splashContent.setAlpha(0f);
        binding.splashContent.setTranslationY(54f);
        binding.splashContent.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(720L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        runningAnimators.add(startInfiniteRotation(binding.viewOuterRing, 0f, 360f, 9000L));
        runningAnimators.add(startInfiniteRotation(binding.viewInnerRing, 360f, 0f, 7000L));
        runningAnimators.add(startInfiniteAlpha(binding.logoCore, 0.88f, 1f, 1600L, 0L));
        runningAnimators.add(startInfiniteAlpha(binding.dotOne, 0.28f, 1f, 900L, 0L));
        runningAnimators.add(startInfiniteAlpha(binding.dotTwo, 0.28f, 1f, 900L, 180L));
        runningAnimators.add(startInfiniteAlpha(binding.dotThree, 0.28f, 1f, 900L, 360L));
    }

    private Animator startInfiniteRotation(View view, float from, float to, long duration) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.ROTATION, from, to);
        animator.setDuration(duration);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.start();
        return animator;
    }

    private Animator startInfiniteAlpha(View view, float from, float to, long duration, long delay) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.ALPHA, from, to);
        animator.setDuration(duration);
        animator.setStartDelay(delay);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.start();
        return animator;
    }

    private void openNextScreen() {
        AppPreferences preferences = new AppPreferences(this);
        Intent intent;
        if (!preferences.isOnboardingDone()) {
            intent = new Intent(this, OnboardingActivity.class);
        } else {
            id.rahmat.projekakhir.wallet.WalletManager walletManager =
                    new id.rahmat.projekakhir.wallet.WalletManager(this);
            if (walletManager.hasPin()) {
                intent = new Intent(this, id.rahmat.projekakhir.ui.auth.PinLoginActivity.class);
            } else {
                intent = new Intent(this, MainActivity.class);
            }
        }
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (Animator animator : runningAnimators) {
            animator.cancel();
        }
        binding = null;
        super.onDestroy();
    }
}
