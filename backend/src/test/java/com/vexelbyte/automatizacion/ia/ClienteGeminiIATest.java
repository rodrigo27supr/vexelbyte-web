package com.vexelbyte.automatizacion.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vexelbyte.automatizacion.common.LimitadorFrecuencia;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClienteGeminiIATest {

  private static final LimitadorFrecuencia LIMITADOR_SIN_ESPERA_PRACTICA = new LimitadorFrecuencia(1_000_000.0);

  @Test
  void enviaLaSolicitudAlModeloCorrectoPidiendoJsonYExtraeElTexto() {
    RestClient.Builder constructorClienteHttp =
        RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();

    String jsonRespuestaSimulada = """
        {
          "candidates": [
            { "content": { "parts": [ { "text": "{\\"titulo\\":\\"Un titular\\"}" } ] } }
          ]
        }
        """;

    servidorSimulado.expect(requestTo(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
                + "?key=clave-de-prueba"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
        .andRespond(withSuccess(jsonRespuestaSimulada, MediaType.APPLICATION_JSON));

    ClienteGeminiIA cliente = new ClienteGeminiIA(
        constructorClienteHttp.build(), "clave-de-prueba", "gemini-3.5-flash-lite", LIMITADOR_SIN_ESPERA_PRACTICA);

    String textoGenerado = cliente.generarTexto("prompt de sistema de prueba", "prompt de usuario de prueba");

    assertThat(textoGenerado).isEqualTo("{\"titulo\":\"Un titular\"}");
    assertThat(cliente.describirProveedor()).isEqualTo("gemini/gemini-3.5-flash-lite");
    servidorSimulado.verify();
  }

  @Test
  void enviaLaImagenEnLineaJuntoAlTextoDelUsuario() {
    RestClient.Builder constructorClienteHttp =
        RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();
    servidorSimulado.expect(requestTo(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent"
                + "?key=clave-de-prueba"))
        .andExpect(jsonPath("$.contents[0].parts[0].text").value("Titular de la noticia"))
        .andExpect(jsonPath("$.contents[0].parts[1].inline_data.mime_type").value("image/jpeg"))
        .andExpect(jsonPath("$.contents[0].parts[1].inline_data.data").value("AQID"))
        .andRespond(withSuccess("""
            {"candidates": [{"content": {"parts": [{"text": "{}"}]}}]}
            """, MediaType.APPLICATION_JSON));

    ClienteGeminiIA cliente = new ClienteGeminiIA(
        constructorClienteHttp.build(), "clave-de-prueba", "gemini-3.6-flash", LIMITADOR_SIN_ESPERA_PRACTICA);

    String textoGenerado = cliente.generarTextoConImagen(
        "sistema", "Titular de la noticia", new ImagenParaIA(new byte[] {1, 2, 3}, "image/jpeg"));

    assertThat(textoGenerado).isEqualTo("{}");
    servidorSimulado.verify();
  }
}
