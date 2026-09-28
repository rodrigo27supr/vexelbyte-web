package com.vexelbyte.automatizacion.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
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

class ClienteCompatibleOpenAiIATest {

  private static final LimitadorFrecuencia LIMITADOR_SIN_ESPERA_PRACTICA = new LimitadorFrecuencia(1_000_000.0);

  private ClienteCompatibleOpenAiIA crearCliente(RestClient.Builder constructorClienteHttp) {
    return new ClienteCompatibleOpenAiIA(
        "groq", constructorClienteHttp.build(), "clave-de-prueba", "openai/gpt-oss-120b",
        LIMITADOR_SIN_ESPERA_PRACTICA);
  }

  @Test
  void enviaMensajesDeSistemaYUsuarioConLaClaveEnLaCabeceraYExtraeLaRespuesta() {
    RestClient.Builder constructorClienteHttp = RestClient.builder().baseUrl("https://api.groq.com/openai/v1");
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();

    servidorSimulado.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer clave-de-prueba"))
        .andExpect(jsonPath("$.model").value("openai/gpt-oss-120b"))
        .andExpect(jsonPath("$.messages[0].role").value("system"))
        .andExpect(jsonPath("$.messages[0].content").value("prompt de sistema"))
        .andExpect(jsonPath("$.messages[1].role").value("user"))
        .andRespond(withSuccess("""
            {"choices": [{"message": {"role": "assistant", "content": "{\\"titulo\\":\\"Un titular\\"}"}}]}
            """, MediaType.APPLICATION_JSON));

    ClienteCompatibleOpenAiIA cliente = crearCliente(constructorClienteHttp);

    assertThat(cliente.generarTexto("prompt de sistema", "prompt de usuario")).isEqualTo("{\"titulo\":\"Un titular\"}");
    assertThat(cliente.describirProveedor()).isEqualTo("groq/openai/gpt-oss-120b");
    servidorSimulado.verify();
  }

  @Test
  void lanzaExcepcionCuandoLaRespuestaNoTraeContenido() {
    RestClient.Builder constructorClienteHttp = RestClient.builder().baseUrl("https://api.groq.com/openai/v1");
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();
    servidorSimulado.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
        .andRespond(withSuccess("{\"choices\": []}", MediaType.APPLICATION_JSON));

    ClienteCompatibleOpenAiIA cliente = crearCliente(constructorClienteHttp);

    assertThatThrownBy(() -> cliente.generarTexto("sistema", "usuario"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("groq");
  }
}
