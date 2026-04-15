// services/PushNotificationService.java
package com.akiba.notification.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

public class PushNotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private final WebClient webClient;

    public PushNotificationService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Sends an Expo push notification.
     * Returns a completed future even on push failure — a failed push
     * should NOT roll back a DB save. We log and move on.
     */
    public Future<Void> send(String pushToken, String title, String body) {
        if (pushToken == null || pushToken.isBlank()) {
            // User hasn't registered a device yet — skip silently
            return Future.succeededFuture();
        }

        JsonObject payload = new JsonObject()
            .put("to",    pushToken)
            .put("title", title)
            .put("body",  body)
            .put("sound", "default");

        return webClient.postAbs(EXPO_PUSH_URL)
            .sendJsonObject(payload)
            .onFailure(err -> System.err.println("[PushService] Expo request failed: " + err.getMessage()))
            .mapEmpty();
    }
}
