package id.rahmat.projekakhir.data.remote.model;

import com.google.gson.annotations.SerializedName;

public class EtherscanNftTransferItem {

    @SerializedName("hash")
    public String hash;

    @SerializedName("from")
    public String from;

    @SerializedName("to")
    public String to;

    @SerializedName("contractAddress")
    public String contractAddress;

    @SerializedName("tokenID")
    public String tokenId;

    @SerializedName("tokenName")
    public String tokenName;

    @SerializedName("tokenSymbol")
    public String tokenSymbol;

    @SerializedName("tokenValue")
    public String tokenValue;

    @SerializedName("timeStamp")
    public String timeStamp;
}
