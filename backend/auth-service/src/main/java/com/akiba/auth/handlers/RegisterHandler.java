package com.akiba.auth.handlers;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.redis.client.RedisAPI;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

import java.security.SecureRandom;
import java.util.UUID;

public class RegisterHandler {

  private final Pool pgPool;
  private final RedisAPI redis;
  private final WebClient webClient;

  private static final int OTP_TTL_SECONDS = 86400;

  public RegisterHandler(Pool pgPool, RedisAPI redis, io.vertx.core.Vertx vertx, WebClient webClient) {
    this.pgPool     = pgPool;
    this.redis      = redis;
    this.webClient  = webClient;
  }

  public void handle(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    if (body == null) {
      rejectWith(ctx, 400, "Request body is required");
      return;
    }

    String fullName = body.getString("fullName");
    String email    = body.getString("email");
    String phone    = body.getString("phone");
    String password = body.getString("password");

    if (!isValidInput(fullName, email, phone, password)) {
      rejectWith(ctx, 400, "fullName, email, phone and password are required");
      return;
    }

    String normalizedPhone = normalizePhone(phone);
    if (normalizedPhone == null) {
      rejectWith(ctx, 400, "Invalid phone number format. Use 07XXXXXXXX or +2547XXXXXXXX");
      return;
    }

    String passwordHash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
    String userId       = UUID.randomUUID().toString();
    String otp          = generateOtp();

    saveUser(userId, fullName, email, normalizedPhone, passwordHash)
      .compose(v -> storeOtpInRedis(userId, otp))
      .compose(v -> sendOtpSms(normalizedPhone, otp))
      .compose(v -> sendVerificationEmail(userId, email, fullName))
      .onSuccess(v -> ctx.response()
        .setStatusCode(201)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject()
          .put("message", "Registration successful. Check your phone and email to verify your account.")
          .put("userId", userId)
          .encode()))
      .onFailure(err -> {
        System.err.println("[RegisterHandler] ❌ " + err.getClass().getSimpleName() + ": " + err.getMessage());
        rejectWith(ctx, 500, "Registration failed: " + err.getMessage());
      });
  }

  // ─── DB ───────────────────────────────────────────────────────────────────

  private Future<Void> saveUser(String userId, String fullName,
                                String email, String phone, String passwordHash) {
    String sql = """
      INSERT INTO auth.users (id, full_name, email, phone, password_hash, role_id)
      VALUES ($1, $2, $3, $4, $5,
        (SELECT id FROM auth.roles WHERE name = 'ROLE_USER'))
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId), fullName, email, phone, passwordHash))
      .mapEmpty();
  }

  // ─── Redis ────────────────────────────────────────────────────────────────

  private Future<Void> storeOtpInRedis(String userId, String otp) {
    String key = "otp:" + userId + ":phone";
    return redis.setex(key, String.valueOf(OTP_TTL_SECONDS), otp).mapEmpty();
  }

  // ─── SMS ──────────────────────────────────────────────────────────────────

  private Future<Void> sendOtpSms(String phone, String otp) {
    String apiKey   = System.getenv("AT_API_KEY");
    String username = System.getenv("AT_USERNAME");

    if (apiKey == null || apiKey.isBlank()) {
      System.err.println("[RegisterHandler] ❌ AT_API_KEY is not set");
      return Future.failedFuture("SMS service misconfigured: AT_API_KEY is missing");
    }
    if (username == null || username.isBlank()) {
      System.err.println("[RegisterHandler] ❌ AT_USERNAME is not set");
      return Future.failedFuture("SMS service misconfigured: AT_USERNAME is missing");
    }

    String body = "username=" + username
      + "&to=" + phone
      + "&message=Your+Akiba+verification+code+is+" + otp + ".+It+expires+in+5+minutes.";

    return webClient.postAbs("https://api.sandbox.africastalking.com/version1/messaging")
      .putHeader("apiKey", apiKey)
      .putHeader("Accept", "application/json")
      .putHeader("Content-Type", "application/x-www-form-urlencoded")
      .sendBuffer(Buffer.buffer(body))
      .compose(response -> {
        System.out.println("[RegisterHandler] AT response (" + response.statusCode() + "): " + response.bodyAsString());
        if (response.statusCode() != 201) {
          return Future.failedFuture("SMS delivery failed: " + response.bodyAsString());
        }
        System.out.println("[RegisterHandler] ✅ OTP SMS sent to " + phone);
        return Future.<Void>succeededFuture();
      });
  }

  // ─── Email ────────────────────────────────────────────────────────────────

  private Future<Void> sendVerificationEmail(String userId, String email, String fullName) {
    // TODO: implement email sending (e.g. via SendGrid or Mailgun)
    System.out.println("[RegisterHandler] 📧 Email verification pending → userId=" + userId + ", email=" + email);
    return Future.succeededFuture();
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private String generateOtp() {
    return String.format("%06d", new SecureRandom().nextInt(999999));
  }

  private String normalizePhone(String phone) {
    if (phone == null) return null;
    phone = phone.trim().replaceAll("\\s+", "");
    if (phone.matches("^0[17]\\d{8}$"))        return "+254" + phone.substring(1);
    if (phone.matches("^\\+254[17]\\d{8}$"))   return phone;
    if (phone.matches("^254[17]\\d{8}$"))      return "+" + phone;
    return null;
  }

  private boolean isValidInput(String... fields) {
    for (String field : fields) {
      if (field == null || field.isBlank()) return false;
    }
    return true;
  }

  private void rejectWith(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
