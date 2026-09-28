package com.vexelbyte.automatizacion.ia;

import com.vexelbyte.automatizacion.common.LimitadorFrecuencia;
import com.vexelbyte.automatizacion.ia.SolicitudAnthropic.MensajeAnthropic;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

// Proveedor de pago: va el ultimo en la cadena y solo entra si hay clave.
public class ClienteAnthropicIA implements ClienteModeloIA {

  // Margen generoso para un articulo completo en HTML con su ficha tecnica.
  private static final int TOKENS_MAXIMOS_RESPUESTA = 8192;

  private final RestClient clienteHttp;
  private final String claveApi;
  private final String modelo;
  private final String versionApi;
  private final LimitadorFrecuencia limitadorFrecuencia;

  public ClienteAnthropicIA(
      RestClient clienteHttp, String claveApi, String modelo, String versionApi,
      LimitadorFrecuencia limitadorFrecuencia) {
    this.clienteHttp = clienteHttp;
    this.claveApi = claveApi;
    this.modelo = modelo;
    this.versionApi = versionApi;
    this.limitadorFrecuencia = limitadorFrecuencia;
  }

  @Override
  public String generarTexto(String promptSistema, String promptUsuario) {
    SolicitudAnthropic solicitud = new SolicitudAnthropic(
        modelo, TOKENS_MAXIMOS_RESPUESTA, promptSistema, List.of(new MensajeAnthropic("user", promptUsuario)));

    limitadorFrecuencia.esperarTurno();

    RespuestaAnthropic respuesta = clienteHttp.post()
        .uri("/v1/messages")
        .header("x-api-key", claveApi)
        .header("anthropic-version", versionApi)
        .contentType(MediaType.APPLICATION_JSON)
        .body(solicitud)
        .retrieve()
        .body(RespuestaAnthropic.class);

    if (respuesta == null || respuesta.content() == null || respuesta.content().isEmpty()) {
      throw new IllegalStateException("Anthropic respondio sin ningun bloque de contenido de texto");
    }
    return respuesta.content().get(0).text();
  }

  @Override
  public String describirProveedor() {
    return "anthropic/" + modelo;
  }
}
