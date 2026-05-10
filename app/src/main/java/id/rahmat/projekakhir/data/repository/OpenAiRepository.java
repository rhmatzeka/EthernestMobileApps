package id.rahmat.projekakhir.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;

import id.rahmat.projekakhir.BuildConfig;
import id.rahmat.projekakhir.data.remote.OpenAiApi;
import retrofit2.Response;

public class OpenAiRepository {

    private final OpenAiApi openAiApi;

    public OpenAiRepository(OpenAiApi openAiApi) {
        this.openAiApi = openAiApi;
    }

    public boolean isConfigured() {
        return BuildConfig.OPENAI_API_KEY != null
                && !BuildConfig.OPENAI_API_KEY.trim().isEmpty()
                && !BuildConfig.OPENAI_API_KEY.startsWith("your_");
    }

    public String ask(String question, String appContext) throws IOException {
        if (!isConfigured()) {
            return "";
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", safeModel());
        body.addProperty("instructions", buildInstructions());
        body.addProperty("input", buildInput(question, appContext));
        body.addProperty("max_output_tokens", 700);

        Response<JsonObject> response = openAiApi
                .createResponse("Bearer " + BuildConfig.OPENAI_API_KEY.trim(), body)
                .execute();
        if (response == null || !response.isSuccessful() || response.body() == null) {
            return "";
        }
        return extractText(response.body()).trim();
    }

    private String safeModel() {
        String model = BuildConfig.OPENAI_MODEL == null ? "" : BuildConfig.OPENAI_MODEL.trim();
        return model.isEmpty() ? "gpt-4.1-mini" : model;
    }

    private String buildInstructions() {
        return "Kamu adalah AI Ethernest, asisten wallet crypto untuk pemula. "
                + "Jawab dalam Bahasa Indonesia yang jelas, natural, dan tidak kaku. "
                + "Kamu boleh menjawab pertanyaan umum seperti sapaan, penjelasan fitur, berita, stablecoin, risiko, dan crypto. "
                + "Kalau pertanyaan menyentuh keputusan finansial, jangan menjanjikan untung atau aman 100%; jelaskan risiko dan berikan langkah defensif. "
                + "Gunakan konteks berita dan sinyal stablecoin yang diberikan jika relevan. "
                + "Kalau user hanya menyapa, balas ramah dan tawarkan bantuan singkat. "
                + "Jaga jawaban maksimal 4 paragraf pendek kecuali user meminta detail.";
    }

    private String buildInput(String question, String appContext) {
        return "Konteks dari aplikasi:\n"
                + appContext
                + "\n\nPertanyaan user:\n"
                + (question == null ? "" : question.trim());
    }

    private String extractText(JsonObject response) {
        if (response.has("output_text") && !response.get("output_text").isJsonNull()) {
            return response.get("output_text").getAsString();
        }
        JsonArray output = response.getAsJsonArray("output");
        if (output == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonElement outputElement : output) {
            if (!outputElement.isJsonObject()) {
                continue;
            }
            JsonArray content = outputElement.getAsJsonObject().getAsJsonArray("content");
            if (content == null) {
                continue;
            }
            for (JsonElement contentElement : content) {
                if (!contentElement.isJsonObject()) {
                    continue;
                }
                JsonObject contentObject = contentElement.getAsJsonObject();
                if (contentObject.has("text") && !contentObject.get("text").isJsonNull()) {
                    builder.append(contentObject.get("text").getAsString());
                }
            }
        }
        return builder.toString();
    }
}
