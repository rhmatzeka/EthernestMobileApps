package id.rahmat.projekakhir.ui.receive;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.math.BigDecimal;
import java.math.BigInteger;

import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.databinding.ActivityReceiveBinding;
import id.rahmat.projekakhir.di.ServiceLocator;
import id.rahmat.projekakhir.ui.base.BaseActivity;
import id.rahmat.projekakhir.utils.WindowInsetsHelper;
import id.rahmat.projekakhir.wallet.EthereumNetwork;

public class ReceiveActivity extends BaseActivity {

    private ActivityReceiveBinding binding;
    private String receiveAddress = "";
    private String requestedAmountEth = "";
    private EthereumNetwork selectedNetwork;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReceiveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.applySystemBarPadding(binding.receiveRoot, true, true);

        selectedNetwork = ServiceLocator.getWalletRepository(this).getSelectedNetwork();
        receiveAddress = ServiceLocator.getWalletRepository(this).getWalletAddress();
        renderNetworkUi();
        binding.textReceiveAddress.setText(receiveAddress.isEmpty() ? getString(R.string.wallet_not_ready) : receiveAddress);
        binding.textReceiveQrAddress.setText(formatQrAddress(receiveAddress));
        updateAmountSummary();
        renderQrCode();

        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonInfo.setOnClickListener(v -> showMessage(
                getString(R.string.receive_info_message, selectedNetwork.getNativeSymbol())
        ));
        binding.buttonCopy.setOnClickListener(v -> copyAddress());
        binding.buttonSetAmount.setOnClickListener(v -> showSetAmountDialog());
        binding.buttonShare.setOnClickListener(v -> shareAddress());
        binding.buttonDepositExchange.setOnClickListener(v -> showExchangeDialog());
    }

    private void copyAddress() {
        String address = binding.textReceiveAddress.getText().toString();
        if (address.isEmpty() || getString(R.string.wallet_not_ready).contentEquals(address)) {
            showMessage(getString(R.string.wallet_not_ready));
            return;
        }
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText("wallet_address", address);
        clipboardManager.setPrimaryClip(clipData);
        showMessage(getString(R.string.copy_action));
    }

    private void shareAddress() {
        String address = binding.textReceiveAddress.getText().toString();
        if (address.isEmpty() || getString(R.string.wallet_not_ready).contentEquals(address)) {
            showMessage(getString(R.string.wallet_not_ready));
            return;
        }
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        String amount = requestedAmountEth;
        String nativeSymbol = selectedNetwork.getNativeSymbol();
        String shareText = amount.isEmpty()
                ? address
                : "Kirim " + amount + " " + nativeSymbol + " ke " + address + "\n" + buildEthereumPayload(address);
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_action)));
    }

    private void renderQrCode() {
        if (receiveAddress == null || receiveAddress.isEmpty()) {
            binding.imageReceiveQr.setImageDrawable(null);
            return;
        }
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            BitMatrix bitMatrix = new MultiFormatWriter().encode(buildEthereumPayload(receiveAddress), BarcodeFormat.QR_CODE, 720, 720);
            Bitmap bitmap = encoder.createBitmap(bitMatrix);
            binding.imageReceiveQr.setBackgroundColor(Color.WHITE);
            binding.imageReceiveQr.setImageBitmap(bitmap);
        } catch (Exception exception) {
            showMessage(getString(R.string.receive_invalid_amount));
        }
    }

    private String buildEthereumPayload(String address) {
        String amount = requestedAmountEth;
        if (amount.isEmpty()) {
            return "ethereum:" + address;
        }
        BigInteger wei = new BigDecimal(amount).movePointRight(18).toBigInteger();
        return "ethereum:" + address + "?value=" + wei.toString();
    }

    private void showSetAmountDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_receive_amount, null);
        EditText input = view.findViewById(R.id.inputReceiveAmount);
        input.setText(requestedAmountEth);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            view.findViewById(R.id.buttonClearAmount).setOnClickListener(v -> {
                requestedAmountEth = "";
                updateAmountSummary();
                renderQrCode();
                dialog.dismiss();
            });
            view.findViewById(R.id.buttonSaveAmount).setOnClickListener(v -> {
                String value = input.getText() == null ? "" : input.getText().toString().trim();
                if (!isValidAmount(value)) {
                    input.setError(getString(R.string.receive_invalid_amount));
                    return;
                }
                requestedAmountEth = value;
                updateAmountSummary();
                renderQrCode();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void showExchangeDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_exchange_list, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            view.findViewById(R.id.buttonExchangeBinance).setOnClickListener(v -> openExchangeApp(dialog, "Binance",
                    "com.binance.dev",
                    "com.binance.client",
                    "com.binance.android",
                    "com.binance.app",
                    "us.binance.dev",
                    "com.binance.us"));
            view.findViewById(R.id.buttonExchangeCoinbase).setOnClickListener(v -> openExchangeApp(dialog, "Coinbase", "com.coinbase.android"));
            view.findViewById(R.id.buttonExchangeKraken).setOnClickListener(v -> openExchangeApp(dialog, "Kraken", "com.kraken.invest.app"));
            view.findViewById(R.id.buttonExchangeOkx).setOnClickListener(v -> openExchangeApp(dialog, "OKX", "com.okinc.okex.gp"));
            view.findViewById(R.id.buttonExchangeIndodax).setOnClickListener(v -> openExchangeApp(dialog, "Indodax", "id.co.indodax"));
        });
        dialog.show();
    }

    private void openExchangeApp(AlertDialog dialog, String label, String... packageNames) {
        for (String packageName : packageNames) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                dialog.dismiss();
                startActivity(launchIntent);
                return;
            }
        }
        dialog.dismiss();
        showMessage(getString(R.string.exchange_app_not_installed, label));
    }

    private boolean isValidAmount(String value) {
        try {
            return !value.isEmpty() && new BigDecimal(value).compareTo(BigDecimal.ZERO) > 0;
        } catch (Exception exception) {
            return false;
        }
    }

    private void updateAmountSummary() {
        if (requestedAmountEth == null || requestedAmountEth.isEmpty()) {
            binding.textReceiveAmountSummary.setText(R.string.receive_amount_not_set);
        } else {
            binding.textReceiveAmountSummary.setText(getString(
                    R.string.receive_amount_summary,
                    requestedAmountEth,
                    selectedNetwork.getNativeSymbol()
            ));
        }
    }

    private void renderNetworkUi() {
        binding.textReceiveTitle.setText(getString(R.string.receive_screen_title_format, selectedNetwork.getNativeSymbol()));
        binding.textReceiveWarning.setText(getString(
                R.string.receive_asset_warning,
                selectedNetwork.getNativeAssetName(),
                selectedNetwork.getNativeSymbol()
        ));
        binding.textReceiveAssetSymbol.setText(selectedNetwork.getNativeSymbol());
        binding.imageReceiveAssetIcon.setImageResource(resolveNetworkIcon(selectedNetwork));
    }

    private int resolveNetworkIcon(EthereumNetwork network) {
        if (EthereumNetwork.BSC.isSameNetwork(network)) {
            return R.drawable.ic_token_bnb_real;
        }
        if (EthereumNetwork.AVALANCHE.isSameNetwork(network)) {
            return R.drawable.ic_token_avax_real;
        }
        if ("POL".equalsIgnoreCase(network.getNativeSymbol()) || "MATIC".equalsIgnoreCase(network.getNativeSymbol())) {
            return R.drawable.ic_token_polygon_real;
        }
        if ("FTM".equalsIgnoreCase(network.getNativeSymbol())) {
            return R.drawable.ic_token_fantom;
        }
        return R.drawable.ic_token_eth_real;
    }

    private String formatQrAddress(String address) {
        if (address == null || address.length() < 18) {
            return address == null ? "" : address;
        }
        int middle = address.length() / 2;
        return address.substring(0, middle) + "\n" + address.substring(middle);
    }
}
