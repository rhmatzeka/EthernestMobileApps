package id.rahmat.projekakhir.ui.send;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import androidx.activity.result.ActivityResultLauncher;
import androidx.lifecycle.ViewModelProvider;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.google.android.material.snackbar.Snackbar;

import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.databinding.ActivitySendBinding;
import id.rahmat.projekakhir.ui.base.BaseActivity;
import id.rahmat.projekakhir.utils.WindowInsetsHelper;
import id.rahmat.projekakhir.wallet.GasEstimation;
import id.rahmat.projekakhir.wallet.WalletManager;

public class SendActivity extends BaseActivity {

    private ActivitySendBinding binding;
    private SendViewModel viewModel;
    private WalletManager walletManager;
    private final ActivityResultLauncher<ScanOptions> qrLauncher = registerForActivityResult(new ScanContract(), result -> {
        String scannedAddress = normalizeScannedAddress(result.getContents());
        if (scannedAddress.isEmpty()) {
            showMessage(getString(R.string.scan_cancelled));
            return;
        }
        if (!walletManager.isValidAddress(scannedAddress)) {
            showMessage(getString(R.string.scan_invalid));
            return;
        }
        binding.inputRecipientAddress.setText(scannedAddress);
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
        binding.textDestinationNetwork.setText(viewModel.getSelectedNetworkName());

        binding.inputEthAmount.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                String amount = editable == null ? "" : editable.toString().trim();
                if (amount.isEmpty()) {
                    binding.textGasFee.setText(getString(R.string.gas_fee_value));
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
        String address = normalizeScannedAddress(text == null ? "" : text.toString());
        if (!walletManager.isValidAddress(address)) {
            showMessage(getString(R.string.send_clipboard_empty));
            return;
        }
        binding.inputRecipientAddress.setText(address);
        binding.inputRecipientAddress.setSelection(address.length());
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
        showMessage(getString(R.string.confirm_send_summary, amount + " ETH", to));
        viewModel.send(to, amount);
    }

    private void startQrScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt(getString(R.string.scan_qr_action));
        options.setBeepEnabled(false);
        options.setCaptureActivity(PortraitCaptureActivity.class);
        options.setOrientationLocked(true);
        qrLauncher.launch(options);
    }

    private String normalizeScannedAddress(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (value.regionMatches(true, 0, "ethereum:", 0, "ethereum:".length())) {
            value = value.substring("ethereum:".length());
        }
        if (value.startsWith("//")) {
            value = value.substring(2);
        }
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int atIndex = value.indexOf('@');
        if (atIndex >= 0) {
            value = value.substring(0, atIndex);
        }
        return value.trim();
    }

    private String formatGas(GasEstimation gasEstimation) {
        return gasEstimation.getGasFeeEth().toPlainString() + " ETH | gas " + gasEstimation.getGasLimit();
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
}
