package com.vexelbyte.automatizacion.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClienteFeedNoticiasHardwareTest {

  private static final String URL_FEED = "https://feed.example/rss";

  private ClienteFeedNoticiasHardware clienteConRespuesta(String xmlRespuesta) {
    RestClient.Builder constructorClienteHttp = RestClient.builder();
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();
    servidorSimulado.expect(requestTo(URL_FEED)).andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(xmlRespuesta, MediaType.APPLICATION_XML));
    return new ClienteFeedNoticiasHardware(constructorClienteHttp.build(), new RetryTemplate());
  }

  @Test
  void mapeaLosItemsDelFeedIgnorandoEtiquetasConNombreParecido() {
    String xmlFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
          <channel>
            <title>Feed de prueba</title>
            <item>
              <title><![CDATA[ Nvidia lanza la RTX 5090 ]]></title>
              <guid isPermaLink="false">abc123</guid>
              <link>https://fuente.example/rtx-5090</link>
              <description><![CDATA[ Resumen breve de la grafica. ]]></description>
              <dc:description>No debe leerse este</dc:description>
              <pubDate>Sat, 26 Sep 2026 14:00:00 +0000</pubDate>
            </item>
          </channel>
        </rss>
        """;

    List<NoticiaHardwareRss> noticias = clienteConRespuesta(xmlFeed).obtenerNoticiasRecientes(URL_FEED);

    assertThat(noticias).hasSize(1);
    NoticiaHardwareRss noticia = noticias.get(0);
    assertThat(noticia.identificadorExterno()).isEqualTo("abc123");
    assertThat(noticia.titulo()).isEqualTo("Nvidia lanza la RTX 5090");
    assertThat(noticia.enlaceOriginal()).isEqualTo("https://fuente.example/rtx-5090");
    assertThat(noticia.resumen()).isEqualTo("Resumen breve de la grafica.");
    assertThat(noticia.fechaPublicacion()).isEqualTo(Instant.parse("2026-09-26T14:00:00Z"));
  }

  @Test
  void convierteEnTextoPlanoElHtmlEscapadoDeLaDescripcion() {
    // Forma real del feed de GSMArena: una <img> y un <p> escapados como
    // texto dentro de description, mas un bloque CDATA.
    String xmlFeed = """
        <rss version="2.0"><channel><item>
          <title>Galaxy Tab S12 Ultra</title><guid>gsm-1</guid>
          <description>&lt;img src=&quot;https://img.example/a.jpg&quot; /&gt; &lt;p&gt;<![CDATA[Samsung prepara   la
        Tab S12 Ultra.]]>&lt;/p&gt;</description>
        </item></channel></rss>
        """;

    List<NoticiaHardwareRss> noticias = clienteConRespuesta(xmlFeed).obtenerNoticiasRecientes(URL_FEED);

    assertThat(noticias.get(0).resumen()).isEqualTo("Samsung prepara la Tab S12 Ultra.");
  }

  @Test
  void recortaLosResumenesDemasiadoLargos() {
    String xmlFeed = """
        <rss version="2.0"><channel><item>
          <title>Largo</title><guid>largo-1</guid><description>%s</description>
        </item></channel></rss>
        """.formatted("a".repeat(5000));

    List<NoticiaHardwareRss> noticias = clienteConRespuesta(xmlFeed).obtenerNoticiasRecientes(URL_FEED);

    assertThat(noticias.get(0).resumen()).hasSize(4000);
  }

  @Test
  void descartaLosItemsSinGuidOSinTitulo() {
    String xmlFeed = """
        <rss version="2.0"><channel>
          <item><title>Sin guid</title></item>
          <item><guid>sin-titulo</guid></item>
          <item><title>Completo</title><guid>ok-1</guid></item>
        </channel></rss>
        """;

    List<NoticiaHardwareRss> noticias = clienteConRespuesta(xmlFeed).obtenerNoticiasRecientes(URL_FEED);

    assertThat(noticias).extracting(NoticiaHardwareRss::identificadorExterno).containsExactly("ok-1");
  }

  @Test
  void usaLaFechaActualCuandoElPubDateEsIlegible() {
    String xmlFeed = """
        <rss version="2.0"><channel>
          <item><title>Fecha rota</title><guid>ok-1</guid><pubDate>ayer por la tarde</pubDate></item>
        </channel></rss>
        """;
    Instant antesDeParsear = Instant.now();

    List<NoticiaHardwareRss> noticias = clienteConRespuesta(xmlFeed).obtenerNoticiasRecientes(URL_FEED);

    assertThat(noticias.get(0).fechaPublicacion()).isAfterOrEqualTo(antesDeParsear);
  }

  @Test
  void rechazaUnFeedConDoctypeParaBloquearXxe() {
    String xmlMalicioso = """
        <?xml version="1.0"?>
        <!DOCTYPE rss [<!ENTITY secreto SYSTEM "file:///etc/passwd">]>
        <rss version="2.0"><channel><item><title>&secreto;</title><guid>x</guid></item></channel></rss>
        """;

    assertThatThrownBy(() -> clienteConRespuesta(xmlMalicioso).obtenerNoticiasRecientes(URL_FEED))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("XML valido");
  }

  @Test
  void lanzaExcepcionCuandoLaRespuestaNoEsXml() {
    assertThatThrownBy(() -> clienteConRespuesta("<html>no es un feed").obtenerNoticiasRecientes(URL_FEED))
        .isInstanceOf(IllegalStateException.class);
  }
}
