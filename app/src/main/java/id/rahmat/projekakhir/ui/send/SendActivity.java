package id.rahmat.projekakhir.ui.send;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.databinding.ActivitySendBinding;
import id.rahmat.projekakhir.ui.base.BaseActivity;
import id.rahmat.projekakhir.utils.WindowInsetsHelper;
import id.rahmat.projekakhir.wallet.GasEstimation;
import id.rahmat.projekakhir.wallet.WalletManager;

public class SendActivity extends BaseActivity {

    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(?i)0x[a-f0-9]{40}");
    private static final Pattern CHAIN_ID_PATTERN = Pattern.compile("@([0-9]+)");

    private ActivitySendBinding binding;
    private SendViewModel viewModel;
    private WalletManager walletManager;
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    launchQrScanner();
                } else {
                    showMessage(getString(R.string.scan_camera_permission_denied));
                }
            }
    );
    private final ActivityResultLauncher<Intent> qrLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            showMessage(getString(R.string.scan_cancelled));
            return;
        }
        String rawResult = result.getData().getStringExtra(QrScannerActivity.EXTRA_QR_CONTENT);
        if (rawResult == null || rawResult.trim().isEmpty()) {
            showMessage(getString(R.string.scan_cancelled));
            return;
        }
        ScannedPayment payment = parseScannedPayment(rawResult);
        if (payment.address.isEmpty() || !walletManager.isValidAddress(payment.address)) {
            showMessage(getString(R.string.scan_invalid));
            return;
        }
        applyScannedPayment(payment);
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySendBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.applySystemBarPadding(binding.sendRoot, true, true);
        viewModel = new ViewModelProvider(this).get(SendViewModel.class);
        walletManager = new WalletManager(this);

        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonScanQr.setOnClickListener(v -> startQrScanner());
        binding.buttonPasteAddress.setOnClickListener(v -> pasteAddressFromClipboard());
        binding.buttonMaxAmount.setOnClickListener(v -> {
            showMessage(getString(R.string.send_max_loading));
            viewModel.loadMaxAmount();
        });
        binding.buttonConfirmSend.setOnClickListener(v -> sendTransaction());
        binding.textSendTitle.setText(getString(R.string.send_screen_title_format, viewModel.getSelectedNetworkSymbol()));
        binding.textDestinationNetwork.setText(viewModel.getSelectedNetworkName());
        binding.imageDestinationNetworkIcon.setImageResource(viewModel.getSelectedNetworkIconRes());
        binding.textAmountSymbol.setText(viewModel.getSelectedNetworkSymbol());
        renderEmptyGasState();

        binding.inputEthAmount.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                String amount = editable == null ? "" : editable.toString().trim();
                if (amount.isEmpty()) {
                    renderEmptyGasState();
                    binding.inputFiatAmount.setText("");
                    return;
                }
                viewModel.estimateFiat(amount);
                maybeEstimateGas();
            }
        });

        binding.inputRecipientAddress.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                maybeEstimateGas();
            }
        });

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getGasState().observe(this, gasEstimation -> {
            if (gasEstimation == null) {
                return;
            }
            binding.textGasFee.setText(formatGas(gasEstimation));
        });

        viewModel.getFiatState().observe(this, fiatText -> {
            if (fiatText == null) {
                return;
            }
            binding.inputFiatAmount.setText(fiatText);
        });

        viewModel.getSendResult().observe(this, hash -> {
            if (hash == null) {
                return;
            }
            if (hash.isEmpty()) {
                showMessage(getString(R.string.send_failed));
                return;
            }
            Snackbar.make(binding.getRoot(), getString(R.string.send_success), Snackbar.LENGTH_LONG).show();
            finish();
        });

        viewModel.getMaxAmountState().observe(this, amount -> {
            if (amount == null || amount.isEmpty()) {
                showMessage(getString(R.string.wallet_not_ready));
                return;
            }
            binding.inputEthAmount.setText(amount);
            binding.inputEthAmount.setSelection(amount.length());
        });
    }

    private void pasteAddressFromClipboard() {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            showMessage(getString(R.string.send_clipboard_empty));
            return;
        }
        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            showMessage(getString(R.string.send_clipboard_empty));
            return;
        }
        CharSequence text = clipData.getItemAt(0).coerceToText(this);
        ScannedPayment payment = parseScannedPayment(text == null ? "" : text.toString());
        if (!walletManager.isValidAddress(payment.address)) {
            showMessage(getString(R.string.send_clipboard_empty));
            return;
        }
        applyScannedPayment(payment);
    }

    private void maybeEstimateGas() {
        String to = getFieldValue(binding.inputRecipientAddress.getText());
        String amount = getFieldValue(binding.inputEthAmount.getText());
        if (to.isEmpty() || amount.isEmpty()) {
            return;
        }
        if (!walletManager.isValidAddress(to)) {
            return;
        }
        viewModel.estimateGas(to, amount);
    }

    private void sendTransaction() {
        String to = getFieldValue(binding.inputRecipientAddress.getText());
        String amount = getFieldValue(binding.inputEthAmount.getText());
        if (to.isEmpty() || amount.isEmpty()) {
            showMessage(getString(R.string.wallet_not_ready));
            return;
        }
        if (!walletManager.isValidAddress(to)) {
            showMessage(getString(R.string.scan_invalid));
            return;
        }
        showMessage(getString(R.string.confirm_send_summary, amount + " " + viewModel.getSelectedNetworkSymbol(), to));
        viewModel.send(to, amount);
    }

    private void startQrScanner() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            showMessage(getString(R.string.scan_camera_unavailable));
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchQrScanner();
            return;
        }
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void launchQrScanner() {
        qrLauncher.launch(new Intent(this, QrScannerActivity.class));
    }

    private void applyScannedPayment(ScannedPayment payment) {
        binding.inputRecipientAddress.setText(payment.address);
        binding.inputRecipientAddress.setSelection(payment.address.length());
        if (!payment.amount.isEmpty()) {
            binding.inputEthAmount.setText(payment.amount);
            binding.inputEthAmount.setSelection(payment.amount.length());
        }
        if (payment.chainId != null && payment.chainId != viewModel.getSelectedNetworkChainId()) {
            showMessage(getString(
                    R.string.scan_network_mismatch,
                    payment.chainId,
                    viewModel.getSelectedNetworkName()
            ));
            return;
        }
        showMessage(payment.amount.isEmpty() ? getString(R.string.scan_success) : getString(R.string.scan_success_with_amount));
    }

    private ScannedPayment parseScannedPayment(String rawValue) {
        if (rawValue == null) {
            return ScannedPayment.empty();
        }
        String value = decodeQrText(rawValue.trim());
        if (value.isEmpty()) {
            return ScannedPayment.empty();
        }
        Matcher addressMatcher = ADDRESS_PATTERN.matcher(value);
        String address = addressMatcher.find() ? normalizeAddressPrefix(addressMatcher.group()) : "";
        Long chainId = parseChainId(value);
        String amount = parseAmount(value);
        return new ScannedPayment(address, amount, chainId);
    }

    private String normalizeAddressPrefix(String address) {
        if (address == null || address.length() < 2) {
            return "";
        }
        return "0x" + address.substring(2);
    }

    private String decodeQrText(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name()).trim();
        } catch (Exception exception) {
            return value.trim();
        }
    }

    private Long parseChainId(String value) {
        Matcher matcher = CHAIN_ID_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String parseAmount(String value) {
        String query = extractQuery(value);
        if (query.isEmpty()) {
            return "";
        }
        String decimalAmount = "";
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = decodeQrText(part.substring(0, separator)).toLowerCase(Locale.US);
            String paramValue = decodeQrText(part.substring(separator + 1));
            if (("amount".equals(key) || "amounteth".equals(key) || "ether".equals(key)) && isPositiveDecimal(paramValue)) {
                decimalAmount = normalizeDecimal(paramValue);
            }
            if (("value".equals(key) || "uint256".equals(key)) && isPositiveDecimal(paramValue)) {
                return normalizeWeiAmount(paramValue);
            }
        }
        return decimalAmount;
    }

    private String extractQuery(String value) {
        int queryIndex = value.indexOf('?');
        if (queryIndex < 0 || queryIndex == value.length() - 1) {
            return "";
        }
        int fragmentIndex = value.indexOf('#', queryIndex);
        return fragmentIndex >= 0
                ? value.substring(queryIndex + 1, fragmentIndex)
                : value.substring(queryIndex + 1);
    }

    private boolean isPositiveDecimal(String value) {
        try {
            return new BigDecimal(value).compareTo(BigDecimal.ZERO) > 0;
        } catch (Exception exception) {
            return false;
        }
    }

    private String normalizeWeiAmount(String value) {
        try {
            return formatAmount(new BigDecimal(value).movePointLeft(18));
        } catch (Exception exception) {
            return "";
        }
    }

    private String normalizeDecimal(String value) {
        try {
            return formatAmount(new BigDecimal(value));
        } catch (Exception exception) {
            return "";
        }
    }

    private String formatAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private String formatGas(GasEstimation gasEstimation) {
        return gasEstimation.getGasFeeEth().toPlainString() + " " + viewModel.getSelectedNetworkSymbol()
                + " | gas " + gasEstimation.getGasLimit();
    }

    private void renderEmptyGasState() {
        binding.textGasFee.setText("0 " + viewModel.getSelectedNetworkSymbol() + " | gas -");
    }

    private String getFieldValue(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    private static class ScannedPayment {
        final String address;
        final String amount;
        final Long chainId;

        ScannedPayment(String address, String amount, Long chainId) {
            this.address = address == null ? "" : address.trim();
            this.amount = amount == null ? "" : amount.trim();
            this.chainId = chainId;
        }

        static ScannedPayment empty() {
            return new ScannedPayment("", "", null);
        }
    }
}
