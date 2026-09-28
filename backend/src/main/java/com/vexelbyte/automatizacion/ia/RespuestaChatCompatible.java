package com.vexelbyte.automatizacion.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record RespuestaChatCompatible(List<EleccionChatCompatible> choices) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record EleccionChatCompatible(MensajeRespuestaCompatible message) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record MensajeRespuestaCompatible(String content) {
  }
}
