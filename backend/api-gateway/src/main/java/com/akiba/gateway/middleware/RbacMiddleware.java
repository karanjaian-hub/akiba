package com.akiba.gateway.middleware;


import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Checks the authenticated user has the required permission for a route.
 * Usage: .handler(rbac.requirePermission("payments:send"))
 */
public class RbacMiddleware {

  public Handler<RoutingContext> requirePermission(String requiredPermission) {
    return ctx -> {
      JsonArray permissions = ctx.get("permissions");

      if (permissions == null) {
        rejectWith(ctx, 403, "No permissions found. Please log in again.");
        return;
      }

      // Java 21 stream — check if required permission exists in claims
      boolean hasPermission = permissions.stream()
        .anyMatch(p -> requiredPermission.equals(p.toString()));

      if (!hasPermission) {
        System.err.printf("[RbacMiddleware] ❌ userId=%s lacks permission=%s%n",
          ctx.get("userId"), requiredPermission);
        rejectWith(ctx, 403, "Forbidden — you do not have permission to perform this action.");
        return;
      }

      ctx.next();
    };
  }

  public Handler<RoutingContext> requireAdmin() {
    return ctx -> {
      String role = ctx.get("role");
      if (!"ROLE_ADMIN".equals(role)) {
        rejectWith(ctx, 403, "Forbidden — admin access required.");
        return;
      }
      ctx.next();
    };
  }

  private void rejectWith(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
