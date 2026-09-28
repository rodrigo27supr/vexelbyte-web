package com.vexelbyte.automatizacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Sin clave, los analisis de GSMArena se siguen descartando: la busqueda es
// opcional y el resto del ciclo no depende de ella.
@ConfigurationProperties(prefix = "vexelbyte.busqueda.tavily")
public record PropiedadesBusquedaTavily(String apiKey, String urlBase) {

  public boolean tieneClave() {
    return apiKey != null && !apiKey.isBlank();
  }
}
