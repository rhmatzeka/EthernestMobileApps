package id.rahmat.projekakhir.data.remote.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

public class EtherscanTransactionsResponse {

    @SerializedName("status")
    public String status;

    @SerializedName("message")
    public String message;

    /** An array of transactions, or an error message string when status is "0". */
    @SerializedName("result")
    public JsonElement result;
}
