package com.vexelbyte.automatizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vexelbyte.articulos.Articulo;
import com.vexelbyte.articulos.ArticuloRepository;
import com.vexelbyte.articulos.CategoriaArticulo;
import com.vexelbyte.articulos.EspecificacionTecnica;
import com.vexelbyte.automatizacion.busqueda.ClienteBusquedaTavily;
import com.vexelbyte.automatizacion.busqueda.FragmentoAnalisis;
import com.vexelbyte.automatizacion.gsmarena.ClienteNoticiaGsmarena;
import com.vexelbyte.automatizacion.gsmarena.MaterialNoticiaGsmarena;
import com.vexelbyte.automatizacion.ia.PipelineGeneracionContenido;
import com.vexelbyte.automatizacion.ia.ResultadoGeneracionContenido;
import com.vexelbyte.automatizacion.ia.SinProveedorIADisponibleException;
import com.vexelbyte.automatizacion.ia.SolicitudGeneracionContenido;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import tools.jackson.databind.ObjectMapper;

class ServicioReescrituraArticulosTest {

  private static final Clock RELOJ_FIJO = Clock.fixed(Instant.parse("2026-09-28T10:00:00Z"), ZoneOffset.UTC);
  private static final String ENLACE_TECHPOWERUP =
      "https://www.techpowerup.com/353113/pny-reportedly-denies-melted-rtx-5090-warranty-claim";
  private static final String ENLACE_GSMARENA =
      "https://www.gsmarena.com/oppo_k14_plus_official_images_memory_options_colors-news-74784.php";
  private static final String CUERPO_PUBLICADO = "<h2>La garantía</h2><p>PNY rechaza la garantía de una RTX 5090 "
      + "fundida.</p><p>Fuente original: <a href=\"" + ENLACE_TECHPOWERUP + "\">techpowerup.com</a></p>";

  private final ArticuloRepository repositorioArticulos = mock(ArticuloRepository.class);
  private final ClienteBusquedaTavily clienteBusqueda = mock(ClienteBusquedaTavily.class);
  private final ClienteNoticiaGsmarena clienteNoticiaGsmarena = mock(ClienteNoticiaGsmarena.class);
  private final PipelineGeneracionContenido pipelineGeneracionContenido = mock(PipelineGeneracionContenido.class);

  private final ServicioReescrituraArticulos servicio = new ServicioReescrituraArticulos(
      repositorioArticulos, clienteBusqueda, clienteNoticiaGsmarena, pipelineGeneracionContenido, new ObjectMapper(),
      RELOJ_FIJO);

  @BeforeEach
  void configurarBusqueda() {
    when(clienteBusqueda.estaConfigurado()).thenReturn(true);
    when(clienteNoticiaGsmarena.esNoticiaDeGsmarena(anyString()))
        .thenAnswer(invocacion -> invocacion.<String>getArgument(0).contains("gsmarena.com"));
  }

  @Test
  void reescribeConLaCoberturaConservandoUrlFotoYSeccionYCitaATodosLosMedios() {
    Articulo articulo = crearArticuloPublicado(ENLACE_TECHPOWERUP);
    devolverPendientes(articulo);
    when(clienteBusqueda.buscarCoberturaDelUltimoMes(anyString(), anyString())).thenReturn(List.of(
        new FragmentoAnalisis("tomshardware.com", "PNY denies RTX 5090 warranty", "https://www.tomshardware.com/pny", "Texto"),
        new FragmentoAnalisis("pcgamer.com", "PNY warranty RTX 5090", "https://www.pcgamer.com/pny", "Texto")));
    when(pipelineGeneracionContenido.generarArticulo(any())).thenReturn(new ResultadoGeneracionContenido.ContenidoAprobado(
        "PNY niega la garantía de una RTX 5090 fundida", "Entradilla nueva", "<h2>Nuevo</h2><p>Texto largo.</p>",
        CategoriaArticulo.RENDIMIENTO, "GeForce RTX 5090", List.of(), List.of("Punto nuevo")));

    servicio.reescribirArticulosPendientes();

    ArgumentCaptor<SolicitudGeneracionContenido> solicitud = ArgumentCaptor.forClass(SolicitudGeneracionContenido.class);
    verify(pipelineGeneracionContenido).generarArticulo(solicitud.capture());
    // La consulta sale del titular en ingles que lleva la URL de la fuente, y el
    // material es el texto publicado sin el parrafo de creditos.
    verify(clienteBusqueda).buscarCoberturaDelUltimoMes(
        "pny reportedly denies melted rtx 5090 warranty claim", "techpowerup.com");
    assertThat(solicitud.getValue().resumenOriginal()).contains("PNY rechaza la garantía").doesNotContain("Fuente original");
    assertThat(solicitud.getValue().coberturaOtrosMedios()).contains("tomshardware.com").contains("pcgamer.com");

    assertThat(articulo.getSlug()).isEqualTo("pny-niega-garantia-abc123");
    assertThat(articulo.getFotoUrl()).isEqualTo("/api/articulos/pny-niega-garantia-abc123/foto");
    assertThat(articulo.getCategoria()).isEqualTo(CategoriaArticulo.HARDWARE_PC);
    assertThat(articulo.getTitulo()).isEqualTo("PNY niega la garantía de una RTX 5090 fundida");
    assertThat(articulo.getCuerpoHtml())
        .startsWith("<h2>Nuevo</h2>")
        .contains("Fuentes: ")
        .contains(ENLACE_TECHPOWERUP)
        .contains("https://www.tomshardware.com/pny")
        .contains("https://www.pcgamer.com/pny");
    // Una redaccion sin ficha no borra la que ya habia.
    assertThat(articulo.getEspecificacionesJson()).contains("Memoria");
    assertThat(articulo.getFechaActualizacion()).isEqualTo(OffsetDateTime.now(RELOJ_FIJO));
    assertThat(articulo.isRedaccionRevisada()).isTrue();
    verify(repositorioArticulos).save(articulo);
  }

