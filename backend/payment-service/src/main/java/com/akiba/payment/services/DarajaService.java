package com.akiba.payment.services;

import com.akiba.payment.config.PaymentConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class DarajaService {

  private static final String DARAJA_BASE_URL = "https://sandbox.safaricom.co.ke";
  private static final String TOKEN_CACHE_KEY  = "daraja:token";
  private static final int    TOKEN_TTL_SEC    = 3500; // slightly under Daraja's 3600s

  private final WebClient webClient;
  private final RedisAPI  redis;
  private final String    consumerKey;
  private final String    consumerSecret;
  private final String    shortcode;
  private final String    passkey;
  private final String    callbackUrl;

  public DarajaService(Vertx vertx, Redis redisClient) {
    this.webClient      = WebClient.create(vertx);
    this.redis          = RedisAPI.api(redisClient);
    this.consumerKey    = PaymentConfig.darajaConsumerKey();
    this.consumerSecret = PaymentConfig.darajaConsumerSecret();
    this.shortcode      = PaymentConfig.darajaShortcode();
    this.passkey        = PaymentConfig.darajaPasskey();
    this.callbackUrl    = PaymentConfig.darajaCallbackUrl();
  }

  // ── Public API
  public Future<JsonObject> initiateStkPush(String phone, int amount, String accountRef, String description) {
    return getAccessToken()
      .compose(token -> sendStkPushRequest(token, phone, amount, accountRef, description));
  }

  // Token management
  private Future<String> getAccessToken() {
    return redis.get(TOKEN_CACHE_KEY)
      .compose(cached -> {
        if (cached != null) {
          return Future.succeededFuture(cached.toString());
        }
        return fetchFreshToken();
      });
  }

  private Future<String> fetchFreshToken() {
    String credentials = Base64.getEncoder().encodeToString(
      (consumerKey + ":" + consumerSecret).getBytes(StandardCharsets.UTF_8)
    );

    return webClient.getAbs(DARAJA_BASE_URL + "/oauth/v1/generate?grant_type=client_credentials")
      .putHeader("Authorization", "Basic " + credentials)
      .send()
      .compose(response -> {
        int    httpStatus = response.statusCode();
        String rawBody    = response.bodyAsString();

        System.out.println("[payment-service] Daraja token response HTTP " + httpStatus + ": " + rawBody);

        if (httpStatus != 200) {
          return Future.failedFuture(
            "Daraja token fetch failed: HTTP " + httpStatus + " — " + rawBody
          );
        }

        JsonObject json;
        try {
          json = new JsonObject(rawBody);
        } catch (Exception e) {
          return Future.failedFuture("Daraja token endpoint returned non-JSON: " + rawBody);
        }

        String token = json.getString("access_token");
        if (token == null || token.isBlank()) {
          return Future.failedFuture("Daraja token response missing access_token: " + rawBody);
        }

        // Cache the token — fire-and-forget, don't block on cache write
        redis.setex(TOKEN_CACHE_KEY, String.valueOf(TOKEN_TTL_SEC), token);
        return Future.succeededFuture(token);
      });
  }

  // STK Push
  private Future<JsonObject> sendStkPushRequest(
    String token, String phone, int amount, String accountRef, String description) {

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    // Daraja requires: Base64(shortcode + passkey + timestamp)
    String password  = Base64.getEncoder().encodeToString(
      (shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8)
    );

    JsonObject body = new JsonObject()
      .put("BusinessShortCode", shortcode)
      .put("Password",          password)
      .put("Timestamp",         timestamp)
      .put("TransactionType",   "CustomerPayBillOnline")
      .put("Amount",            amount)
      .put("PartyA",            phone)         // sender — the user's phone
      .put("PartyB",            shortcode)     // receiver — our shortcode
      .put("PhoneNumber",       phone)
      .put("CallBackURL",       callbackUrl)
      .put("AccountReference",  accountRef)
      .put("TransactionDesc",   description);

    return webClient.postAbs(DARAJA_BASE_URL + "/mpesa/stkpush/v1/processrequest")
      .putHeader("Authorization",  "Bearer " + token)
      .putHeader("Content-Type",   "application/json")
      .sendJsonObject(body)
      .compose(response -> {
        int    httpStatus = response.statusCode();
        String rawBody    = response.bodyAsString();

        // Log everything — raw HTTP status + raw body — before we touch the JSON.
        // WHY: bodyAsJsonObject() returns null silently if the body isn't valid JSON.
        // Logging first means we always know what Daraja actually sent us.
        System.out.println("[payment-service] Daraja STK response HTTP " + httpStatus + ": " + rawBody);

        if (httpStatus != 200) {
          return Future.failedFuture(
            "STK Push HTTP error " + httpStatus + ": " + rawBody
          );
        }

        JsonObject resp;
        try {
          resp = new JsonObject(rawBody);
        } catch (Exception e) {
          return Future.failedFuture("Daraja returned non-JSON body: " + rawBody);
        }

        // Daraja sandbox sends ResponseCode as integer 0; production as string "0".
        // String.valueOf handles both — valueOf(0) = "0", valueOf("0") = "0"
        Object responseCode = resp.getValue("ResponseCode");
        boolean success = "0".equals(String.valueOf(responseCode));

        if (!success) {
          return Future.failedFuture(
            "STK Push failed: " + resp.getString("ResponseDescription", "No description") +
              " (code=" + responseCode + ")"
          );
        }

        return Future.succeededFuture(resp);
      });
  }
}
