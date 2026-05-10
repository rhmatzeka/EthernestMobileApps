package id.rahmat.projekakhir.data.remote;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GdeltApi {

    @GET("api/v2/doc/doc")
    Call<JsonObject> searchNews(@Query("query") String query,
                                @Query("mode") String mode,
                                @Query("format") String format,
                                @Query("maxrecords") int maxRecords,
                                @Query("sort") String sort);
}
