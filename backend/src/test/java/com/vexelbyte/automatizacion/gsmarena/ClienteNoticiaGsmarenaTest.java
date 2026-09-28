package com.vexelbyte.automatizacion.gsmarena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClienteNoticiaGsmarenaTest {

  private static final String URL_NOTICIA = "https://www.gsmarena.com/oppo_k14_plus_official_images-news-74784.php";

  // La barra de tendencias va antes del cuerpo a proposito: el cliente tiene
  // que ignorarla y quedarse con el dispositivo que enlaza la propia noticia.
  private static final String HTML_NOTICIA = """
      <html><body>
        <table><tr><td><a href="xiaomi_18_pro_max-14958.php">Xiaomi 18 Pro Max</a></td></tr></table>
        <div id="review-body">
          <p>Oppo ha mostrado el <a href="oppo_k14_plus_official-news-70000.php">anuncio</a> del
             <a href="oppo_k14_plus_5g-14963.php">Oppo K14 Plus</a> en cinco colores.</p>
          <p>Llegara a India el 29 de septiembre.</p>
        </div>
        <div id="user-comments"><p>Comentario de un lector que no es parte de la noticia</p></div>
      </body></html>
      """;

  private static final String HTML_FICHA = """
      <html><body>
        <h1 class="specs-phone-name-title">Oppo K14 Plus</h1>
        <div id="specs-list">
          <table><tr><th rowspan="2">Network</th><td class="ttl">Technology</td><td class="nfo">GSM / LTE / 5G</td></tr></table>
          <table>
            <tr><th rowspan="2">Display</th><td class="ttl">Type</td><td class="nfo">AMOLED, 144Hz</td></tr>
            <tr><td class="ttl">Size</td><td class="nfo">6.77 inches</td></tr>
          </table>
          <table><tr><th rowspan="1">Battery</th><td class="ttl">Type</td><td class="nfo">8000 mAh</td></tr></table>
        </div>
      </body></html>
      """;


  @Test
  void extraeElTextoCompletoYLaFichaDelDispositivoQueEnlazaElCuerpo() {
    RestClient.Builder constructorClienteHttp = RestClient.builder();
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();
    servidorSimulado.expect(requestTo(URL_NOTICIA)).andRespond(withSuccess(HTML_NOTICIA, MediaType.TEXT_HTML));
    servidorSimulado.expect(requestTo("https://www.gsmarena.com/oppo_k14_plus_5g-14963.php"))
        .andRespond(withSuccess(HTML_FICHA, MediaType.TEXT_HTML));

    ClienteNoticiaGsmarena cliente = new ClienteNoticiaGsmarena(constructorClienteHttp.build());

    MaterialNoticiaGsmarena material = cliente.obtenerMaterial(URL_NOTICIA);

    assertThat(material.textoCuerpo())
        .isEqualTo("Oppo ha mostrado el anuncio del Oppo K14 Plus en cinco colores. Llegara a India el 29 de septiembre.");
    assertThat(material.fichaTecnica())
        .contains("Dispositivo: Oppo K14 Plus")
        .contains("Display - Type: AMOLED, 144Hz")
        .contains("Display - Size: 6.77 inches")
        .contains("Battery - Type: 8000 mAh")
        .doesNotContain("GSM / LTE / 5G");
    servidorSimulado.verify();
  }

  @Test
  void devuelveElTextoSinFichaCuandoLaNoticiaNoEnlazaNingunDispositivo() {
    RestClient.Builder constructorClienteHttp = RestClient.builder();
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();
    servidorSimulado.expect(requestTo(URL_NOTICIA)).andRespond(withSuccess(
        "<html><body><div id=\"review-body\"><p>Una noticia sin fichas.</p></div></body></html>",
        MediaType.TEXT_HTML));

    ClienteNoticiaGsmarena cliente = new ClienteNoticiaGsmarena(constructorClienteHttp.build());

    MaterialNoticiaGsmarena material = cliente.obtenerMaterial(URL_NOTICIA);

    assertThat(material.textoCuerpo()).isEqualTo("Una noticia sin fichas.");
    assertThat(material.fichaTecnica()).isNull();
  }

  @Test
  void soloReconoceNoticiasDeGsmarena() {
    ClienteNoticiaGsmarena cliente = new ClienteNoticiaGsmarena(RestClient.create());

    assertThat(cliente.esNoticiaDeGsmarena(URL_NOTICIA)).isTrue();
    assertThat(cliente.esNoticiaDeGsmarena("https://www.techpowerup.com/353118/intel")).isFalse();
    assertThat(cliente.esNoticiaDeGsmarena("no es una url")).isFalse();
  }

  @Test
  void distingueLosAnalisisDeGsmarenaDeSusNoticias() {
    ClienteNoticiaGsmarena cliente = new ClienteNoticiaGsmarena(RestClient.create());

    assertThat(cliente.esAnalisisDeGsmarena("https://www.gsmarena.com/apple_iphone_18_pro-review-3003.php")).isTrue();
    assertThat(cliente.esAnalisisDeGsmarena(URL_NOTICIA)).isFalse();
    assertThat(cliente.esAnalisisDeGsmarena("https://www.techpowerup.com/review/rtx-5090-review/")).isFalse();
  }
}
