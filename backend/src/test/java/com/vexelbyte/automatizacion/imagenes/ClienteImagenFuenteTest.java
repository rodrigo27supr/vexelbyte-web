package com.vexelbyte.automatizacion.imagenes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClienteImagenFuenteTest {

  private static final String URL_NOTICIA_GSMARENA =
      "https://www.gsmarena.com/oppo_k14_plus_official_images-news-74784.php";

  private static byte[] crearPng(int ancho, int alto) throws IOException {
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), "png", salida);
    return salida.toByteArray();
  }

  @Test
  void quitaElRecorteConLogotipoDeGsmarenaParaQuedarseConLaImagenOriginal() {
    RestClient.Builder constructorClienteHttp = RestClient.builder();
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();
    servidorSimulado.expect(requestTo(URL_NOTICIA_GSMARENA)).andRespond(withSuccess("""
        <html><head><meta property="og:image"
          content="https://fdn.gsmarena.com/imgroot/news/26/09/oppo-k14-plus-official-images/-952x498w6/gsmarena_001.jpg">
        </head><body></body></html>
        """, MediaType.TEXT_HTML));

    ClienteImagenFuente cliente = new ClienteImagenFuente(constructorClienteHttp.build());

    assertThat(cliente.obtenerUrlImagenPrincipal(URL_NOTICIA_GSMARENA))
        .contains("https://fdn.gsmarena.com/imgroot/news/26/09/oppo-k14-plus-official-images/gsmarena_001.jpg");
  }

  @Test
  void usaOgImageTalCualEnOtrasFuentesYNadaSiNoLaHay() {
    RestClient.Builder constructorClienteHttp = RestClient.builder();
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();
    servidorSimulado.expect(requestTo("https://www.techpowerup.com/353118/intel-presentmon"))
        .andRespond(withSuccess("""
            <html><head><meta property="og:image" content="https://www.techpowerup.com/img/Pleo9fe9R7d8suSY.jpg">
            </head></html>
            """, MediaType.TEXT_HTML));
    servidorSimulado.expect(requestTo("https://www.techpowerup.com/353119/sin-imagen"))
        .andRespond(withSuccess("<html><head></head></html>", MediaType.TEXT_HTML));

    ClienteImagenFuente cliente = new ClienteImagenFuente(constructorClienteHttp.build());

    assertThat(cliente.obtenerUrlImagenPrincipal("https://www.techpowerup.com/353118/intel-presentmon"))
        .contains("https://www.techpowerup.com/img/Pleo9fe9R7d8suSY.jpg");
    assertThat(cliente.obtenerUrlImagenPrincipal("https://www.techpowerup.com/353119/sin-imagen")).isEmpty();
  }

  @Test
  void descargaLaImagenConSusDimensionesYRechazaLoQueNoLoEs() throws IOException {
    RestClient.Builder constructorClienteHttp = RestClient.builder();
    MockRestServiceServer servidorSimulado = MockRestServiceServer.bindTo(constructorClienteHttp).build();
    servidorSimulado.expect(requestTo("https://www.techpowerup.com/img/foto.png"))
        .andRespond(withSuccess(crearPng(1200, 675), MediaType.IMAGE_PNG));
    servidorSimulado.expect(requestTo("https://www.techpowerup.com/img/bloqueada.jpg"))
        .andRespond(withSuccess("<html>Access denied</html>", MediaType.TEXT_HTML));

    ClienteImagenFuente cliente = new ClienteImagenFuente(constructorClienteHttp.build());

    ImagenDescargada imagen = cliente.descargarImagen("https://www.techpowerup.com/img/foto.png");
    assertThat(imagen.tipoContenido()).isEqualTo("image/png");
    assertThat(imagen.ancho()).isEqualTo(1200);
    assertThat(imagen.alto()).isEqualTo(675);
    assertThatThrownBy(() -> cliente.descargarImagen("https://www.techpowerup.com/img/bloqueada.jpg"))
        .isInstanceOf(IllegalStateException.class);
  }
}
