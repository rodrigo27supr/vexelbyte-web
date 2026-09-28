package com.vexelbyte.automatizacion.ia;

import com.fasterxml.jackson.annotation.JsonProperty;

record ParteImagenGemini(@JsonProperty("inline_data") DatosEnLineaGemini datosEnLinea) implements ParteSolicitudGemini {

  record DatosEnLineaGemini(@JsonProperty("mime_type") String tipoMime, String data) {
  }
}
