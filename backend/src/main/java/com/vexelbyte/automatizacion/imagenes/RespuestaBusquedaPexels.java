package com.vexelbyte.automatizacion.imagenes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record RespuestaBusquedaPexels(List<FotoPexels> photos) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record FotoPexels(String url, String photographer, TamanosFotoPexels src) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TamanosFotoPexels(String landscape) {
  }
}
