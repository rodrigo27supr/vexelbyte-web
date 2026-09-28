package com.vexelbyte.articulos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

// Levanto un servidor HTTP real (webEnvironment=RANDOM_PORT) porque es la
// unica forma de comprobar de verdad que SecurityConfig deja pasar la API sin
// autenticacion: llamar al controlador en Java nunca pasa por la cadena de
// filtros de seguridad. Base de datos H2 propia para no compartir estado.
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:vexelbyte-test-lectura-publica;DB_CLOSE_DELAY=-1")
class LecturaPublicaArticulosSeguridadTest {

  @LocalServerPort
  private int puertoAsignado;

  @Autowired
  private ArticuloRepository repositorioArticulos;

  @Autowired
  private FotoArticuloAlmacenadaRepository repositorioFotos;

  private RestTestClient clienteHttpPrueba;

  @BeforeEach
  void construirClienteHttp() {
    clienteHttpPrueba = RestTestClient.bindToServer().baseUrl("http://localhost:" + puertoAsignado).build();
  }

  @AfterEach
  void limpiarArticulosDePrueba() {
    repositorioFotos.deleteAll();
    repositorioArticulos.deleteAll();
  }

  @Test
  void elListadoEsAccesibleSinAutenticacion() {
    clienteHttpPrueba.get().uri("/api/articulos").exchange().expectStatus().isOk();
  }

  @Test
  void lasRutasRetiradasYaNoExisten() {
    // Analisis, hardware y afiliados se retiraron: sin permitAll caen en
    // anyRequest().authenticated() y nunca responden contenido.
    clienteHttpPrueba.get().uri("/api/analisis").exchange().expectStatus().is4xxClientError();
    clienteHttpPrueba.get().uri("/api/hardware").exchange().expectStatus().is4xxClientError();
  }

  @Test
  void elListadoYElDetalleExcluyenLosBorradores() {
    repositorioArticulos.save(construirArticulo("noticia-publicada", false));
    repositorioArticulos.save(construirArticulo("noticia-borrador", true));

    ArticuloResumenResponse[] listado = clienteHttpPrueba.get().uri("/api/articulos")
        .exchange()
        .expectStatus().isOk()
        .returnResult(ArticuloResumenResponse[].class)
        .getResponseBody();

    assertThat(listado).extracting(ArticuloResumenResponse::slug).containsExactly("noticia-publicada");
    clienteHttpPrueba.get().uri("/api/articulos/noticia-borrador").exchange().expectStatus().isNotFound();
  }

  @Test
  void unaNoticiaRecienRedactadaEsperaASuFotoSalvoQueLleveMasDeUnDiaFallando() {
    Articulo recienRedactada = construirArticulo("recien-redactada", false);
    recienRedactada.setFotoRevisada(false);
    Articulo fotoFallandoDesdeAyer = construirArticulo("foto-fallando-desde-ayer", false);
    fotoFallandoDesdeAyer.setFotoRevisada(false);
    fotoFallandoDesdeAyer.setFechaCreacion(OffsetDateTime.now().minusHours(25));
    repositorioArticulos.save(recienRedactada);
    repositorioArticulos.save(fotoFallandoDesdeAyer);

    ArticuloResumenResponse[] listado = clienteHttpPrueba.get().uri("/api/articulos")
        .exchange()
        .expectStatus().isOk()
        .returnResult(ArticuloResumenResponse[].class)
        .getResponseBody();

    assertThat(listado).extracting(ArticuloResumenResponse::slug).containsExactly("foto-fallando-desde-ayer");
    clienteHttpPrueba.get().uri("/api/articulos/recien-redactada").exchange().expectStatus().isNotFound();
  }

  @Test
  void elDetalleIncluyeLaFichaTecnicaYLosPuntosClaveGuardadosComoJson() {
    Articulo articulo = construirArticulo("oppo-k14-plus", false);
    articulo.setCategoria(CategoriaArticulo.MOVILES);
    articulo.setProducto("Oppo K14 Plus");
    articulo.setEspecificacionesJson("[{\"nombre\":\"Batería\",\"valor\":\"8000 mAh\"}]");
    articulo.setPuntosClaveJson("[\"Batería enorme\"]");
    repositorioArticulos.save(articulo);

    ArticuloDetalleResponse detalle = clienteHttpPrueba.get().uri("/api/articulos/oppo-k14-plus")
        .exchange()
        .expectStatus().isOk()
        .returnResult(ArticuloDetalleResponse.class)
        .getResponseBody();

    assertThat(detalle.categoria()).isEqualTo("MOVILES");
    assertThat(detalle.producto()).isEqualTo("Oppo K14 Plus");
    assertThat(detalle.especificaciones()).containsExactly(new EspecificacionTecnica("Batería", "8000 mAh"));
    assertThat(detalle.puntosClave()).containsExactly("Batería enorme");
  }

  @Test
  void laFotoSeSirveSinAutenticacionSoloSiElArticuloEstaPublicado() {
    Articulo publicado = repositorioArticulos.save(construirArticulo("con-foto", false));
    Articulo borrador = repositorioArticulos.save(construirArticulo("borrador-con-foto", true));
    guardarFoto(publicado.getId());
    guardarFoto(borrador.getId());

    byte[] contenido = clienteHttpPrueba.get().uri("/api/articulos/con-foto/foto")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType("image/jpeg")
        .returnResult(byte[].class)
        .getResponseBody();

    assertThat(contenido).containsExactly(1, 2, 3);
    clienteHttpPrueba.get().uri("/api/articulos/borrador-con-foto/foto").exchange().expectStatus().isNotFound();
    clienteHttpPrueba.get().uri("/api/articulos/no-existe/foto").exchange().expectStatus().isNotFound();
  }

  private void guardarFoto(Long articuloId) {
    FotoArticuloAlmacenada foto = new FotoArticuloAlmacenada();
    foto.setArticuloId(articuloId);
    foto.setContenido(new byte[] {1, 2, 3});
    foto.setTipoContenido("image/jpeg");
    repositorioFotos.save(foto);
  }

  private Articulo construirArticulo(String slug, boolean borrador) {
    Articulo articulo = new Articulo();
    articulo.setSlug(slug);
    articulo.setTitulo("Titulo de prueba");
    articulo.setDescripcion("Descripcion de prueba");
    articulo.setCuerpoHtml("<article><p>Cuerpo de prueba</p></article>");
    articulo.setFechaPublicacion(OffsetDateTime.now());
    articulo.setFechaCreacion(OffsetDateTime.now());
    articulo.setAutor("Equipo VexelByte");
    articulo.setBorrador(borrador);
    articulo.setFotoRevisada(true);
    return articulo;
  }
}
