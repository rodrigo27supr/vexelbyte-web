package com.vexelbyte.automatizacion.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record RespuestaAnthropic(List<BloqueContenidoAnthropic> content) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record BloqueContenidoAnthropic(String type, String text) {
  }
}
