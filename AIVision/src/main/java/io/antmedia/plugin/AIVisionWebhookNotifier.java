package io.antmedia.plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

public class AIVisionWebhookNotifier {

	private static final Logger logger = LoggerFactory.getLogger(AIVisionWebhookNotifier.class);
	private final Gson gson = new Gson();
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	public void notify(String webhookUrl, AIVisionAnalysisResponse response) {
		if (webhookUrl == null || webhookUrl.isBlank()) {
			return;
		}

		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(webhookUrl))
					.timeout(Duration.ofSeconds(15))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(gson.toJson(response)))
					.build();
			httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
					.whenComplete((webhookResponse, error) -> {
						if (error != null) {
							logger.warn("AI Vision alert webhook request failed", error);
						}
						else if (webhookResponse.statusCode() < 200 || webhookResponse.statusCode() >= 300) {
							logger.warn("AI Vision alert webhook returned HTTP {}", webhookResponse.statusCode());
						}
					});
		}
		catch (IllegalArgumentException e) {
			logger.warn("Invalid AI Vision webhook URL: {}", webhookUrl);
		}
	}
}
