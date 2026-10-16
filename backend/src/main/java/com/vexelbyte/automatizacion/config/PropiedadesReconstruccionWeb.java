package com.vexelbyte.automatizacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Deploy Hook de Vercel: quien tenga la URL puede lanzar un despliegue, asi que
// es un secreto y solo llega por variable de entorno. Sin ella el backend no
// avisa y la reconstruccion la lanzan los respaldos (cron-job.org y GitHub).
@ConfigurationProperties(prefix = "vexelbyte.web")
public record PropiedadesReconstruccionWeb(String urlDeployHook) {

  public boolean tieneDeployHook() {
    return urlDeployHook != null && !urlDeployHook.isBlank();
  }
}
