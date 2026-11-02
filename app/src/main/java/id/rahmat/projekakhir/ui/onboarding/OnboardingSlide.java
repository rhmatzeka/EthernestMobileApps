package id.rahmat.projekakhir.ui.onboarding;

import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;

/** One onboarding page: an illustration layout plus its title and description. */
public class OnboardingSlide {

    @LayoutRes
    private final int artLayout;
    @StringRes
    private final int title;
    @StringRes
    private final int description;

    public OnboardingSlide(@LayoutRes int artLayout, @StringRes int title, @StringRes int description) {
        this.artLayout = artLayout;
        this.title = title;
        this.description = description;
    }

    @LayoutRes
    public int getArtLayout() {
        return artLayout;
    }

    @StringRes
    public int getTitle() {
        return title;
    }

    @StringRes
    public int getDescription() {
        return description;
    }
}
