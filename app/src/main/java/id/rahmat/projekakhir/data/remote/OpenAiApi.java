package id.rahmat.projekakhir.data.remote;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface OpenAiApi {

    @POST("v1/responses")
    Call<JsonObject> createResponse(@Header("Authorization") String authorization,
                                    @Body JsonObject body);
}
