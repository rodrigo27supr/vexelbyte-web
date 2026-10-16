package com.vexelbyte.automatizacion;

import com.vexelbyte.automatizacion.config.PropiedadesReconstruccionWeb;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// La web es estatica: una noticia nueva no se ve hasta que Vercel reconstruye,
// y cada reconstruccion lee de Neon todas las fotos (su plan gratuito corta la
// base a los 5 GB de transferencia al mes). Reconstruyo solo cuando el ciclo
// acaba de publicar algo, en vez de a horas fijas.
@Component
public class NotificadorReconstruccionWeb {

  private static final Logger REGISTRO = LoggerFactory.getLogger(NotificadorReconstruccionWeb.class);

  private final RestClient clienteHttp;
  private final PropiedadesReconstruccionWeb propiedades;

  public NotificadorReconstruccionWeb(
      @Qualifier("clienteHttpReconstruccionWeb") RestClient clienteHttp, PropiedadesReconstruccionWeb propiedades) {
    this.clienteHttp = clienteHttp;
    this.propiedades = propiedades;
  }

  // Un fallo aqui no debe tumbar el ciclo: la noticia ya esta publicada y el
  // respaldo de GitHub reconstruye si la web no la tiene.
  public void solicitarReconstruccion() {
    if (!propiedades.tieneDeployHook()) {
      REGISTRO.info("Sin URL del Deploy Hook de Vercel: no pido la reconstruccion de la web");
      return;
    }
    try {
      clienteHttp.post().uri(URI.create(propiedades.urlDeployHook())).retrieve().toBodilessEntity();
      REGISTRO.info("Reconstruccion de la web solicitada a Vercel");
    } catch (RestClientResponseException respuestaDeError) {
      // Nunca registro el mensaje de la excepcion: incluye la URL, que es un secreto.
      REGISTRO.warn("Vercel rechazo la reconstruccion de la web con el estado {}", respuestaDeError.getStatusCode().value());
    } catch (RestClientException falloDeConexion) {
      REGISTRO.warn("No pude pedir la reconstruccion de la web ({})", falloDeConexion.getClass().getSimpleName());
    }
  }
}
