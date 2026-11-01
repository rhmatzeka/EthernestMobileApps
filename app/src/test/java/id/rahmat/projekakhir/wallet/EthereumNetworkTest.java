package id.rahmat.projekakhir.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import id.rahmat.projekakhir.BuildConfig;

public class EthereumNetworkTest {

    @Test
    public void everyPresetHasAnRpcUrl() {
        // Without local.properties (e.g. the CI build) the public RPCs fill in.
        for (EthereumNetwork network : EthereumNetwork.getPresetNetworks()) {
            assertTrue(network.getDisplayName() + " has no RPC", network.hasRpcUrl());
            assertTrue(network.getRpcUrl().startsWith("https://"));
        }
    }

    @Test
    public void sepoliaUsesInfuraOnlyWhenAProjectIdIsSet() {
        String expected = BuildConfig.INFURA_PROJECT_ID.trim().isEmpty()
                ? BuildConfig.SEPOLIA_RPC_URL
                : "https://sepolia.infura.io/v3/" + BuildConfig.INFURA_PROJECT_ID.trim();
        assertEquals(expected, EthereumNetwork.SEPOLIA.getRpcUrl());
    }

    @Test
    public void unknownPresetKeyFallsBackToSepolia() {
        assertEquals(EthereumNetwork.SEPOLIA, EthereumNetwork.fromPresetKey("does-not-exist"));
    }
}
