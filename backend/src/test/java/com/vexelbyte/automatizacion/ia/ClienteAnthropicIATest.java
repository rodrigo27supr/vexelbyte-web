package com.vexelbyte.automatizacion.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vexelbyte.automatizacion.common.LimitadorFrecuencia;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClienteAnthropicIATest {

  private static final LimitadorFrecuencia LIMITADOR_SIN_ESPERA_PRACTICA = new LimitadorFrecuencia(1_000_000.0);

  @Test
  void enviaLaSolicitudConLasCabecerasCorrectasYExtraeElTextoDeLaRespuesta() {
    RestClient.Builder constructorClienteHttp = RestClient.builder().baseUrl("https://api.anthropic.com");
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();

    String jsonRespuestaSimulada = """
        {
          "content": [
            { "type": "text", "text": "{\\"titulo\\":\\"Un titular\\"}" }
          ]
        }
        """;

    servidorSimulado.expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-api-key", "clave-de-prueba"))
        .andExpect(header("anthropic-version", "2023-06-01"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andRespond(withSuccess(jsonRespuestaSimulada, MediaType.APPLICATION_JSON));

    ClienteAnthropicIA cliente = new ClienteAnthropicIA(
        constructorClienteHttp.build(), "clave-de-prueba", "claude-sonnet-5", "2023-06-01",
        LIMITADOR_SIN_ESPERA_PRACTICA);

    String textoGenerado = cliente.generarTexto("prompt de sistema de prueba", "prompt de usuario de prueba");

    assertThat(textoGenerado).isEqualTo("{\"titulo\":\"Un titular\"}");
    servidorSimulado.verify();
  }
}
