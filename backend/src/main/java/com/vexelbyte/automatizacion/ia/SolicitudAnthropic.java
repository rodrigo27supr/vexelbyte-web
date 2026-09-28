package com.vexelbyte.automatizacion.ia;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// Mapeo del cuerpo que espera POST /v1/messages de Anthropic. Traduzco
// max_tokens a un nombre propio (tokensMaximos) para no arrastrar la convencion de nombres de la API externa.
record SolicitudAnthropic(
    String model,
    @JsonProperty("max_tokens") int tokensMaximos,
    String system,
    List<MensajeAnthropic> messages) {

  record MensajeAnthropic(String role, String content) {
  }
}
