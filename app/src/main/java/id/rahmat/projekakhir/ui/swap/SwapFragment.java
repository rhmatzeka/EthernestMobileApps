package id.rahmat.projekakhir.ui.swap;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
        for (SwapViewModel.Asset asset : SwapViewModel.Asset.values()) {
            if (!viewModel.hasAsset(asset)) {
                continue;
            }
            assetOptions.add(asset);
            assetLabels.add(viewModel.getAssetDisplayLabel(asset));
        }
    }

    private void setupListeners() {
        binding.cardSwapFromAsset.setOnClickListener(v -> openAssetPickerIfNeeded());
        binding.cardSwapToAsset.setOnClickListener(v -> openAssetPickerIfNeeded());
        binding.buttonSwapDirection.setOnClickListener(v -> {
            currentDirection = currentDirection == SwapViewModel.Direction.TOKEN_TO_ETH
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
        viewModel.getQuoteState().observe(getViewLifecycleOwner(), this::renderQuote);
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
                ? getString(R.string.continue_action)
                : getString(R.string.swap_coming_soon_action));
        binding.buttonExecuteSwap.setEnabled(viewModel.isSelectedAssetReady());
        binding.textSwapNote.setText(viewModel.getPoolNote());
        binding.buttonApproveSwap.setVisibility(viewModel.isSelectedAssetReady() ? binding.buttonApproveSwap.getVisibility() : View.GONE);
        binding.inputSwapAmount.setHint(currentDirection == SwapViewModel.Direction.TOKEN_TO_ETH
                ? viewModel.getAssetSymbol(viewModel.getSelectedAsset())
                : "ETH");

        boolean tokenIsFrom = currentDirection == SwapViewModel.Direction.TOKEN_TO_ETH;
        bindAssetCard(
                tokenIsFrom,
                binding.imageSwapFromIcon,
                binding.textSwapFromSymbol,
                binding.textSwapFromName,
                binding.textSwapFromMeta
        );
        bindAssetCard(
                !tokenIsFrom,
                binding.imageSwapToIcon,
                binding.textSwapToSymbol,
                binding.textSwapToName,
                binding.textSwapQuoteMeta
        );
    }

    private void bindAssetCard(boolean tokenCard, android.widget.ImageView iconView,
                               android.widget.TextView symbolView,
                               android.widget.TextView nameView,
                               android.widget.TextView metaView) {
        if (tokenCard) {
            SwapViewModel.Asset asset = viewModel.getSelectedAsset();
            symbolView.setText(viewModel.getAssetSymbol(asset));
            nameView.setText(getAssetName(asset));
            iconView.setImageResource(getAssetIcon(asset));
            if (metaView == binding.textSwapFromMeta) {
                metaView.setText(getString(R.string.swap_input_caption));
            }
            return;
        }

        symbolView.setText("ETH");
        nameView.setText(getString(R.string.swap_asset_name_eth));
        iconView.setImageResource(R.drawable.ic_token_eth_real);
        if (metaView == binding.textSwapFromMeta) {
            metaView.setText(getString(R.string.swap_input_caption));
        }
    }

    private void renderQuote(String quote) {
        String safeQuote = quote == null ? "" : quote.trim();
        String prefix = getString(R.string.swap_quote_receive_prefix);
        if (!safeQuote.isEmpty() && safeQuote.startsWith(prefix)) {
            String value = safeQuote.substring(prefix.length()).trim();
            binding.textSwapQuote.setText(value);
            binding.textSwapQuoteMeta.setText(getString(R.string.swap_quote_label_small));
            return;
        }

        if (safeQuote.isEmpty() || safeQuote.equals(getString(R.string.swap_quote_placeholder))) {
            binding.textSwapQuote.setText("0");
            binding.textSwapQuoteMeta.setText(getString(R.string.swap_quote_placeholder));
            return;
        }

        binding.textSwapQuote.setText("0");
        binding.textSwapQuoteMeta.setText(safeQuote);
    }

    private void openAssetPickerIfNeeded() {
        if (assetOptions.isEmpty()) {
            showMessage(getString(R.string.swap_no_active_pool));
            return;
        }

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_swap_asset_picker, null, false);
        LinearLayout container = view.findViewById(R.id.swapAssetOptionsContainer);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .create();
        dialog.setOnShowListener(dialogInterface -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        });

        for (SwapViewModel.Asset asset : assetOptions) {
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_swap_asset_option, container, false);
            LinearLayout rowRoot = row.findViewById(R.id.swapAssetOptionRoot);
            ImageView imageIcon = row.findViewById(R.id.imageSwapAssetIcon);
            TextView textSymbol = row.findViewById(R.id.textSwapAssetSymbol);
            TextView textName = row.findViewById(R.id.textSwapAssetName);
            TextView textStatus = row.findViewById(R.id.textSwapAssetStatus);
            ImageView imageSelected = row.findViewById(R.id.imageSwapAssetSelected);

            boolean isSelected = asset == viewModel.getSelectedAsset();
            imageIcon.setImageResource(getAssetIcon(asset));
            textSymbol.setText(viewModel.getAssetSymbol(asset));
            textName.setText(getAssetName(asset));
            textStatus.setText(isSelected
                    ? getString(R.string.swap_asset_status_selected)
                    : getString(R.string.swap_asset_status_ready));
            textStatus.setBackgroundResource(R.drawable.bg_swap_asset_status_ready);

            if (isSelected) {
                rowRoot.setBackgroundResource(R.drawable.bg_swap_asset_option_selected);
                imageSelected.setVisibility(View.VISIBLE);
            }

            rowRoot.setOnClickListener(v -> {
                viewModel.setSelectedAsset(asset);
                syncSwapUi();
                refreshQuote();
                dialog.dismiss();
            });
            container.addView(row);
        }

        view.findViewById(R.id.buttonCloseSwapAssetPicker).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private int getAssetIcon(SwapViewModel.Asset asset) {
        switch (asset) {
            case IDRX:
                return R.drawable.ic_token_idrx;
            case CUSTOM_1:
            case CUSTOM_2:
            case CUSTOM_3:
            case CUSTOM_4:
            case CUSTOM_5:
                return R.drawable.ic_token_custom;
            case MATS:
            default:
                return R.drawable.ic_token_mats;
        }
    }

    private String getAssetName(SwapViewModel.Asset asset) {
        return viewModel.getAssetName(asset);
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
