package id.rahmat.projekakhir.data.remote;

import id.rahmat.projekakhir.data.remote.model.EtherscanTransactionsResponse;
import id.rahmat.projekakhir.data.remote.model.EtherscanNftTransfersResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/** Etherscan API V2 (https://docs.etherscan.io/v2-migration). */
public interface EtherscanApi {

    @GET(".")
    Call<EtherscanTransactionsResponse> getTransactions(@Query("chainid") long chainId,
                                                        @Query("module") String module,
                                                        @Query("action") String action,
                                                        @Query("address") String address,
                                                        @Query("sort") String sort,
                                                        @Query("apikey") String apiKey);

    @GET(".")
    Call<EtherscanNftTransfersResponse> getNftTransfers(@Query("chainid") long chainId,
                                                        @Query("module") String module,
                                                        @Query("action") String action,
                                                        @Query("address") String address,
                                                        @Query("page") int page,
                                                        @Query("offset") int offset,
                                                        @Query("startblock") long startBlock,
                                                        @Query("endblock") long endBlock,
                                                        @Query("sort") String sort,
                                                        @Query("apikey") String apiKey);
}
