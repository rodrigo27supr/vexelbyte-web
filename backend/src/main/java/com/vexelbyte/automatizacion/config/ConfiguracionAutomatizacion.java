package com.vexelbyte.automatizacion.config;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Configuration
@EnableScheduling
public class ConfiguracionAutomatizacion {

  // Reloj inyectable: el ritmo por seccion y la retencion dependen de la fecha,
  // y los tests necesitan fijarla.
  @Bean
  public Clock reloj() {
    return Clock.systemUTC();
  }

  @Bean
  public RetryTemplate plantillaReintentosApisExternas() {
    RetryTemplate plantilla = new RetryTemplate();

    // Solo lo usa la lectura de feeds: los clientes de IA no reintentan para no
    // gastar cuota del proveedor. Solo reintento fallos con una chance real de
    // resolverse solos: caidas de red/timeout, errores 5xx y un 429 (limite de
    // peticiones superado). Nunca reintento un 4xx
    // de negocio (400/401/404) — va a fallar exactamente igual en el
    // siguiente intento y solo estaria retrasando el resto del job.
    Map<Class<? extends Throwable>, Boolean> excepcionesReintentables = Map.of(
        ResourceAccessException.class, true,
        HttpServerErrorException.class, true,
        HttpClientErrorException.TooManyRequests.class, true);
    plantilla.setRetryPolicy(new SimpleRetryPolicy(4, excepcionesReintentables));

    ExponentialBackOffPolicy politicaEspera = new ExponentialBackOffPolicy();
    // Arranco en 500ms y duplico en cada intento (500ms, 1s, 2s) hasta un
    // techo de 4s — suficiente para absorber un pico transitorio de la API sin
    // que un job en background se quede minutos esperando por un solo item.
    politicaEspera.setInitialInterval(Duration.ofMillis(500).toMillis());
    politicaEspera.setMultiplier(2.0);
    politicaEspera.setMaxInterval(Duration.ofSeconds(4).toMillis());
    plantilla.setBackOffPolicy(politicaEspera);

    return plantilla;
  }
}
