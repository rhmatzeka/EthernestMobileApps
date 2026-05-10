package id.rahmat.projekakhir.ui.send;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.journeyapps.barcodescanner.camera.CameraSettings;

import java.util.Collections;

import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.databinding.ActivityQrScannerBinding;
import id.rahmat.projekakhir.ui.base.BaseActivity;
import id.rahmat.projekakhir.utils.WindowInsetsHelper;

public class QrScannerActivity extends BaseActivity {

    public static final String EXTRA_QR_CONTENT = "id.rahmat.projekakhir.extra.QR_CONTENT";

    private ActivityQrScannerBinding binding;
    private DecoratedBarcodeView barcodeView;
    private boolean torchEnabled;
    private boolean finished;

    private final BarcodeCallback barcodeCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (finished || result == null || result.getText() == null || result.getText().trim().isEmpty()) {
                return;
            }
            finished = true;
            barcodeView.pause();
            Intent data = new Intent();
            data.putExtra(EXTRA_QR_CONTENT, result.getText().trim());
            setResult(Activity.RESULT_OK, data);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQrScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.applySystemBarPadding(binding.qrScannerRoot, true, true);

        barcodeView = binding.zxingBarcodeScanner;
        CameraSettings settings = new CameraSettings();
        settings.setAutoFocusEnabled(true);
        settings.setContinuousFocusEnabled(true);
        settings.setBarcodeSceneModeEnabled(true);
        settings.setMeteringEnabled(true);
        settings.setExposureEnabled(true);
        barcodeView.setCameraSettings(settings);
        barcodeView.setDecoderFactory(new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        barcodeView.setStatusText(getString(R.string.scan_qr_prompt));

        binding.buttonCloseScanner.setOnClickListener(v -> cancelScan());
        binding.buttonFlashScanner.setOnClickListener(v -> toggleTorch());
    }

    @Override
    protected void onResume() {
        super.onResume();
        finished = false;
        barcodeView.resume();
        barcodeView.decodeContinuous(barcodeCallback);
    }

    @Override
    protected void onPause() {
        barcodeView.pause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        cancelScan();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return barcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    private void toggleTorch() {
        torchEnabled = !torchEnabled;
        if (torchEnabled) {
            barcodeView.setTorchOn();
            binding.buttonFlashScanner.setAlpha(1f);
        } else {
            barcodeView.setTorchOff();
            binding.buttonFlashScanner.setAlpha(0.72f);
        }
    }

    private void cancelScan() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }
}
