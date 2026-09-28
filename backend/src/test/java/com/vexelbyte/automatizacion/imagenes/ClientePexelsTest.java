package com.vexelbyte.automatizacion.imagenes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vexelbyte.automatizacion.config.PropiedadesPexels;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClientePexelsTest {

  private static final String URL_BASE = "https://pexels.example";

  private static final String RESPUESTA_PEXELS = """
      {"page": 1, "photos": [
        {"id": 1, "url": "https://www.pexels.com/photo/1/", "photographer": "Ya Usada",
         "src": {"landscape": "https://images.pexels.com/photos/1/foto.jpeg?w=1200"}},
        {"id": 2, "url": "https://www.pexels.com/photo/2/", "photographer": "Ana Fotógrafa",
         "src": {"landscape": "https://images.pexels.com/photos/2/foto.jpeg?w=1200"}}
      ]}
      """;

  @Test
  void devuelveLaPrimeraFotoApaisadaQueNoUsaOtroArticulo() {
    RestClient.Builder constructorClienteHttp = RestClient.builder().baseUrl(URL_BASE);
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();
    servidorSimulado.expect(requestTo(allOf(
            containsString("/v1/search"), containsString("query=graphics%20card"),
            containsString("orientation=landscape"))))
        .andExpect(header("Authorization", "clave-pexels"))
        .andRespond(withSuccess(RESPUESTA_PEXELS, MediaType.APPLICATION_JSON));

    ClientePexels cliente = new ClientePexels(
        constructorClienteHttp.build(), new PropiedadesPexels("clave-pexels", URL_BASE));
    Set<String> fotosEnUso = Set.of("https://images.pexels.com/photos/1/foto.jpeg?w=1200");

    Optional<FotoIlustrativa> foto = cliente.buscarFoto("graphics card", fotosEnUso::contains);

    assertThat(foto).contains(new FotoIlustrativa(
        "https://images.pexels.com/photos/2/foto.jpeg?w=1200", "Ana Fotógrafa", "https://www.pexels.com/photo/2/"));
    servidorSimulado.verify();
  }

  @Test
  void soloEstaConfiguradoConClave() {
    assertThat(new ClientePexels(RestClient.create(), new PropiedadesPexels("", URL_BASE)).estaConfigurado()).isFalse();
    assertThat(new ClientePexels(RestClient.create(), new PropiedadesPexels("clave", URL_BASE)).estaConfigurado())
        .isTrue();
  }
}
