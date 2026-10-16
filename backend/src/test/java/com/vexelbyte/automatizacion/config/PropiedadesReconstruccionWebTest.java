package com.vexelbyte.automatizacion.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

// El application.yml de test sustituye al de produccion, asi que ningun otro
// test comprueba que la variable de entorno llega a la propiedad: un error
// de nombre dejaria el aviso a Vercel apagado sin que nada fallara.
class PropiedadesReconstruccionWebTest {

  private PropiedadesReconstruccionWeb leerConfiguracionDeProduccion(Map<String, Object> variablesDeEntorno) throws IOException {
    StandardEnvironment entorno = new StandardEnvironment();
    new YamlPropertySourceLoader()
        .load("produccion", new FileSystemResource("src/main/resources/application.yml"))
        .forEach(entorno.getPropertySources()::addLast);
    entorno.getPropertySources().addFirst(new MapPropertySource("variables", variablesDeEntorno));
    return Binder.get(entorno).bind("vexelbyte.web", PropiedadesReconstruccionWeb.class).get();
  }

  @Test
  void leeElDeployHookDeLaVariableUrlDeployHookVercel() throws IOException {
    PropiedadesReconstruccionWeb propiedades =
        leerConfiguracionDeProduccion(Map.of("URL_DEPLOY_HOOK_VERCEL", "https://api.vercel.com/v1/integrations/deploy/prj_x/y"));

    assertThat(propiedades.tieneDeployHook()).isTrue();
    assertThat(propiedades.urlDeployHook()).isEqualTo("https://api.vercel.com/v1/integrations/deploy/prj_x/y");
  }

  @Test
  void sinLaVariableNoHayDeployHookYElBackendArrancaIgual() throws IOException {
    assertThat(leerConfiguracionDeProduccion(Map.of()).tieneDeployHook()).isFalse();
  }
}
