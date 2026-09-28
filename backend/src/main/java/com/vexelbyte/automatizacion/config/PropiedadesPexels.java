package com.vexelbyte.automatizacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Sin clave, los articulos sin material oficial se quedan con la portada de
// datos: la foto ilustrativa es un complemento, no un requisito del ciclo.
@ConfigurationProperties(prefix = "vexelbyte.imagenes.pexels")
public record PropiedadesPexels(String apiKey, String urlBase) {

  public boolean tieneClave() {
    return apiKey != null && !apiKey.isBlank();
  }
}
