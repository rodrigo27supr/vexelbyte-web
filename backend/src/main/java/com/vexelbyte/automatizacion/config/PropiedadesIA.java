package com.vexelbyte.automatizacion.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Un bloque por proveedor. Las claves no tienen valor por defecto: un
// proveedor sin clave simplemente no entra en la cadena, en vez de arrancar
// con una credencial vacia que fallaria en cada noticia.
@ConfigurationProperties(prefix = "vexelbyte.ia")
public record PropiedadesIA(
    ProveedorGemini gemini,
    ProveedorCompatibleOpenAi groq,
    ProveedorCompatibleOpenAi openrouter,
    ProveedorAnthropic anthropic) {

  public record ProveedorGemini(String apiKey, List<String> modelos, String urlBase, double llamadasPorMinuto) {
  }

  public record ProveedorCompatibleOpenAi(String apiKey, String modelo, String urlBase, double llamadasPorMinuto) {
  }

  public record ProveedorAnthropic(
      String apiKey, String modelo, String urlBase, String version, double llamadasPorMinuto) {
  }
}
