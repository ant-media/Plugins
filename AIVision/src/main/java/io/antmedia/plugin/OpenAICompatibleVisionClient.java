package io.antmedia.plugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class OpenAICompatibleVisionClient {

	private final Gson gson = new Gson();
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();

	public String analyze(AIVisionSettings settings, String prompt, File imageFile) throws IOException, InterruptedException {
		String imageDataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(imageFile.toPath()));
		JsonObject requestBody = createRequestBody(settings.getModel(), prompt, imageDataUrl);

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(settings.getBaseUrl() + "/chat/completions"))
				.timeout(Duration.ofSeconds(90))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)));

		if (settings.getToken() != null && !settings.getToken().isBlank()) {
			requestBuilder.header("Authorization", "Bearer " + settings.getToken());
		}

		HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("AI service returned HTTP " + response.statusCode() + ": " + response.body());
		}

		return parseAssistantText(response.body());
	}

	private JsonObject createRequestBody(String model, String prompt, String imageDataUrl) {
		JsonObject root = new JsonObject();
		root.addProperty("model", model);

		JsonArray messages = new JsonArray();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");

		JsonArray content = new JsonArray();
		JsonObject textPart = new JsonObject();
		textPart.addProperty("type", "text");
		textPart.addProperty("text", prompt);
		content.add(textPart);

		JsonObject imageUrl = new JsonObject();
		imageUrl.addProperty("url", imageDataUrl);
		JsonObject imagePart = new JsonObject();
		imagePart.addProperty("type", "image_url");
		imagePart.add("image_url", imageUrl);
		content.add(imagePart);

		userMessage.add("content", content);
		messages.add(userMessage);
		root.add("messages", messages);
		root.addProperty("temperature", 0.2);
		return root;
	}

	private String parseAssistantText(String responseBody) throws IOException {
		JsonObject root = gson.fromJson(responseBody, JsonObject.class);
		JsonArray choices = root != null ? root.getAsJsonArray("choices") : null;
		if (choices == null || choices.isEmpty()) {
			throw new IOException("AI service response does not contain choices");
		}

		JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
		if (message == null || !message.has("content")) {
			throw new IOException("AI service response does not contain assistant content");
		}

		if (message.get("content").isJsonArray()) {
			StringBuilder builder = new StringBuilder();
			for (var element : message.getAsJsonArray("content")) {
				if (element.isJsonObject() && element.getAsJsonObject().has("text")) {
					builder.append(element.getAsJsonObject().get("text").getAsString());
				}
			}
			return builder.toString();
		}

		return message.get("content").getAsString();
	}
}
