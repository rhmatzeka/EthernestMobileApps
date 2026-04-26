package id.rahmat.projekakhir.utils;

import android.content.Context;
import android.content.SharedPreferences;

import id.rahmat.projekakhir.BuildConfig;

public class AppPreferences {

    public static final String NETWORK_SEPOLIA = "sepolia";
    public static final String NETWORK_MAINNET = "mainnet";
    public static final String NETWORK_BSC = "bsc";
    public static final String NETWORK_AVALANCHE = "avalanche";

    private static final String PREFS_NAME = "mats_wallet_prefs";
    private static final String KEY_ONBOARDING_DONE = "key_onboarding_done";
    private static final String KEY_BIOMETRIC_ENABLED = "key_biometric_enabled";
    private static final String KEY_SELECTED_NETWORK = "key_selected_network";
    private static final String KEY_CUSTOM_NETWORKS_JSON = "key_custom_networks_json";
    private static final String KEY_BALANCE_HIDDEN = "key_balance_hidden";
    private static final String KEY_ZERO_BALANCE_TOKENS_HIDDEN = "key_zero_balance_tokens_hidden";

    private final SharedPreferences sharedPreferences;

    public AppPreferences(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOnboardingDone() {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public void setOnboardingDone(boolean completed) {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply();
    }

    public boolean isBiometricEnabled() {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false);
    }

    public void setBiometricEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply();
    }

    public String getSelectedNetwork() {
        return sharedPreferences.getString(KEY_SELECTED_NETWORK, BuildConfig.DEFAULT_ETH_NETWORK);
    }

    public void setSelectedNetwork(String network) {
        sharedPreferences.edit().putString(KEY_SELECTED_NETWORK, network).apply();
    }

    public String getCustomNetworksJson() {
        return sharedPreferences.getString(KEY_CUSTOM_NETWORKS_JSON, "[]");
    }

    public void setCustomNetworksJson(String json) {
        sharedPreferences.edit().putString(KEY_CUSTOM_NETWORKS_JSON, json).apply();
    }

    public boolean isBalanceHidden() {
        return sharedPreferences.getBoolean(KEY_BALANCE_HIDDEN, false);
    }

    public void setBalanceHidden(boolean hidden) {
        sharedPreferences.edit().putBoolean(KEY_BALANCE_HIDDEN, hidden).apply();
    }

    public boolean isZeroBalanceTokensHidden() {
        return sharedPreferences.getBoolean(KEY_ZERO_BALANCE_TOKENS_HIDDEN, true);
    }

    public void setZeroBalanceTokensHidden(boolean hidden) {
        sharedPreferences.edit().putBoolean(KEY_ZERO_BALANCE_TOKENS_HIDDEN, hidden).apply();
    }

    public void clearSession() {
        sharedPreferences.edit().clear().apply();
    }
}
