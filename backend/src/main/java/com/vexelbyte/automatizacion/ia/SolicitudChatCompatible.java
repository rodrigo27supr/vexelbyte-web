package com.vexelbyte.automatizacion.ia;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// Formato /chat/completions de OpenAI, que Groq y OpenRouter replican tal cual.
record SolicitudChatCompatible(
    String model,
    List<MensajeChatCompatible> messages,
    @JsonProperty("max_tokens") int tokensMaximos) {

  record MensajeChatCompatible(String role, String content) {
  }
}
