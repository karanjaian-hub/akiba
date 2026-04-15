// verticles/HttpVerticle.java
package com.akiba.notification.verticles;

import com.akiba.notification.handlers.AlertHandler;
import com.akiba.notification.handlers.HealthCheckHandler;
import com.akiba.notification.repositories.PreferencesRepository;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.UUID;

public class HttpVerticle extends AbstractVerticle {

    private final AlertHandler alertHandler;
    private final PreferencesRepository prefsRepo;
    private final int port;
    private final HealthCheckHandler healthCheck;

  public HttpVerticle(AlertHandler alertHandler, PreferencesRepository prefsRepo,
                      int port, HealthCheckHandler healthCheck) {
    this.alertHandler = alertHandler;
    this.prefsRepo    = prefsRepo;
    this.port         = port;
    this.healthCheck  = healthCheck;
  }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Routes
        router.get("/notifications").handler(this::handleGetAlerts);
        router.put("/notifications/:id/read").handler(this::handleMarkOneRead);
        router.put("/notifications/read-all").handler(this::handleMarkAllRead);
        router.get("/notifications/unread-count").handler(this::handleUnreadCount);
        router.get("/notifications/preferences").handler(this::handleGetPreferences);
        router.put("/notifications/preferences").handler(this::handleUpdatePreferences);
      router.get("/health").handler(healthCheck::handle);


      vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
            .onSuccess(v -> {
                System.out.println("[NotificationService] HTTP listening on port " + port);
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    // ─── Route Handlers ────────────────────────────────────────────────────────

    private void handleGetAlerts(RoutingContext ctx) {
        UUID userId  = extractUserId(ctx);
        int page     = intParam(ctx, "page", 1);
        int pageSize = intParam(ctx, "page_size", 20);

        if (userId == null) return; // extractUserId already sent 401

        alertHandler.getAlerts(userId, page, pageSize)
            .onSuccess(result -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(result.encode()))
            .onFailure(err -> sendError(ctx, 500, err.getMessage()));
    }

    private void handleMarkOneRead(RoutingContext ctx) {
        UUID userId  = extractUserId(ctx);
        UUID alertId = parseUUID(ctx.pathParam("id"), ctx);
        if (userId == null || alertId == null) return;

        alertHandler.markOneRead(alertId, userId)
            .onSuccess(v -> ctx.response().setStatusCode(204).end())
            .onFailure(err -> sendError(ctx, 500, err.getMessage()));
    }

    private void handleMarkAllRead(RoutingContext ctx) {
        UUID userId = extractUserId(ctx);
        if (userId == null) return;

        alertHandler.markAllRead(userId)
            .onSuccess(v -> ctx.response().setStatusCode(204).end())
            .onFailure(err -> sendError(ctx, 500, err.getMessage()));
    }

    private void handleUnreadCount(RoutingContext ctx) {
        UUID userId = extractUserId(ctx);
        if (userId == null) return;

        alertHandler.getUnreadCount(userId)
            .onSuccess(result -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(result.encode()))
            .onFailure(err -> sendError(ctx, 500, err.getMessage()));
    }

    private void handleGetPreferences(RoutingContext ctx) {
        UUID userId = extractUserId(ctx);
        if (userId == null) return;

        prefsRepo.getPreferences(userId)
            .onSuccess(prefs -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(prefs.encode()))
            .onFailure(err -> sendError(ctx, 500, err.getMessage()));
    }

    private void handleUpdatePreferences(RoutingContext ctx) {
        UUID userId = extractUserId(ctx);
        if (userId == null) return;

        JsonObject body = ctx.body().asJsonObject();
        prefsRepo.upsertPreferences(userId, body)
            .onSuccess(v -> ctx.response().setStatusCode(204).end())
            .onFailure(err -> sendError(ctx, 500, err.getMessage()));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** In production this reads the verified JWT claim set by the API Gateway. */
    private UUID extractUserId(RoutingContext ctx) {
        String userIdHeader = ctx.request().getHeader("X-User-Id"); // set by API Gateway
        if (userIdHeader == null) {
            sendError(ctx, 401, "Missing X-User-Id header");
            return null;
        }
        return UUID.fromString(userIdHeader);
    }

    private UUID parseUUID(String raw, RoutingContext ctx) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            sendError(ctx, 400, "Invalid UUID: " + raw);
            return null;
        }
    }

    private int intParam(RoutingContext ctx, String name, int defaultVal) {
        String val = ctx.request().getParam(name);
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultVal; }
    }

    private void sendError(RoutingContext ctx, int status, String message) {
        ctx.response()
            .setStatusCode(status)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", message).encode());
    }
}
