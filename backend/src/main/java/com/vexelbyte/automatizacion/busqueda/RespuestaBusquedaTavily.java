package com.vexelbyte.automatizacion.busqueda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record RespuestaBusquedaTavily(List<ResultadoTavily> results) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ResultadoTavily(String title, String url, String content) {
  }
}
