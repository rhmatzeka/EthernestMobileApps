package id.rahmat.projekakhir.data.repository;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import id.rahmat.projekakhir.BuildConfig;
import id.rahmat.projekakhir.data.remote.EtherscanApi;
import id.rahmat.projekakhir.data.remote.model.EtherscanNftTransferItem;
import id.rahmat.projekakhir.data.remote.model.EtherscanNftTransfersResponse;
import id.rahmat.projekakhir.utils.AppExecutors;
import id.rahmat.projekakhir.utils.AppPreferences;
import id.rahmat.projekakhir.wallet.EthereumNetwork;
import id.rahmat.projekakhir.wallet.EthereumNetworkRegistry;
import id.rahmat.projekakhir.wallet.EthereumService;
import id.rahmat.projekakhir.wallet.NftAsset;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class NftRepository {

    private static final int NFT_TRANSFER_LIMIT = 100;
    private static final int NFT_METADATA_LIMIT = 20;
    private static final long ETHERSCAN_LAST_BLOCK = 99_999_999L;

    private static final long METADATA_TIMEOUT_SECONDS = 15L;

    private final EtherscanApi etherscanApi;
    private final AppPreferences appPreferences;
    private final EthereumService ethereumService;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();
    // Token metadata rarely changes, so keep it for the app session.
    private final Map<String, NftMetadata> metadataCache = new ConcurrentHashMap<>();

    public NftRepository(EtherscanApi etherscanApi, AppPreferences appPreferences,
                         EthereumService ethereumService, OkHttpClient httpClient) {
        this.etherscanApi = etherscanApi;
        this.appPreferences = appPreferences;
        this.ethereumService = ethereumService;
        this.httpClient = httpClient;
    }

    public List<NftAsset> loadOwnedNfts(String walletAddress) throws Exception {
        if (walletAddress == null || walletAddress.isEmpty() || !TransactionRepository.hasEtherscanKey()) {
            return Collections.emptyList();
        }

        EthereumNetwork network = EthereumNetworkRegistry.resolve(appPreferences.getSelectedNetwork(), appPreferences);
        if (!network.supportsExplorerSync()) {
            return Collections.emptyList();
        }
        JsonArray erc721Transfers = fetchNftTransfers(network, walletAddress, "tokennfttx");
        JsonArray erc1155Transfers = fetchNftTransfers(network, walletAddress, "token1155tx");

        List<NftAsset> erc721 = inferOwnedErc721Nfts(walletAddress, erc721Transfers, network);
        List<NftAsset> erc1155 = inferOwnedErc1155Nfts(walletAddress, erc1155Transfers, network);
        List<NftAsset> ownedNfts = new ArrayList<>(erc721.size() + erc1155.size());
        ownedNfts.addAll(erc721);
        ownedNfts.addAll(erc1155);
        return enrichWithMetadata(ownedNfts, erc721.size(), network);
    }

    private JsonArray fetchNftTransfers(EthereumNetwork network, String walletAddress, String action) throws Exception {
        Response<EtherscanNftTransfersResponse> response = etherscanApi.getNftTransfers(
                network.getChainId(),
                "account",
                action,
                walletAddress,
                1,
                NFT_TRANSFER_LIMIT,
                0,
                ETHERSCAN_LAST_BLOCK,
                "asc",
                BuildConfig.ETHERSCAN_API_KEY
        ).execute();

        EtherscanNftTransfersResponse body = response.body();
        if (body == null || body.result == null || !body.result.isJsonArray()) {
            return new JsonArray();
        }
        return body.result.getAsJsonArray();
    }

    private List<NftAsset> inferOwnedErc721Nfts(String walletAddress, JsonArray transfers, EthereumNetwork network) {
        Map<String, NftAsset> ownedNfts = new LinkedHashMap<>();
        for (JsonElement element : transfers) {
            EtherscanNftTransferItem transfer = gson.fromJson(element, EtherscanNftTransferItem.class);
            if (transfer == null || isBlank(transfer.contractAddress) || isBlank(transfer.tokenId)) {
                continue;
            }

            String key = transfer.contractAddress.toLowerCase() + ":" + transfer.tokenId;
            if (walletAddress.equalsIgnoreCase(transfer.to)) {
                ownedNfts.put(key, new NftAsset(
                        fallbackCollectionName(transfer),
                        transfer.tokenSymbol == null ? "" : transfer.tokenSymbol,
                        transfer.tokenId,
                        transfer.contractAddress,
                        network.getDisplayName(),
                        ""
                ));
            } else if (walletAddress.equalsIgnoreCase(transfer.from)) {
                ownedNfts.remove(key);
            }
        }
        return new ArrayList<>(ownedNfts.values());
    }

    private List<NftAsset> inferOwnedErc1155Nfts(String walletAddress, JsonArray transfers, EthereumNetwork network) {
        Map<String, Erc1155Holding> holdings = new LinkedHashMap<>();
        for (JsonElement element : transfers) {
            EtherscanNftTransferItem transfer = gson.fromJson(element, EtherscanNftTransferItem.class);
            if (transfer == null || isBlank(transfer.contractAddress) || isBlank(transfer.tokenId)) {
                continue;
            }

            String key = transfer.contractAddress.toLowerCase() + ":" + transfer.tokenId;
            Erc1155Holding holding = holdings.get(key);
            if (holding == null) {
                holding = new Erc1155Holding(transfer);
                holdings.put(key, holding);
            }

            BigInteger value = parseTokenValue(transfer.tokenValue);
            if (walletAddress.equalsIgnoreCase(transfer.to)) {
                holding.balance = holding.balance.add(value);
                holding.latestTransfer = transfer;
            }
            if (walletAddress.equalsIgnoreCase(transfer.from)) {
                holding.balance = holding.balance.subtract(value);
                holding.latestTransfer = transfer;
            }
        }

        List<NftAsset> ownedNfts = new ArrayList<>();
        for (Erc1155Holding holding : holdings.values()) {
            if (holding.balance.compareTo(BigInteger.ZERO) <= 0) {
                continue;
            }
            EtherscanNftTransferItem transfer = holding.latestTransfer;
            ownedNfts.add(new NftAsset(
                    fallbackCollectionName(transfer),
                    transfer.tokenSymbol == null ? "" : transfer.tokenSymbol,
                    transfer.tokenId,
                    transfer.contractAddress,
                    network.getDisplayName(),
                    ""
            ));
        }
        return ownedNfts;
    }

    private BigInteger parseTokenValue(String value) {
        if (isBlank(value)) {
            return BigInteger.ONE;
        }
        try {
            return new BigInteger(value.trim());
        } catch (NumberFormatException ignored) {
            return BigInteger.ONE;
        }
    }

    /**
     * Loads metadata for the first NFT_METADATA_LIMIT items in parallel (it used
     * to be one RPC call plus one HTTP request after another, per NFT).
     * Items before {@code erc1155Start} are ERC-721, the rest ERC-1155.
     */
    private List<NftAsset> enrichWithMetadata(List<NftAsset> nfts, int erc1155Start, EthereumNetwork network) {
        int count = Math.min(nfts.size(), NFT_METADATA_LIMIT);
        List<CompletableFuture<NftMetadata>> futures = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            NftAsset nft = nfts.get(i);
            boolean erc1155 = i >= erc1155Start;
            futures.add(CompletableFuture.supplyAsync(
                    () -> loadMetadataCached(nft, erc1155, network), AppExecutors.network()));
        }

        List<NftAsset> enriched = new ArrayList<>(nfts.size());
        for (int i = 0; i < nfts.size(); i++) {
            NftAsset nft = nfts.get(i);
            if (i >= count) {
                enriched.add(nft);
                continue;
            }

            NftMetadata metadata = awaitMetadata(futures.get(i));
            enriched.add(new NftAsset(
                    isBlank(metadata.name) ? nft.collectionName : metadata.name,
                    nft.tokenSymbol,
                    nft.tokenId,
                    nft.contractAddress,
                    nft.networkName,
                    metadata.imageUrl
            ));
        }
        return enriched;
    }

    private NftMetadata awaitMetadata(CompletableFuture<NftMetadata> future) {
        try {
            return future.get(METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            future.cancel(true);
            return NftMetadata.empty();
        }
    }

    private NftMetadata loadMetadataCached(NftAsset nft, boolean erc1155, EthereumNetwork network) {
        String key = network.getChainId() + ":" + nft.contractAddress.toLowerCase(Locale.US) + ":" + nft.tokenId;
        NftMetadata cached = metadataCache.get(key);
        if (cached != null) {
            return cached;
        }
        NftMetadata metadata = loadMetadataSafely(nft, erc1155, network);
        if (!metadata.isEmpty()) {
            metadataCache.put(key, metadata);
        }
        return metadata;
    }

    private NftMetadata loadMetadataSafely(NftAsset nft, boolean erc1155, EthereumNetwork network) {
        try {
            String tokenUri = erc1155
                    ? ethereumService.getErc1155Uri(nft.contractAddress, nft.tokenId, network)
                    : ethereumService.getErc721TokenUri(nft.contractAddress, nft.tokenId, network);
            String metadataUrl = normalizeTokenUri(tokenUri);
            if (isBlank(metadataUrl) || !metadataUrl.startsWith("http")) {
                return NftMetadata.empty();
            }

            Request request = new Request.Builder().url(metadataUrl).build();
            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    return NftMetadata.empty();
                }
                JsonObject json = gson.fromJson(body.string(), JsonObject.class);
                if (json == null) {
                    return NftMetadata.empty();
                }
                String name = getJsonString(json, "name");
                String imageUrl = normalizeTokenUri(getJsonString(json, "image"));
                return new NftMetadata(name, imageUrl);
            }
        } catch (Exception ignored) {
            return NftMetadata.empty();
        }
    }

    private String normalizeTokenUri(String value) {
        if (isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("ipfs://ipfs/")) {
            return "https://ipfs.io/ipfs/" + trimmed.substring("ipfs://ipfs/".length());
        }
        if (trimmed.startsWith("ipfs://")) {
            return "https://ipfs.io/ipfs/" + trimmed.substring("ipfs://".length());
        }
        return trimmed;
    }

    private String getJsonString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

    private String fallbackCollectionName(EtherscanNftTransferItem transfer) {
        if (!isBlank(transfer.tokenName)) {
            return transfer.tokenName;
        }
        if (!isBlank(transfer.tokenSymbol)) {
            return transfer.tokenSymbol;
        }
        return "NFT Collection";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class NftMetadata {
        final String name;
        final String imageUrl;

        NftMetadata(String name, String imageUrl) {
            this.name = name;
            this.imageUrl = imageUrl;
        }

        static NftMetadata empty() {
            return new NftMetadata("", "");
        }

        boolean isEmpty() {
            return (name == null || name.isEmpty()) && (imageUrl == null || imageUrl.isEmpty());
        }
    }

    private static class Erc1155Holding {
        BigInteger balance = BigInteger.ZERO;
        EtherscanNftTransferItem latestTransfer;

        Erc1155Holding(EtherscanNftTransferItem latestTransfer) {
            this.latestTransfer = latestTransfer;
        }
    }
}
