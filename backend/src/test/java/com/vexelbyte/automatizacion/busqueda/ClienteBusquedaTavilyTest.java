package com.vexelbyte.automatizacion.busqueda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vexelbyte.automatizacion.config.PropiedadesBusquedaTavily;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClienteBusquedaTavilyTest {

  private static final String URL_BASE = "https://tavily.example";
  private static final String TEXTO_IPHONE = "El iPhone 18 Pro dura mas de un dia con uso intenso y su camara mejora. ".repeat(4);
  private static final String TEXTO_OTRO_MODELO = "El Galaxy S26 tiene buena pantalla y una bateria que aguanta. ".repeat(4);

  // Dos resultados del mismo medio, uno de otro modelo, uno demasiado corto,
  // uno de un enlace no web y dos validos: solo esos dos deben sobrevivir.
  private static final String RESPUESTA_TAVILY = """
      {"query": "q", "results": [
        {"title": "Apple iPhone 18 Pro review", "url": "https://www.theverge.com/review/1", "content": "%1$s"},
        {"title": "iPhone 18 Pro, otra pieza", "url": "https://www.theverge.com/review/2", "content": "%1$s"},
        {"title": "Galaxy S26 review", "url": "https://www.cnet.com/galaxy", "content": "%2$s"},
        {"title": "iPhone 18 Pro", "url": "https://www.engadget.com/corto", "content": "Muy corto"},
        {"title": "iPhone 18 Pro", "url": "ftp://archivo.example/iphone", "content": "%1$s"},
        {"title": "iPhone 18 Pro review: la bateria", "url": "https://engadget.com/review", "content": "%1$s"}
      ], "response_time": 1.2}
      """;

  @Test
  void devuelveUnFragmentoPorMedioSoloDeResultadosQueHablanDelProducto() {
    RestClient.Builder constructorClienteHttp = RestClient.builder().baseUrl(URL_BASE);
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();
    String respuesta = RESPUESTA_TAVILY.formatted(TEXTO_IPHONE, TEXTO_OTRO_MODELO);
    servidorSimulado.expect(requestTo(URL_BASE + "/search"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer tvly-clave-de-prueba"))
        .andExpect(jsonPath("$.query").value("\"Apple iPhone 18 Pro\" review"))
        .andExpect(jsonPath("$.search_depth").value("advanced"))
        .andExpect(jsonPath("$.exclude_domains").isArray())
        .andExpect(jsonPath("$.include_domains").doesNotExist())
        .andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));

    ClienteBusquedaTavily cliente = new ClienteBusquedaTavily(
        constructorClienteHttp.build(), new PropiedadesBusquedaTavily("tvly-clave-de-prueba", URL_BASE));

    List<FragmentoAnalisis> fragmentos = cliente.buscarAnalisis("Apple iPhone 18 Pro");

    assertThat(fragmentos).extracting(FragmentoAnalisis::medio).containsExactly("theverge.com", "engadget.com");
    assertThat(fragmentos.getFirst().url()).isEqualTo("https://www.theverge.com/review/1");
    servidorSimulado.verify();
  }

  @Test
  void buscaCoberturaDeLaNoticiaSoloEnMediosReconocidosSinElDeOrigenYDelMismoTema() {
    RestClient.Builder constructorClienteHttp = RestClient.builder().baseUrl(URL_BASE);
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();
    String texto = "Oppo confirms the K14 Plus launch with an 8000 mAh battery and 80W charging support. ".repeat(3);
    String respuesta = """
        {"results": [
          {"title": "Oppo K14 Plus launch date", "url": "https://www.gsmarena.com/oppo-k14", "content": "%1$s"},
          {"title": "Oppo K14 Plus specs", "url": "https://www.gizmochina.com/oppo-k14-plus", "content": "%1$s"},
          {"title": "Oppo K14 Plus launching", "url": "https://moshirewritten.com/oppo-k14-plus", "content": "%1$s"},
          {"title": "Oppo K14 Plus thread", "url": "https://forums.gizmochina.com/oppo-k14-plus", "content": "%1$s"},
          {"title": "Best budget laptops", "url": "https://www.laptopmag.com/best", "content": "%2$s"}
        ]}
        """.formatted(texto, "Our favourite budget laptops this year, tested for battery life and screens. ".repeat(3));
    servidorSimulado.expect(requestTo(URL_BASE + "/search"))
        .andExpect(jsonPath("$.query").value("Oppo K14 Plus launch confirmed"))
        .andExpect(jsonPath("$.topic").value("news"))
        .andExpect(jsonPath("$.time_range").value("week"))
        .andExpect(jsonPath("$.include_domains").isArray())
        .andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));

    ClienteBusquedaTavily cliente = new ClienteBusquedaTavily(
        constructorClienteHttp.build(), new PropiedadesBusquedaTavily("tvly-clave-de-prueba", URL_BASE));

    List<FragmentoAnalisis> cobertura = cliente.buscarCobertura("Oppo K14 Plus launch confirmed", "gsmarena.com");

    assertThat(cobertura).extracting(FragmentoAnalisis::medio).containsExactly("gizmochina.com");
    servidorSimulado.verify();
  }

  @Test
  void soloEstaConfiguradoConClave() {
    RestClient clienteHttp = RestClient.create();

    assertThat(new ClienteBusquedaTavily(clienteHttp, new PropiedadesBusquedaTavily("", URL_BASE)).estaConfigurado())
        .isFalse();
    assertThat(new ClienteBusquedaTavily(clienteHttp, new PropiedadesBusquedaTavily(null, URL_BASE)).estaConfigurado())
        .isFalse();
    assertThat(new ClienteBusquedaTavily(clienteHttp, new PropiedadesBusquedaTavily("tvly-x", URL_BASE)).estaConfigurado())
        .isTrue();
  }
}
