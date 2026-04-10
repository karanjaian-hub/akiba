package com.akiba.ai.providers;

import com.akiba.ai.models.Message;
import io.vertx.core.Future;

import java.util.List;

/**
 * AiProvider defines the contract every AI backend must fulfil.
 *
 * Why an interface? Because today we use Gemini, but tomorrow we might
 * switch to Claude or any other LLM. All the callers only talk to this
 * interface — swapping the provider requires zero changes in the
 * handlers or services.
 */
public interface AiProvider {

  /**
   * Sends a prompt to the underlying AI model and returns the response text.
   *
   * @param systemPrompt  The persona / context injected before the conversation.
   *                      This is where we'll put the user's financial snapshot.
   * @param userMessage   The actual message the user just typed.
   * @param history       Prior turns in the conversation (oldest → newest).
   *                      Empty list is fine for one-off insight calls.
   * @return Future<String> resolving to the model's reply text.
   */
  Future<String> complete(String systemPrompt, String userMessage, List<Message> history);
}
