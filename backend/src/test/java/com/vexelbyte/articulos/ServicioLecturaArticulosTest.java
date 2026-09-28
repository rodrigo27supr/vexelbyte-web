package com.vexelbyte.articulos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

class ServicioLecturaArticulosTest {

  private final ArticuloRepository repositorioArticulos = mock(ArticuloRepository.class);
  private final ServicioLecturaArticulos servicioLectura =
      new ServicioLecturaArticulos(repositorioArticulos, mock(FotoArticuloAlmacenadaRepository.class), new ObjectMapper());

  private Articulo construirArticuloPublicado(String slug) {
    Articulo articulo = new Articulo();
    articulo.setSlug(slug);
    articulo.setTitulo("Titulo de prueba");
    articulo.setDescripcion("Descripcion de prueba");
    articulo.setCuerpoHtml("<article><p>Cuerpo de prueba</p></article>");
    articulo.setFechaPublicacion(OffsetDateTime.parse("2027-03-15T10:00:00Z"));
    articulo.setAutor("Equipo VexelByte");
    articulo.setBorrador(false);
    return articulo;
  }

  @Test
  void listarPublicadosMapeaCategoriaYProducto() {
    Articulo articulo = construirArticuloPublicado("oppo-k14-plus");
    articulo.setCategoria(CategoriaArticulo.MOVILES);
    articulo.setProducto("Oppo K14 Plus");
    when(repositorioArticulos.findByBorradorFalseOrderByFechaPublicacionDesc()).thenReturn(List.of(articulo));

    List<ArticuloResumenResponse> resultado = servicioLectura.listarPublicados();

    // ArticuloResumenResponse no tiene cuerpoHtml: si alguien lo anadiera por
    // error, el listado empezaria a cargar el articulo entero de cada noticia.
    assertThat(resultado).singleElement().satisfies(resumen -> {
      assertThat(resumen.slug()).isEqualTo("oppo-k14-plus");
      assertThat(resumen.categoria()).isEqualTo("MOVILES");
      assertThat(resumen.producto()).isEqualTo("Oppo K14 Plus");
    });
  }

  @Test
  void listarPublicadosIncluyeLaFotoYLosTresPrimerosDatosClaveQueCabenEnUnaLinea() {
    Articulo conFoto = construirArticuloPublicado("iphone-18-pro");
    conFoto.setEspecificacionesJson("""
        [{"nombre":"Velocidad teórica máxima","valor":"46 Gbps"},{"nombre":"Pantalla","valor":"6,3 pulgadas"},
         {"nombre":"Procesador","valor":"A20 Pro"},{"nombre":"Cámara","valor":"48 MP con apertura variable f/1.5-f/4.0"},
         {"nombre":"Batería","valor":"4.056 mAh"},{"nombre":"Peso","valor":"199 g"}]""");
    conFoto.setFotoUrl("https://upload.wikimedia.org/iphone.jpg");
    conFoto.setFotoAncho(1280);
    conFoto.setFotoAlto(901);
    conFoto.setFotoAutor("Kyu3a");
    conFoto.setFotoLicencia("CC BY-SA 4.0");
    Articulo sinFoto = construirArticuloPublicado("sin-foto");
    when(repositorioArticulos.findByBorradorFalseOrderByFechaPublicacionDesc()).thenReturn(List.of(conFoto, sinFoto));

    List<ArticuloResumenResponse> resultado = servicioLectura.listarPublicados();

    assertThat(resultado.get(0).datosClave()).extracting(EspecificacionTecnica::nombre)
        .containsExactly("Pantalla", "Procesador", "Batería");
    // La foto la sirve nuestra API, nunca Wikimedia directamente.
    assertThat(resultado.get(0).foto().url()).isEqualTo("/api/articulos/iphone-18-pro/foto");
    assertThat(resultado.get(0).foto().autor()).isEqualTo("Kyu3a");
    assertThat(resultado.get(1).foto()).isNull();
    assertThat(resultado.get(1).datosClave()).isEmpty();
  }

  @Test
  void obtenerPorSlugDevuelveElDetalleConLasListasDeserializadas() {
    Articulo articulo = construirArticuloPublicado("oppo-k14-plus");
    articulo.setEspecificacionesJson("[{\"nombre\":\"Batería\",\"valor\":\"8000 mAh\"}]");
    articulo.setPuntosClaveJson("[\"Batería enorme\"]");
    when(repositorioArticulos.findBySlugAndBorradorFalse("oppo-k14-plus")).thenReturn(Optional.of(articulo));

    ResponseEntity<ArticuloDetalleResponse> respuesta = servicioLectura.obtenerPorSlug("oppo-k14-plus");

    assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(respuesta.getBody().cuerpoHtml()).isEqualTo("<article><p>Cuerpo de prueba</p></article>");
    assertThat(respuesta.getBody().especificaciones()).containsExactly(new EspecificacionTecnica("Batería", "8000 mAh"));
    assertThat(respuesta.getBody().puntosClave()).containsExactly("Batería enorme");
  }

  @Test
  void devuelveListasVaciasNuncaNulasCuandoElArticuloNoTieneDatosEditoriales() {
    when(repositorioArticulos.findBySlugAndBorradorFalse("sin-datos"))
        .thenReturn(Optional.of(construirArticuloPublicado("sin-datos")));

    ArticuloDetalleResponse detalle = servicioLectura.obtenerPorSlug("sin-datos").getBody();

    assertThat(detalle.especificaciones()).isEmpty();
    assertThat(detalle.puntosClave()).isEmpty();
  }

  @Test
  void obtenerPorSlugDevuelve404CuandoNoExisteOEstaDespublicado() {
    when(repositorioArticulos.findBySlugAndBorradorFalse("no-existe")).thenReturn(Optional.empty());

    ResponseEntity<ArticuloDetalleResponse> respuesta = servicioLectura.obtenerPorSlug("no-existe");

    assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(respuesta.getBody()).isNull();
  }
}
