package com.akiba.auth.handlers;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.akiba.auth.services.MailService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.RedisAPI;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

import java.security.SecureRandom;
import java.util.UUID;

public class RegisterHandler {

  private final Pool pgPool;
  private final RedisAPI redis;
  private final MailService mailService;

  private static final int OTP_TTL_SECONDS = 86400; // 24 hours

  public RegisterHandler(Pool pgPool, RedisAPI redis, MailService mailService) {
    this.pgPool      = pgPool;
    this.redis       = redis;
    this.mailService = mailService;
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
      .compose(v -> mailService.sendVerificationOtp(email, fullName, otp))
      .onSuccess(v -> ctx.response()
        .setStatusCode(201)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject()
          .put("message", "Registration successful. Please check your email to verify your account.")
          .put("userId", userId)
          .encode()))
      .onFailure(err -> {
        err.printStackTrace();
        System.err.println("[RegisterHandler] ❌ " + err.getClass().getSimpleName() + ": " + err.getMessage());
        rejectWith(ctx, 500, "Registration failed. Please try again.");
      });
  }

  private Future<Void> saveUser(String userId, String fullName,
                                String email, String phone, String passwordHash) {
    String sql = """
      INSERT INTO auth.users (id, full_name, email, phone, password_hash, role_id, status)
      VALUES ($1, $2, $3, $4, $5,
        (SELECT id FROM auth.roles WHERE name = 'ROLE_USER'),
        'PENDING_VERIFICATION')
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId), fullName, email, phone, passwordHash))
      .mapEmpty();
  }

  private Future<Void> storeOtpInRedis(String userId, String otp) {
    String key = "email_verify:" + userId;
    return redis.setex(key, String.valueOf(OTP_TTL_SECONDS), otp).mapEmpty();
  }

  private String generateOtp() {
    return String.format("%06d", new SecureRandom().nextInt(999999));
  }

  private String normalizePhone(String phone) {
    if (phone == null) return null;
    phone = phone.trim().replaceAll("\\s+", "");
    if (phone.matches("^0[17]\\d{8}$"))       return "+254" + phone.substring(1);
    if (phone.matches("^\\+254[17]\\d{8}$"))  return phone;
    if (phone.matches("^254[17]\\d{8}$"))     return "+" + phone;
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
