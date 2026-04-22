package id.rahmat.projekakhir.ui.buy;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.databinding.ActivityPaymentWebviewBinding;
import id.rahmat.projekakhir.ui.base.BaseActivity;
import id.rahmat.projekakhir.utils.WindowInsetsHelper;

public class PaymentWebViewActivity extends BaseActivity {

    public static final String EXTRA_PAYMENT_URL = "extra_payment_url";

    private ActivityPaymentWebviewBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentWebviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.applySystemBarPadding(binding.paymentRoot, true, true);

        binding.buttonBack.setOnClickListener(v -> finish());

        String paymentUrl = getIntent().getStringExtra(EXTRA_PAYMENT_URL);
        if (paymentUrl == null || paymentUrl.trim().isEmpty()) {
            showMessage(getString(R.string.buy_checkout_missing_url));
            finish();
            return;
        }

        setupWebView();
        binding.webViewPayment.loadUrl(paymentUrl);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = binding.webViewPayment.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);

        binding.webViewPayment.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                binding.progressPayment.setVisibility(View.GONE);
            }
        });
        binding.webViewPayment.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                binding.progressPayment.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                binding.progressPayment.setProgress(newProgress);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (binding != null && binding.webViewPayment.canGoBack()) {
            binding.webViewPayment.goBack();
            return;
        }
        super.onBackPressed();
    }
}