  @Test
  void conservaElTextoSinGastarIaSiNadieMasHaPublicadoLaNoticia() {
    Articulo articulo = crearArticuloPublicado(ENLACE_TECHPOWERUP);
    devolverPendientes(articulo);
    when(clienteBusqueda.buscarCoberturaDelUltimoMes(anyString(), anyString())).thenReturn(List.of());

    servicio.reescribirArticulosPendientes();

    verify(pipelineGeneracionContenido, never()).generarArticulo(any());
    assertThat(articulo.getCuerpoHtml()).isEqualTo(CUERPO_PUBLICADO);
    assertThat(articulo.isRedaccionRevisada()).isTrue();
  }

  @Test
  void conservaElTextoPublicadoSiLaNuevaRedaccionNoPasaLaValidacion() {
    Articulo articulo = crearArticuloPublicado(ENLACE_TECHPOWERUP);
    devolverPendientes(articulo);
    when(clienteBusqueda.buscarCoberturaDelUltimoMes(anyString(), anyString())).thenReturn(List.of(
        new FragmentoAnalisis("tomshardware.com", "PNY", "https://www.tomshardware.com/pny", "Texto")));
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("cuerpo demasiado corto"));

    servicio.reescribirArticulosPendientes();

    assertThat(articulo.getCuerpoHtml()).isEqualTo(CUERPO_PUBLICADO);
    assertThat(articulo.isRedaccionRevisada()).isTrue();
  }

  @Test
  void dejaLosPendientesParaElSiguienteCicloSiNoQuedaProveedorDeIa() {
    Articulo primero = crearArticuloPublicado(ENLACE_TECHPOWERUP);
    Articulo segundo = crearArticuloPublicado(ENLACE_TECHPOWERUP);
    devolverPendientes(primero, segundo);
    when(clienteBusqueda.buscarCoberturaDelUltimoMes(anyString(), anyString())).thenReturn(List.of(
        new FragmentoAnalisis("tomshardware.com", "PNY", "https://www.tomshardware.com/pny", "Texto")));
    when(pipelineGeneracionContenido.generarArticulo(any())).thenThrow(new SinProveedorIADisponibleException("sin cuota"));

    servicio.reescribirArticulosPendientes();

    verify(pipelineGeneracionContenido, times(1)).generarArticulo(any());
    assertThat(primero.isRedaccionRevisada()).isFalse();
    assertThat(segundo.isRedaccionRevisada()).isFalse();
  }

  @Test
  void releeLaNoticiaCompletaDeGsmarenaYUsaSuTitularComoConsulta() {
    Articulo articulo = crearArticuloPublicado(ENLACE_GSMARENA);
    devolverPendientes(articulo);
    String textoCompleto = "Oppo ha publicado las imagenes oficiales del K14 Plus. ".repeat(20);
    when(clienteNoticiaGsmarena.obtenerMaterial(ENLACE_GSMARENA))
        .thenReturn(new MaterialNoticiaGsmarena(textoCompleto, "Bateria: 8000 mAh"));
    when(clienteBusqueda.buscarCoberturaDelUltimoMes(anyString(), anyString())).thenReturn(List.of(
        new FragmentoAnalisis("gizmochina.com", "Oppo K14 Plus", "https://www.gizmochina.com/oppo", "Texto")));
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("da igual en este test"));

    servicio.reescribirArticulosPendientes();

    verify(clienteBusqueda).buscarCoberturaDelUltimoMes(
        eq("oppo k14 plus official images memory options colors"), eq("gsmarena.com"));
    ArgumentCaptor<SolicitudGeneracionContenido> solicitud = ArgumentCaptor.forClass(SolicitudGeneracionContenido.class);
    verify(pipelineGeneracionContenido).generarArticulo(solicitud.capture());
    assertThat(solicitud.getValue().resumenOriginal()).isEqualTo(textoCompleto);
    assertThat(solicitud.getValue().fichaTecnica()).isEqualTo("Bateria: 8000 mAh");
  }

  @Test
  void noHaceNadaSinBusquedaConfigurada() {
    when(clienteBusqueda.estaConfigurado()).thenReturn(false);

    servicio.reescribirArticulosPendientes();

    verify(repositorioArticulos, never())
        .findByBorradorFalseAndRedaccionRevisadaFalseOrderByFechaPublicacionDesc(any(Limit.class));
    verify(pipelineGeneracionContenido, never()).generarArticulo(any());
  }

  private void devolverPendientes(Articulo... articulos) {
    when(repositorioArticulos.findByBorradorFalseAndRedaccionRevisadaFalseOrderByFechaPublicacionDesc(any(Limit.class)))
        .thenReturn(List.of(articulos));
  }

  private Articulo crearArticuloPublicado(String enlaceFuente) {
    Articulo articulo = new Articulo();
    articulo.setId(7L);
    articulo.setEnlaceFuente(enlaceFuente);
    articulo.setTitulo("PNY niega la garantía");
    articulo.setDescripcion("Entradilla antigua");
    articulo.setSlug("pny-niega-garantia-abc123");
    articulo.setCuerpoHtml(CUERPO_PUBLICADO);
    articulo.setCategoria(CategoriaArticulo.HARDWARE_PC);
    articulo.setFotoUrl("/api/articulos/pny-niega-garantia-abc123/foto");
    articulo.setEspecificacionesJson(new ObjectMapper().writeValueAsString(
        List.of(new EspecificacionTecnica("Memoria", "32 GB GDDR7"))));
    articulo.setRedaccionRevisada(false);
    return articulo;
  }
}
