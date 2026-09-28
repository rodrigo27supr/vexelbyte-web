package com.vexelbyte.automatizacion.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record RespuestaGemini(List<CandidatoGemini> candidates) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record CandidatoGemini(ContenidoRespuestaGemini content) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ContenidoRespuestaGemini(List<ParteTextoGemini> parts) {
  }
}
