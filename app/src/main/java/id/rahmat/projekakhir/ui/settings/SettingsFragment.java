package id.rahmat.projekakhir.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.Handler;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.concurrent.Executor;

import id.rahmat.projekakhir.R;
import id.rahmat.projekakhir.data.local.AppDatabase;
import id.rahmat.projekakhir.data.repository.WalletRepository;
import id.rahmat.projekakhir.databinding.FragmentSettingsBinding;
import id.rahmat.projekakhir.di.ServiceLocator;
import id.rahmat.projekakhir.ui.base.BaseFragment;
import id.rahmat.projekakhir.ui.onboarding.OnboardingActivity;
import id.rahmat.projekakhir.utils.AppExecutors;
import id.rahmat.projekakhir.utils.AppPreferences;
import id.rahmat.projekakhir.wallet.EthereumNetwork;
import id.rahmat.projekakhir.wallet.EthereumNetworkRegistry;

public class SettingsFragment extends BaseFragment {

    private FragmentSettingsBinding binding;
    private WalletRepository walletRepository;
    private AppPreferences appPreferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        appPreferences = new AppPreferences(requireContext());
        walletRepository = ServiceLocator.getWalletRepository(requireContext());

        binding.switchBiometric.setChecked(appPreferences.isBiometricEnabled());
        binding.switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appPreferences.setBiometricEnabled(isChecked);
            showMessage(isChecked ? getString(R.string.activate_biometric) : getString(R.string.use_pin_instead));
        });

        renderSelectedNetwork();
        binding.buttonChooseNetwork.setOnClickListener(v -> showNetworkPicker());
        binding.buttonAddCustomNetwork.setOnClickListener(v -> showAddCustomNetworkDialog());
        binding.buttonBackupMnemonic.setOnClickListener(v -> revealMnemonic(appPreferences.isBiometricEnabled()));
        binding.buttonChangePin.setOnClickListener(v -> showMessage(getString(R.string.change_pin)));
        binding.buttonLogout.setOnClickListener(v -> confirmLogout());
        binding.textDeveloperGithub.setOnClickListener(v -> openGithubProfile());
    }

    private void revealMnemonic(boolean biometricEnabled) {
        if (!walletRepository.hasWallet()) {
            showMessage(getString(R.string.wallet_not_ready));
            return;
        }

        if (!biometricEnabled) {
            showMnemonicDialog();
            return;
        }

        BiometricManager biometricManager = BiometricManager.from(requireContext());
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            showMnemonicDialog();
            return;
        }

        Executor executor = requireActivity().getMainExecutor();
        BiometricPrompt prompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                showMnemonicDialog();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                showMessage(errString.toString());
            }
        });

        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.backup_mnemonic))
                .setSubtitle(getString(R.string.biometric_subtitle))
                .setNegativeButtonText(getString(R.string.back_action))
                .build());
    }

    private void showMnemonicDialog() {
        String mnemonic = walletRepository.getMnemonic();
        if (mnemonic == null || mnemonic.isEmpty()) {
            showMessage(getString(R.string.no_mnemonic_backup));
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.backup_seed_title))
                .setMessage(mnemonic)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void confirmLogout() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_logout, null, false);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();
        dialogView.findViewById(R.id.buttonCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.buttonConfirmLogout).setOnClickListener(v -> {
            dialog.dismiss();
            logout();
        });
        dialog.show();
    }

    private void logout() {
        // Capture the context up front: the fragment may be detached by the
        // time the background work finishes, and requireContext() would throw.
        Context appContext = requireContext().getApplicationContext();
        AppExecutors.io().execute(() -> {
            walletRepository.clearSession();
            AppDatabase.getInstance(appContext).clearAllTables();

            new Handler(Looper.getMainLooper()).post(() -> {
                Intent intent = new Intent(appContext, OnboardingActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                appContext.startActivity(intent);
            });
        });
    }

    private void showNetworkPicker() {
        List<EthereumNetwork> networks = EthereumNetworkRegistry.getAll(appPreferences);
        String selectedKey = appPreferences.getSelectedNetwork();
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_network_picker, null, false);
        LinearLayout container = view.findViewById(R.id.networkOptionsContainer);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .create();
        dialog.setOnShowListener(dialogInterface -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        });

        for (EthereumNetwork network : networks) {
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_network_option, container, false);
            LinearLayout rowRoot = row.findViewById(R.id.networkOptionRoot);
            TextView textSymbol = row.findViewById(R.id.textNetworkSymbol);
            TextView textName = row.findViewById(R.id.textNetworkName);
            TextView textMeta = row.findViewById(R.id.textNetworkMeta);
            TextView textStatus = row.findViewById(R.id.textNetworkStatus);
            ImageView imageSelected = row.findViewById(R.id.imageNetworkSelected);

            boolean isSelected = network.getKey().equalsIgnoreCase(selectedKey);
            boolean hasRpc = network.hasRpcUrl();
            textSymbol.setText(network.getNativeSymbol());
            textName.setText(network.getDisplayName());
            textMeta.setText(buildNetworkMeta(network));
            textStatus.setText(isSelected
                    ? getString(R.string.network_status_selected)
                    : getString(hasRpc ? R.string.network_rpc_ready : R.string.network_rpc_missing));
            textStatus.setBackgroundResource(hasRpc
                    ? R.drawable.bg_network_status_ready
                    : R.drawable.bg_network_status_missing);
            if (isSelected) {
                rowRoot.setBackgroundResource(R.drawable.bg_network_option_selected);
                imageSelected.setVisibility(View.VISIBLE);
            }

            rowRoot.setOnClickListener(v -> {
                if (!network.hasRpcUrl()) {
                    showMessage(getString(R.string.network_missing_rpc_select, network.getDisplayName()));
                    return;
                }
                appPreferences.setSelectedNetwork(network.getKey());
                renderSelectedNetwork();
                showMessage(getString(R.string.network_selected_message, network.getDisplayName()));
                dialog.dismiss();
            });
            container.addView(row);
        }

        view.findViewById(R.id.buttonCloseNetworkPicker).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showAddCustomNetworkDialog() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_network, null, false);
        TextInputEditText inputName = view.findViewById(R.id.inputNetworkName);
        TextInputEditText inputSymbol = view.findViewById(R.id.inputNetworkSymbol);
        TextInputEditText inputChainId = view.findViewById(R.id.inputNetworkChainId);
        TextInputEditText inputRpcUrl = view.findViewById(R.id.inputNetworkRpcUrl);
        TextInputEditText inputCoinGeckoId = view.findViewById(R.id.inputNetworkCoinGeckoId);
        TextInputEditText inputExplorerUrl = view.findViewById(R.id.inputNetworkExplorerUrl);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .create();
        dialog.setOnShowListener(dialogInterface -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        });
        dialog.show();
        view.findViewById(R.id.buttonCancelCustomNetwork).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.buttonSaveCustomNetwork).setOnClickListener(v -> {
            String displayName = textOf(inputName);
            String symbol = textOf(inputSymbol);
            String chainIdRaw = textOf(inputChainId);
            String rpcUrl = textOf(inputRpcUrl);
            String coinGeckoId = textOf(inputCoinGeckoId);
            String explorerUrl = normalizeExplorerBaseUrl(textOf(inputExplorerUrl));
            long chainId = parseLong(chainIdRaw);
            if (displayName.isEmpty() || symbol.isEmpty() || chainId <= 0 || rpcUrl.isEmpty()) {
                showMessage(getString(R.string.network_form_invalid));
                return;
            }

            EthereumNetwork customNetwork = EthereumNetwork.custom(
                    displayName,
                    displayName,
                    symbol,
                    coinGeckoId,
                    chainId,
                    rpcUrl,
                    explorerUrl
            );
            EthereumNetworkRegistry.saveCustomNetwork(appPreferences, customNetwork);
            appPreferences.setSelectedNetwork(customNetwork.getKey());
            renderSelectedNetwork();
            showMessage(getString(R.string.network_form_saved, customNetwork.getDisplayName()));
            dialog.dismiss();
        });
    }

    private void renderSelectedNetwork() {
        EthereumNetwork network = EthereumNetworkRegistry.resolve(appPreferences.getSelectedNetwork(), appPreferences);
        binding.textSelectedNetworkName.setText(network.getDisplayName());
        String rpcState = network.hasRpcUrl()
                ? getString(R.string.network_rpc_ready)
                : getString(R.string.network_rpc_missing);
        String suffix = network.isCustom() ? " - " + getString(R.string.network_custom_suffix) : "";
        binding.textSelectedNetworkMeta.setText(
                "Chain " + network.getChainId() + " - " + network.getNativeSymbol() + " - " + rpcState + suffix
        );
    }

    private String buildNetworkMeta(EthereumNetwork network) {
        String suffix = network.isCustom() ? " - " + getString(R.string.network_custom_suffix) : "";
        return "Chain " + network.getChainId() + " - " + network.getNativeSymbol() + suffix;
    }

    private void openGithubProfile() {
        String githubUrl = "https://github.com/rhmatzeka";
        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(githubUrl));
        startActivity(intent);
    }

    private String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private String normalizeExplorerBaseUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
