package id.rahmat.projekakhir.ui.swap;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.databinding.DialogSwapSuccessBinding;
import id.rahmat.projekakhir.databinding.FragmentSwapBinding;
import id.rahmat.projekakhir.ui.base.BaseFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SwapFragment extends BaseFragment {

    private FragmentSwapBinding binding;
    private SwapViewModel viewModel;
    private SwapViewModel.Direction currentDirection = SwapViewModel.Direction.TOKEN_TO_ETH;
    private final List<SwapViewModel.Asset> assetOptions = new ArrayList<>();
    private final List<String> assetLabels = new ArrayList<>();
    private final List<String> directionLabels = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSwapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SwapViewModel.class);

        binding.textSwapNetworkBadge.setText(viewModel.getNetworkLabel());
        setupDropdowns();
        setupListeners();
        observeState();
        syncSwapUi();
        refreshQuote();
    }

    private void setupDropdowns() {
        assetOptions.clear();
        assetLabels.clear();
        if (viewModel.hasAsset(SwapViewModel.Asset.MATS)) {
            assetOptions.add(SwapViewModel.Asset.MATS);
            assetLabels.add(viewModel.getAssetDisplayLabel(SwapViewModel.Asset.MATS));
        }
        if (viewModel.hasAsset(SwapViewModel.Asset.IDRX)) {
            assetOptions.add(SwapViewModel.Asset.IDRX);
            assetLabels.add(viewModel.getAssetDisplayLabel(SwapViewModel.Asset.IDRX));
        }

        ArrayAdapter<String> assetAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                assetLabels
        );
        binding.inputSwapAsset.setAdapter(assetAdapter);

        int selectedAssetIndex = Math.max(0, assetOptions.indexOf(viewModel.getSelectedAsset()));
        if (!assetLabels.isEmpty()) {
            binding.inputSwapAsset.setText(assetLabels.get(selectedAssetIndex), false);
        }

        rebuildDirectionDropdown();
    }

    private void rebuildDirectionDropdown() {
        directionLabels.clear();
        directionLabels.add(viewModel.getTokenToEthLabel());
        directionLabels.add(viewModel.getEthToTokenLabel());

        ArrayAdapter<String> directionAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                directionLabels
        );
        binding.inputSwapDirection.setAdapter(directionAdapter);
        binding.inputSwapDirection.setText(directionLabels.get(currentDirection == SwapViewModel.Direction.TOKEN_TO_ETH ? 0 : 1), false);
    }

    private void setupListeners() {
        binding.inputSwapAsset.setOnItemClickListener((parent, itemView, position, id) -> {
            if (position < 0 || position >= assetOptions.size()) {
                return;
            }
            viewModel.setSelectedAsset(assetOptions.get(position));
            rebuildDirectionDropdown();
            syncSwapUi();
            refreshQuote();
        });

        binding.inputSwapDirection.setOnItemClickListener((parent, itemView, position, id) -> {
            currentDirection = position == 1
                    ? SwapViewModel.Direction.ETH_TO_TOKEN
                    : SwapViewModel.Direction.TOKEN_TO_ETH;
            syncSwapUi();
            refreshQuote();
        });

        binding.inputSwapAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshQuote();
            }
        });

        binding.buttonApproveSwap.setOnClickListener(v -> viewModel.approveSelectedToken(getAmountText()));
        binding.buttonExecuteSwap.setOnClickListener(v -> viewModel.executeSwap(currentDirection, getAmountText()));
    }

    private void observeState() {
        viewModel.getQuoteState().observe(getViewLifecycleOwner(), quote -> binding.textSwapQuote.setText(quote));
        viewModel.getApprovalRequired().observe(getViewLifecycleOwner(), required ->
                binding.buttonApproveSwap.setVisibility(Boolean.TRUE.equals(required) && viewModel.isSelectedAssetReady()
                        ? View.VISIBLE : View.GONE));
        viewModel.getEventState().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                binding.textSwapStatus.setVisibility(View.VISIBLE);
                binding.textSwapStatus.setText(message);
                int colorRes = isFailureMessage(message) ? R.color.mw_highlight : R.color.mw_text_secondary;
                binding.textSwapStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
                showMessage(message);
            }
        });
        viewModel.getSwapSuccessState().observe(getViewLifecycleOwner(), successEvent -> {
            if (successEvent != null) {
                binding.textSwapStatus.setVisibility(View.VISIBLE);
                binding.textSwapStatus.setText(getString(R.string.swap_success_status, successEvent.symbol));
                binding.textSwapStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.mw_text_secondary));
                showSwapSuccessDialog(successEvent);
            }
        });
    }

    private void refreshQuote() {
        viewModel.refreshQuote(currentDirection, getAmountText());
    }

    private void syncSwapUi() {
        binding.buttonApproveSwap.setText(viewModel.getApproveLabel());
        binding.buttonExecuteSwap.setText(viewModel.isSelectedAssetReady()
                ? viewModel.getExecuteLabel()
                : getString(R.string.swap_coming_soon_action));
        binding.buttonExecuteSwap.setEnabled(viewModel.isSelectedAssetReady());
        binding.textSwapNote.setText(viewModel.getPoolNote());
        binding.buttonApproveSwap.setVisibility(viewModel.isSelectedAssetReady() ? binding.buttonApproveSwap.getVisibility() : View.GONE);
    }

    private String getAmountText() {
        CharSequence value = binding.inputSwapAmount.getText();
        return value == null ? "" : value.toString().trim();
    }

    private boolean isFailureMessage(String message) {
        String safeMessage = message == null ? "" : message.toLowerCase();
        return safeMessage.contains("gagal")
                || safeMessage.contains("invalid")
                || safeMessage.contains("belum")
                || safeMessage.contains("error")
                || safeMessage.contains("segera");
    }

    private void showSwapSuccessDialog(SwapViewModel.SwapSuccessEvent event) {
        DialogSwapSuccessBinding dialogBinding = DialogSwapSuccessBinding.inflate(getLayoutInflater());
        dialogBinding.textSwapSuccessMessage.setText(getString(R.string.swap_success_dialog_message, event.symbol));
        dialogBinding.textSwapSuccessHash.setText(event.shortTransactionHash);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogBinding.getRoot())
                .create();

        dialogBinding.buttonCloseSwapSuccess.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.buttonOpenSwapEtherscan.setOnClickListener(v -> {
            String url = "https://sepolia.etherscan.io/tx/" + event.transactionHash;
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        });
        dialog.show();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
