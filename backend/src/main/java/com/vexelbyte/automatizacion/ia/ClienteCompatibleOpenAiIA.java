package com.vexelbyte.automatizacion.ia;

import com.vexelbyte.automatizacion.common.LimitadorFrecuencia;
import com.vexelbyte.automatizacion.ia.SolicitudChatCompatible.MensajeChatCompatible;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

// Un solo cliente para Groq y OpenRouter: ambos exponen la API de chat de
// OpenAI, asi que solo cambian la URL base, la clave y el modelo.
public class ClienteCompatibleOpenAiIA implements ClienteModeloIA {

  // Algunos modelos gratuitos razonan antes de responder y esos tokens cuentan
  // en el limite: con poco margen el JSON del articulo llegaria cortado.
  private static final int TOKENS_MAXIMOS_RESPUESTA = 8192;

  private final String nombreProveedor;
  private final RestClient clienteHttp;
  private final String claveApi;
  private final String modelo;
  private final LimitadorFrecuencia limitadorFrecuencia;

  public ClienteCompatibleOpenAiIA(
      String nombreProveedor, RestClient clienteHttp, String claveApi, String modelo,
      LimitadorFrecuencia limitadorFrecuencia) {
    this.nombreProveedor = nombreProveedor;
    this.clienteHttp = clienteHttp;
    this.claveApi = claveApi;
    this.modelo = modelo;
    this.limitadorFrecuencia = limitadorFrecuencia;
  }

  @Override
  public String generarTexto(String promptSistema, String promptUsuario) {
    SolicitudChatCompatible solicitud = new SolicitudChatCompatible(
        modelo,
        List.of(new MensajeChatCompatible("system", promptSistema), new MensajeChatCompatible("user", promptUsuario)),
        TOKENS_MAXIMOS_RESPUESTA);

    limitadorFrecuencia.esperarTurno();

    RespuestaChatCompatible respuesta = clienteHttp.post()
        .uri("/chat/completions")
        .header("Authorization", "Bearer " + claveApi)
        .contentType(MediaType.APPLICATION_JSON)
        .body(solicitud)
        .retrieve()
        .body(RespuestaChatCompatible.class);

    if (respuesta == null || respuesta.choices() == null || respuesta.choices().isEmpty()
        || respuesta.choices().get(0).message() == null || respuesta.choices().get(0).message().content() == null) {
      throw new IllegalStateException(nombreProveedor + " respondio sin contenido de texto");
    }
    return respuesta.choices().get(0).message().content();
  }

  @Override
  public String describirProveedor() {
    return nombreProveedor + "/" + modelo;
  }
}
