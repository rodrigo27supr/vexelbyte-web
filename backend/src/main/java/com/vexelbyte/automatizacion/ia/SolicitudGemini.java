package com.vexelbyte.automatizacion.ia;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record SolicitudGemini(
    @JsonProperty("system_instruction") InstruccionSistemaGemini instruccionSistema,
    List<ContenidoGemini> contents,
    @JsonProperty("generationConfig") ConfiguracionGeneracionGemini configuracionGeneracion) {

  record InstruccionSistemaGemini(List<ParteTextoGemini> parts) {
  }

  record ContenidoGemini(List<ParteSolicitudGemini> parts) {
  }

  // Pido JSON a nivel de API, no solo en el prompt: el modelo deja de envolver
  // la respuesta en markdown o texto suelto que el pipeline tendria que rechazar.
  record ConfiguracionGeneracionGemini(@JsonProperty("responseMimeType") String tipoMimeRespuesta) {
  }
}
