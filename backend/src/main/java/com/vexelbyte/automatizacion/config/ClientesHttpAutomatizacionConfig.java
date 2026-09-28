package com.vexelbyte.automatizacion.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientesHttpAutomatizacionConfig {

  // Sin baseUrl: las URLs completas (feeds y fichas tecnicas) las pasa cada
  // cliente. Me identifico con un User-Agent propio porque varios medios
  // bloquean el agente por defecto de Java, y pongo timeouts para que un medio
  // lento no bloquee el ciclo entero.
  @Bean
  public RestClient clienteHttpFeedHardware() {
    HttpClient clienteJdk = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    JdkClientHttpRequestFactory fabricaPeticiones = new JdkClientHttpRequestFactory(clienteJdk);
    fabricaPeticiones.setReadTimeout(Duration.ofSeconds(30));
    return RestClient.builder()
        .requestFactory(fabricaPeticiones)
        .defaultHeader("User-Agent", "VexelByteBot/1.0 (+https://www.vexelbyte.com)")
        .build();
  }

  @Bean
  public RestClient clienteHttpPexels(PropiedadesPexels propiedadesPexels) {
    HttpClient clienteJdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    JdkClientHttpRequestFactory fabricaPeticiones = new JdkClientHttpRequestFactory(clienteJdk);
    fabricaPeticiones.setReadTimeout(Duration.ofSeconds(30));
    return RestClient.builder()
        .baseUrl(propiedadesPexels.urlBase())
        .requestFactory(fabricaPeticiones)
        .build();
  }

  // La busqueda avanzada de Tavily lee varias paginas antes de responder:
  // le doy un minuto de lectura, lejos de los 120 s de la IA.
  @Bean
  public RestClient clienteHttpTavily(PropiedadesBusquedaTavily propiedadesTavily) {
    HttpClient clienteJdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    JdkClientHttpRequestFactory fabricaPeticiones = new JdkClientHttpRequestFactory(clienteJdk);
    fabricaPeticiones.setReadTimeout(Duration.ofSeconds(60));
    return RestClient.builder()
        .baseUrl(propiedadesTavily.urlBase())
        .requestFactory(fabricaPeticiones)
        .build();
  }
}
