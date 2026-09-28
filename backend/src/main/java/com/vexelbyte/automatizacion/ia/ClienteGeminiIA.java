package com.vexelbyte.automatizacion.ia;

import com.vexelbyte.automatizacion.common.LimitadorFrecuencia;
import com.vexelbyte.automatizacion.ia.SolicitudGemini.ConfiguracionGeneracionGemini;
import com.vexelbyte.automatizacion.ia.SolicitudGemini.ContenidoGemini;
import com.vexelbyte.automatizacion.ia.SolicitudGemini.InstruccionSistemaGemini;
import java.util.Base64;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

// Un cliente por modelo: en el plan gratuito cada modelo de Gemini tiene su
// propia cuota diaria, asi que la cadena usa varios con la misma clave.
public class ClienteGeminiIA implements ClienteModeloIA {

  private final RestClient clienteHttp;
  private final String claveApi;
  private final String modelo;
  private final LimitadorFrecuencia limitadorFrecuencia;

  public ClienteGeminiIA(
      RestClient clienteHttp, String claveApi, String modelo, LimitadorFrecuencia limitadorFrecuencia) {
    this.clienteHttp = clienteHttp;
    this.claveApi = claveApi;
    this.modelo = modelo;
    this.limitadorFrecuencia = limitadorFrecuencia;
  }

  @Override
  public String generarTexto(String promptSistema, String promptUsuario) {
    return enviarSolicitud(promptSistema, List.of(new ParteTextoGemini(promptUsuario)));
  }

  // La imagen viaja en linea (base64) junto al texto: sin subirla antes a la
  // API de ficheros, que exigiria una segunda llamada por imagen.
  @Override
  public String generarTextoConImagen(String promptSistema, String promptUsuario, ImagenParaIA imagen) {
    ParteImagenGemini parteImagen = new ParteImagenGemini(new ParteImagenGemini.DatosEnLineaGemini(
        imagen.tipoMime(), Base64.getEncoder().encodeToString(imagen.contenido())));
    return enviarSolicitud(promptSistema, List.of(new ParteTextoGemini(promptUsuario), parteImagen));
  }

  private String enviarSolicitud(String promptSistema, List<ParteSolicitudGemini> partesUsuario) {
    SolicitudGemini solicitud = new SolicitudGemini(
        new InstruccionSistemaGemini(List.of(new ParteTextoGemini(promptSistema))),
        List.of(new ContenidoGemini(partesUsuario)),
        new ConfiguracionGeneracionGemini(MediaType.APPLICATION_JSON_VALUE));

    // Una sola llamada, sin reintentos: si falla, la cadena pasa al siguiente
    // proveedor en vez de insistir contra una cuota agotada.
    limitadorFrecuencia.esperarTurno();

    // La api-key de Gemini viaja como query param, no como cabecera — asi
    // la expone la API de Google, a diferencia de Anthropic.
    RespuestaGemini respuesta = clienteHttp.post()
        .uri(constructorUri -> constructorUri
            .path("/v1beta/models/{modelo}:generateContent")
            .queryParam("key", claveApi)
            .build(modelo))
        .contentType(MediaType.APPLICATION_JSON)
        .body(solicitud)
        .retrieve()
        .body(RespuestaGemini.class);

    return extraerPrimerTexto(respuesta);
  }

  @Override
  public String describirProveedor() {
    return "gemini/" + modelo;
  }

  private String extraerPrimerTexto(RespuestaGemini respuesta) {
    if (respuesta == null || respuesta.candidates() == null || respuesta.candidates().isEmpty()) {
      throw new IllegalStateException("Gemini respondio sin ningun candidato de contenido");
    }

    List<ParteTextoGemini> partes = respuesta.candidates().get(0).content().parts();
    if (partes == null || partes.isEmpty()) {
      throw new IllegalStateException("Gemini respondio un candidato sin partes de texto");
    }

    return partes.get(0).text();
  }
}
