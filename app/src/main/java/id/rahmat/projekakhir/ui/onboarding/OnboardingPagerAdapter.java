package id.rahmat.projekakhir.ui.onboarding;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.databinding.ItemOnboardingPageBinding;

public class OnboardingPagerAdapter extends RecyclerView.Adapter<OnboardingPagerAdapter.OnboardingViewHolder> {

    private static final int PIN_LENGTH = 6;
    private static final int PIN_FILLED = 4;
    /** Tall screens may enlarge the illustration a little to fill the space. */
    private static final float MAX_ART_SCALE = 1.15f;

    private final List<OnboardingSlide> slides;

    public OnboardingPagerAdapter(List<OnboardingSlide> slides) {
        this.slides = slides;
    }

    /** Each slide has its own illustration, so the layout id doubles as the view type. */
    @Override
    public int getItemViewType(int position) {
        return slides.get(position).getArtLayout();
    }

    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int artLayout) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemOnboardingPageBinding binding = ItemOnboardingPageBinding.inflate(inflater, parent, false);
        View art = inflater.inflate(artLayout, binding.artHolder, true);
        LinearLayout pinSlots = art.findViewById(R.id.pinSlots);
        if (pinSlots != null) {
            fillPinSlots(pinSlots);
        }
        return new OnboardingViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        holder.bind(slides.get(position));
    }

    @Override
    public int getItemCount() {
        return slides.size();
    }

    private static void fillPinSlots(LinearLayout container) {
        Context context = container.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int width = Math.round(34 * density);
        int height = Math.round(42 * density);
        int gap = Math.round(4 * density);
        int dot = Math.round(10 * density);
        for (int i = 0; i < PIN_LENGTH; i++) {
            boolean filled = i < PIN_FILLED;
            FrameLayout slot = new FrameLayout(context);
            slot.setBackgroundResource(filled ? R.drawable.bg_pin_slot_filled : R.drawable.bg_pin_slot_empty);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            params.setMarginStart(i == 0 ? 0 : gap);
            container.addView(slot, params);
            if (filled) {
                View pinDot = new View(context);
                pinDot.setBackgroundResource(R.drawable.bg_onboarding_pin_dot);
                FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dot, dot);
                dotParams.gravity = android.view.Gravity.CENTER;
                slot.addView(pinDot, dotParams);
            }
        }
    }

    static class OnboardingViewHolder extends RecyclerView.ViewHolder {

        private final ItemOnboardingPageBinding binding;

        OnboardingViewHolder(ItemOnboardingPageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            // Fit the illustration to the space left by the text: shrink it on
            // short screens, enlarge it slightly on tall ones.
            binding.artFrame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitArt());
        }

        void bind(OnboardingSlide slide) {
            binding.textSlideTitle.setText(slide.getTitle());
            binding.textSlideDescription.setText(slide.getDescription());
        }

        private void fitArt() {
            View art = binding.artHolder;
            int frameWidth = binding.artFrame.getWidth();
            int frameHeight = binding.artFrame.getHeight();
            if (art.getWidth() == 0 || art.getHeight() == 0 || frameHeight == 0) {
                return;
            }
            float scale = Math.min(MAX_ART_SCALE, Math.min(
                    frameWidth / (float) art.getWidth(),
                    frameHeight * 0.9f / art.getHeight()));
            art.setScaleX(scale);
            art.setScaleY(scale);
        }
    }
}
