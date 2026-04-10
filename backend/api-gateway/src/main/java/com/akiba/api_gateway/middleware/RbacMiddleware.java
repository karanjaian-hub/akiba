package com.akiba.api_gateway.middleware;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Checks that the authenticated user has the required permission
 * for the route. Call requirePermission("payments:send") in the router
 * to protect a specific route.
 */
public class RbacMiddleware {

  /**
   * Returns a handler that enforces a specific permission.
   * Usage in router: .handler(rbac.requirePermission("payments:send"))
   */
  public Handler<RoutingContext> requirePermission(String requiredPermission) {
    return ctx -> {
      JsonArray permissions = ctx.get("permissions");

      if (permissions == null) {
        rejectWith(ctx, 403, "No permissions found. Please log in again.");
        return;
      }

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

  /**
   * Enforces ROLE_ADMIN — used for admin-only routes.
   */
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
