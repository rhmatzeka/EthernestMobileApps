package id.rahmat.projekakhir.ui.buy;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import id.rahmat.projekakhir.BuildConfig;
import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.data.repository.PriceRepository;
import id.rahmat.projekakhir.data.repository.WalletRepository;
import id.rahmat.projekakhir.databinding.ActivityBuyBinding;
import id.rahmat.projekakhir.di.ServiceLocator;
import id.rahmat.projekakhir.ui.base.BaseActivity;
import id.rahmat.projekakhir.utils.AppExecutors;
import id.rahmat.projekakhir.utils.WindowInsetsHelper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BuyActivity extends BaseActivity {

    private ActivityBuyBinding binding;
    private final StringBuilder amountDigits = new StringBuilder();
    private BigDecimal ethPriceIdr = BigDecimal.ZERO;
    private final DecimalFormat idrFormatter = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("id-ID")));
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();
    private boolean isCreatingOrder = false;
    private PaymentMethod selectedPaymentMethod = PaymentMethod.ALL;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBuyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.applySystemBarPadding(binding.buyRoot, true, true);

        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonPaymentMethod.setOnClickListener(v -> showPaymentMethodDialog());
        binding.buttonContinue.setOnClickListener(v -> openMidtransPayment());
        setupKeypad();
        loadEthPrice();
        renderPaymentMethod();
        renderAmount();
    }

    private void setupKeypad() {
        binding.key1.setOnClickListener(v -> appendDigit("1"));
        binding.key2.setOnClickListener(v -> appendDigit("2"));
        binding.key3.setOnClickListener(v -> appendDigit("3"));
        binding.key4.setOnClickListener(v -> appendDigit("4"));
        binding.key5.setOnClickListener(v -> appendDigit("5"));
        binding.key6.setOnClickListener(v -> appendDigit("6"));
        binding.key7.setOnClickListener(v -> appendDigit("7"));
        binding.key8.setOnClickListener(v -> appendDigit("8"));
        binding.key9.setOnClickListener(v -> appendDigit("9"));
        binding.key0.setOnClickListener(v -> appendDigit("0"));
        binding.keyComma.setOnClickListener(v -> appendDigit("000"));
        binding.keyBackspace.setOnClickListener(v -> {
            if (amountDigits.length() > 0) {
                amountDigits.deleteCharAt(amountDigits.length() - 1);
                renderAmount();
            }
        });
    }

    private void appendDigit(String digit) {
        if (amountDigits.length() >= 12) {
            return;
        }
        if (amountDigits.length() == 0 && "0".equals(digit)) {
            return;
        }
        amountDigits.append(digit);
        renderAmount();
    }

    private void renderAmount() {
        BigDecimal amountIdr = getAmountIdr();
        binding.textFiatAmount.setText(amountIdr.compareTo(BigDecimal.ZERO) == 0
                ? "0"
                : idrFormatter.format(amountIdr));

        BigDecimal ethAmount = calculateEstimatedEth(amountIdr);
        if (ethAmount.compareTo(BigDecimal.ZERO) <= 0) {
            binding.textEthEstimate.setText("0.000000");
            return;
        }
        binding.textEthEstimate.setText(ethAmount.stripTrailingZeros().toPlainString());
    }

    private BigDecimal calculateEstimatedEth(BigDecimal amountIdr) {
        if (ethPriceIdr.compareTo(BigDecimal.ZERO) <= 0 || amountIdr.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return amountIdr.divide(ethPriceIdr, 8, RoundingMode.DOWN);
    }

    private BigDecimal getAmountIdr() {
        if (amountDigits.length() == 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(amountDigits.toString());
    }

    private void loadEthPrice() {
        AppExecutors.io().execute(() -> {
            try {
                PriceRepository.PriceSnapshot snapshot = ServiceLocator
                        .getPriceRepository(this)
                        .getLatestEthPrice();
                ethPriceIdr = snapshot == null ? BigDecimal.ZERO : snapshot.idr;
            } catch (Exception exception) {
                ethPriceIdr = BigDecimal.ZERO;
            }
            runOnUiThread(this::renderAmount);
        });
    }

    private void openMidtransPayment() {
        BigDecimal amountIdr = getAmountIdr();
        if (amountIdr.compareTo(BigDecimal.ZERO) <= 0) {
            showMessage(getString(R.string.buy_amount_empty));
            return;
        }

        String backendBaseUrl = safeTrim(BuildConfig.BUY_BACKEND_BASE_URL);
        if (!backendBaseUrl.isEmpty()) {
            createBackendBuyOrder(amountIdr);
            return;
        }

        String paymentUrl = safeTrim(BuildConfig.MIDTRANS_PAYMENT_URL);
        if (paymentUrl.isEmpty()) {
            showMessage(getString(R.string.buy_midtrans_missing));
            return;
        }
        showMessage(getString(R.string.buy_midtrans_opening));
        openPaymentUrl(paymentUrl);
    }

    private void createBackendBuyOrder(BigDecimal amountIdr) {
        if (isCreatingOrder) {
            return;
        }

        BigDecimal estimatedEth = calculateEstimatedEth(amountIdr);
        if (estimatedEth.compareTo(BigDecimal.ZERO) <= 0) {
            showMessage(getString(R.string.buy_price_unavailable));
            return;
        }

        WalletRepository walletRepository = ServiceLocator.getWalletRepository(this);
        String walletAddress = safeTrim(walletRepository.getWalletAddress());
        if (walletAddress.isEmpty()) {
            showMessage(getString(R.string.buy_wallet_missing));
            return;
        }

        isCreatingOrder = true;
        binding.buttonContinue.setEnabled(false);
        showMessage(getString(R.string.buy_order_creating));

        AppExecutors.io().execute(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("walletAddress", walletAddress);
                payload.addProperty("amountIdr", amountIdr.toPlainString());
                payload.addProperty("estimatedEth", estimatedEth.toPlainString());
                payload.addProperty("paymentMethod", selectedPaymentMethod.code);

                String redirectUrl = createBuyOrder(payload.toString());
                runOnUiThread(() -> {
                    isCreatingOrder = false;
                    binding.buttonContinue.setEnabled(true);
                    showMessage(getString(R.string.buy_midtrans_opening));
                    openPaymentUrl(redirectUrl);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    isCreatingOrder = false;
                    binding.buttonContinue.setEnabled(true);
                    showMessage(formatBuyOrderError(exception));
                });
            }
        });
    }

    private String createBuyOrder(String payloadJson) throws Exception {
        Exception lastNetworkException = null;
        for (String baseUrl : getBackendBaseUrls()) {
            try {
                Request request = new Request.Builder()
                        .url(normalizeBaseUrl(baseUrl) + "api/buy/eth")
                        .post(RequestBody.create(payloadJson, JSON))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    ResponseBody body = response.body();
                    String responseText = body == null ? "" : body.string();
                    if (!response.isSuccessful()) {
                        throw new IllegalStateException(extractErrorMessage(responseText));
                    }
                    JsonObject json = gson.fromJson(responseText, JsonObject.class);
                    String redirectUrl = getJsonString(json, "redirectUrl");
                    if (redirectUrl.isEmpty()) {
                        throw new IllegalStateException("Midtrans redirectUrl kosong.");
                    }
                    return redirectUrl;
                }
            } catch (IOException exception) {
                lastNetworkException = exception;
            }
        }
        if (lastNetworkException != null) {
            throw lastNetworkException;
        }
        throw new ConnectException("Tidak ada URL buy server yang bisa dicoba.");
    }

    private List<String> getBackendBaseUrls() {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        String configuredUrl = safeTrim(BuildConfig.BUY_BACKEND_BASE_URL);
        if (!configuredUrl.isEmpty()) {
            urls.add(configuredUrl);
        }
        urls.add("http://10.0.2.2:8787/");
        urls.add("http://127.0.0.1:8787/");
        return new ArrayList<>(urls);
    }

    private void openPaymentUrl(String paymentUrl) {
        Intent intent = new Intent(this, PaymentWebViewActivity.class);
        intent.putExtra(PaymentWebViewActivity.EXTRA_PAYMENT_URL, paymentUrl);
        startActivity(intent);
    }

    private void showPaymentMethodDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_payment_methods, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            view.findViewById(R.id.buttonPaymentAll).setOnClickListener(v -> selectPaymentMethod(dialog, PaymentMethod.ALL));
            view.findViewById(R.id.buttonPaymentQris).setOnClickListener(v -> selectPaymentMethod(dialog, PaymentMethod.QRIS));
            view.findViewById(R.id.buttonPaymentEwallet).setOnClickListener(v -> selectPaymentMethod(dialog, PaymentMethod.EWALLET));
            view.findViewById(R.id.buttonPaymentBank).setOnClickListener(v -> selectPaymentMethod(dialog, PaymentMethod.BANK_TRANSFER));
            view.findViewById(R.id.buttonPaymentRetail).setOnClickListener(v -> selectPaymentMethod(dialog, PaymentMethod.RETAIL));
        });
        dialog.show();
    }

    private void selectPaymentMethod(AlertDialog dialog, PaymentMethod method) {
        selectedPaymentMethod = method;
        renderPaymentMethod();
        dialog.dismiss();
    }

    private void renderPaymentMethod() {
        binding.textPaymentIcon.setText(selectedPaymentMethod.badge);
        binding.textPaymentMethod.setText(selectedPaymentMethod.labelResId);
    }

    private String normalizeBaseUrl(String value) {
        String url = safeTrim(value);
        if (url.endsWith("/")) {
            return url;
        }
        return url + "/";
    }

    private String getJsonString(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return "";
        }
        return json.get(key).getAsString();
    }

    private String extractErrorMessage(String responseText) {
        String text = safeTrim(responseText);
        if (text.isEmpty()) {
            return getString(R.string.buy_order_failed);
        }
        try {
            JsonObject json = gson.fromJson(text, JsonObject.class);
            String message = getJsonString(json, "message");
            if (!message.isEmpty()) {
                return message;
            }
        } catch (Exception ignored) {
            // Some backend/proxy errors are HTML, so show a shortened raw message instead.
        }
        return text;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String formatBuyOrderError(Exception exception) {
        if (exception instanceof ConnectException
                || exception instanceof SocketTimeoutException
                || exception instanceof UnknownHostException) {
            return getString(R.string.buy_server_unreachable);
        }

        String message = exception == null ? "" : safeTrim(exception.getMessage());
        if (message.isEmpty()) {
            return getString(R.string.buy_order_failed);
        }
        if (message.length() > 120) {
            message = message.substring(0, 120) + "...";
        }
        return getString(R.string.buy_order_failed_detail, message);
    }

    private enum PaymentMethod {
        ALL("all", "ALL", R.string.buy_payment_all),
        QRIS("qris", "QR", R.string.buy_payment_qris),
        EWALLET("ewallet", "EW", R.string.buy_payment_ewallet),
        BANK_TRANSFER("bank_transfer", "VA", R.string.buy_payment_bank),
        RETAIL("retail", "RT", R.string.buy_payment_retail);

        private final String code;
        private final String badge;
        private final int labelResId;

        PaymentMethod(String code, String badge, int labelResId) {
            this.code = code;
            this.badge = badge;
            this.labelResId = labelResId;
        }
    }
}
