package com.vexelbyte.automatizacion.config;

import com.vexelbyte.automatizacion.common.LimitadorFrecuencia;
import com.vexelbyte.automatizacion.ia.CadenaProveedoresIA;
import com.vexelbyte.automatizacion.ia.ClienteAnthropicIA;
import com.vexelbyte.automatizacion.ia.ClienteCompatibleOpenAiIA;
import com.vexelbyte.automatizacion.ia.ClienteGeminiIA;
import com.vexelbyte.automatizacion.ia.ClienteModeloIA;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Monta la cadena de proveedores en orden de preferencia: primero los modelos
// gratuitos de Gemini (misma clave, cuotas separadas), luego Groq y
// OpenRouter como respaldo, y Anthropic al final porque es de pago.
@Configuration
public class ConfiguracionProveedoresIA {

  private static final Logger REGISTRO = LoggerFactory.getLogger(ConfiguracionProveedoresIA.class);

  // Sin timeout, un proveedor colgado bloqueaba el ciclo entero. La lectura es
  // larga porque redactar un articulo completo puede tardar mas de un minuto.
  private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(10);
  private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(120);

  @Bean
  public ClienteModeloIA cadenaProveedoresIA(PropiedadesIA propiedades) {
    List<ClienteModeloIA> eslabones = new ArrayList<>();
    anadirModelosGemini(propiedades.gemini(), eslabones);
    anadirProveedorCompatible("groq", propiedades.groq(), eslabones);
    anadirProveedorCompatible("openrouter", propiedades.openrouter(), eslabones);
    anadirAnthropic(propiedades.anthropic(), eslabones);

    if (eslabones.isEmpty()) {
      REGISTRO.warn("No hay ningun proveedor de IA con clave: el job no podra redactar articulos");
    } else {
      REGISTRO.info("Cadena de IA: {}", eslabones.stream().map(ClienteModeloIA::describirProveedor).toList());
    }
    return new CadenaProveedoresIA(eslabones);
  }

  private void anadirModelosGemini(PropiedadesIA.ProveedorGemini gemini, List<ClienteModeloIA> eslabones) {
    if (gemini == null || !tieneClave(gemini.apiKey()) || gemini.modelos() == null) {
      return;
    }
    RestClient clienteHttp = crearClienteHttp(gemini.urlBase());
    for (String modelo : gemini.modelos()) {
      // Limitador propio por modelo: el limite por minuto de Gemini se cuenta
      // por modelo, asi que compartirlo frenaria a los demas sin motivo.
      eslabones.add(new ClienteGeminiIA(
          clienteHttp, gemini.apiKey(), modelo.strip(), crearLimitador(gemini.llamadasPorMinuto())));
    }
  }

  private void anadirProveedorCompatible(
      String nombreProveedor, PropiedadesIA.ProveedorCompatibleOpenAi proveedor, List<ClienteModeloIA> eslabones) {
    if (proveedor == null || !tieneClave(proveedor.apiKey())) {
      return;
    }
    eslabones.add(new ClienteCompatibleOpenAiIA(
        nombreProveedor, crearClienteHttp(proveedor.urlBase()), proveedor.apiKey(), proveedor.modelo(),
        crearLimitador(proveedor.llamadasPorMinuto())));
  }

  private void anadirAnthropic(PropiedadesIA.ProveedorAnthropic anthropic, List<ClienteModeloIA> eslabones) {
    if (anthropic == null || !tieneClave(anthropic.apiKey())) {
      return;
    }
    eslabones.add(new ClienteAnthropicIA(
        crearClienteHttp(anthropic.urlBase()), anthropic.apiKey(), anthropic.modelo(), anthropic.version(),
        crearLimitador(anthropic.llamadasPorMinuto())));
  }

  private boolean tieneClave(String claveApi) {
    return claveApi != null && !claveApi.isBlank();
  }

  private LimitadorFrecuencia crearLimitador(double llamadasPorMinuto) {
    return new LimitadorFrecuencia(llamadasPorMinuto / 60.0);
  }

  private RestClient crearClienteHttp(String urlBase) {
    HttpClient clienteJdk = HttpClient.newBuilder().connectTimeout(TIMEOUT_CONEXION).build();
    JdkClientHttpRequestFactory fabricaPeticiones = new JdkClientHttpRequestFactory(clienteJdk);
    fabricaPeticiones.setReadTimeout(TIMEOUT_LECTURA);
    return RestClient.builder().baseUrl(urlBase).requestFactory(fabricaPeticiones).build();
  }
}
