package com.akiba.payments.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.redis.client.RedisAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * Handles all communication with Safaricom's Daraja API.
 * Two responsibilities: (1) get/cache access token, (2) fire STK Push.
 */
public class DarajaService {

  private static final Logger log = LoggerFactory.getLogger(DarajaService.class);

  // Daraja sandbox base URL — swap to production URL via env in docker-compose
  private static final String DARAJA_BASE_URL = "https://sandbox.safaricom.co.ke";
  private static final String TOKEN_REDIS_KEY  = "daraja:token";
  private static final int    TOKEN_TTL_SECONDS = 3500; // token lives 3600s; cache for 3500 to be safe

  private final WebClient webClient;
  private final RedisAPI  redis;
  private final String    consumerKey;
  private final String    consumerSecret;
  private final String    shortcode;
  private final String    passkey;
  private final String    callbackUrl;

  public DarajaService(WebClient webClient, RedisAPI redis, JsonObject config) {
    this.webClient      = webClient;
    this.redis          = redis;
    this.consumerKey    = config.getString("DARAJA_CONSUMER_KEY");
    this.consumerSecret = config.getString("DARAJA_CONSUMER_SECRET");
    this.shortcode      = config.getString("DARAJA_SHORTCODE");
    this.passkey        = config.getString("DARAJA_PASSKEY");
    this.callbackUrl    = config.getString("DARAJA_CALLBACK_URL");
  }

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  /**
   * Returns a valid access token, either from Redis cache or fresh from Daraja.
   * This is called before every STK Push — the cache prevents hammering the OAuth endpoint.
   */
  public Future<String> getAccessToken() {
    return redis.get(TOKEN_REDIS_KEY)
      .compose(cached -> {
        if (cached != null) {
          return Future.succeededFuture(cached.toString());
        }
        return fetchFreshToken();
      });
  }

  /**
   * Initiates an M-Pesa STK Push (the prompt sent to the customer's phone).
   *
   * @param phone       Customer phone in international format, e.g. 254712345678
   * @param amount      Amount in KES (whole number — Daraja rejects decimals)
   * @param accountRef  Short reference shown on the M-Pesa receipt
   * @param description Transaction description (shown in STK prompt)
   * @return JsonObject with CheckoutRequestID and MerchantRequestID from Daraja
   */
  public Future<JsonObject> initiateStkPush(String phone, int amount, String accountRef, String description) {
    return getAccessToken()
      .compose(token -> sendStkPushRequest(token, phone, amount, accountRef, description));
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private Future<String> fetchFreshToken() {
    // Daraja uses HTTP Basic auth: base64(consumerKey:consumerSecret)
    String credentials = Base64.getEncoder()
      .encodeToString((consumerKey + ":" + consumerSecret).getBytes());

    return webClient
      .getAbs(DARAJA_BASE_URL + "/oauth/v1/generate?grant_type=client_credentials")
      .putHeader("Authorization", "Basic " + credentials)
      .send()
      .compose(response -> {
        if (response.statusCode() != 200) {
          String error = "Daraja token fetch failed: HTTP " + response.statusCode();
          log.error(error);
          return Future.failedFuture(error);
        }

        String token = response.bodyAsJsonObject().getString("access_token");
        // Cache so subsequent calls within the same hour skip this round-trip
        return redis.setex(TOKEN_REDIS_KEY, String.valueOf(TOKEN_TTL_SECONDS), token)
          .map(token);
      });
  }

  private Future<JsonObject> sendStkPushRequest(
      String token, String phone, int amount, String accountRef, String description) {

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    // Daraja requires: base64(shortcode + passkey + timestamp)
    String password = Base64.getEncoder()
      .encodeToString((shortcode + passkey + timestamp).getBytes());

    JsonObject body = new JsonObject()
      .put("BusinessShortCode", shortcode)
      .put("Password", password)
      .put("Timestamp", timestamp)
      .put("TransactionType", "CustomerPayBillOnline")
      .put("Amount", amount)
      .put("PartyA", phone)            // customer phone
      .put("PartyB", shortcode)        // your shortcode receives the money
      .put("PhoneNumber", phone)       // phone that gets the STK prompt
      .put("CallBackURL", callbackUrl)
      .put("AccountReference", accountRef)
      .put("TransactionDesc", description);

    return webClient
      .postAbs(DARAJA_BASE_URL + "/mpesa/stkpush/v1/processrequest")
      .putHeader("Authorization", "Bearer " + token)
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(body)
      .compose(response -> {
        JsonObject result = response.bodyAsJsonObject();

        // ResponseCode "0" means Daraja accepted the push request
        if (!"0".equals(result.getString("ResponseCode"))) {
          String error = "STK Push rejected: " + result.getString("errorMessage", result.encode());
          log.error(error);
          return Future.failedFuture(error);
        }

        return Future.succeededFuture(result);
      });
  }
}
