package com.vexelbyte.automatizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vexelbyte.articulos.CategoriaArticulo;
import com.vexelbyte.articulos.EspecificacionTecnica;
import com.vexelbyte.automatizacion.busqueda.ClienteBusquedaTavily;
import com.vexelbyte.automatizacion.busqueda.FragmentoAnalisis;
import com.vexelbyte.automatizacion.config.PropiedadesFeedHardware;
import com.vexelbyte.automatizacion.gsmarena.ClienteNoticiaGsmarena;
import com.vexelbyte.automatizacion.gsmarena.MaterialNoticiaGsmarena;
import com.vexelbyte.automatizacion.ia.ClasificadorTitularesIA;
import com.vexelbyte.automatizacion.ia.PipelineGeneracionContenido;
import com.vexelbyte.automatizacion.ia.ResultadoGeneracionContenido;
import com.vexelbyte.automatizacion.ia.SinProveedorIADisponibleException;
import com.vexelbyte.automatizacion.ia.SolicitudGeneracionContenido;
import com.vexelbyte.automatizacion.imagenes.ServicioFotosArticulos;
import com.vexelbyte.automatizacion.persistencia.DatosEditorialesArticulo;
import com.vexelbyte.automatizacion.persistencia.DatosFuenteArticulo;
import com.vexelbyte.automatizacion.persistencia.RegistroNoticiasDescartadas;
import com.vexelbyte.automatizacion.persistencia.ResultadoIngesta;
import com.vexelbyte.automatizacion.persistencia.ServicioIngestaArticulos;
import com.vexelbyte.automatizacion.rss.ClienteFeedNoticiasHardware;
import com.vexelbyte.automatizacion.rss.NoticiaHardwareRss;
import java.time.Instant;
import java.util.List;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

class TrabajoSincronizacionContenidoTest {

  private static final String URL_FEED_PC = "https://pc.example/rss";
  private static final String URL_FEED_MOVILES = "https://moviles.example/rss";

  private final ClienteFeedNoticiasHardware clienteFeed = mock(ClienteFeedNoticiasHardware.class);
  private final ClienteNoticiaGsmarena clienteNoticiaGsmarena = mock(ClienteNoticiaGsmarena.class);
  private final ClasificadorTitularesIA clasificadorTitulares = mock(ClasificadorTitularesIA.class);
  private final PipelineGeneracionContenido pipelineGeneracionContenido = mock(PipelineGeneracionContenido.class);
  private final ServicioIngestaArticulos servicioIngestaArticulos = mock(ServicioIngestaArticulos.class);
  private final RegistroNoticiasDescartadas registroDescartadas = mock(RegistroNoticiasDescartadas.class);
  private final ClienteBusquedaTavily clienteBusqueda = mock(ClienteBusquedaTavily.class);
  private final ServicioFotosArticulos servicioFotos = mock(ServicioFotosArticulos.class);
  private final CalendarioSecciones calendarioSecciones = mock(CalendarioSecciones.class);
  private final ServicioReescrituraArticulos servicioReescritura = mock(ServicioReescrituraArticulos.class);

  // El preparador es real, con sus clientes externos simulados: asi estos
  // tests cubren tambien la decision de que material recibe la IA.
  private final TrabajoSincronizacionContenido trabajo = new TrabajoSincronizacionContenido(
      clienteFeed, new PreparadorMaterialFuente(clienteNoticiaGsmarena, clienteBusqueda), clasificadorTitulares,
      pipelineGeneracionContenido, servicioIngestaArticulos, registroDescartadas, servicioFotos, calendarioSecciones,
      servicioReescritura, new PropiedadesFeedHardware(List.of(URL_FEED_PC, URL_FEED_MOVILES), 2));

  // Por defecto todas las secciones estan abiertas y el clasificador aprueba
  // cada titular en una seccion distinta (rotando), para que el ritmo por
  // seccion no interfiera en los tests que no tratan de el.
  @BeforeEach
  void abrirTodasLasSeccionesYAprobarLosTitulares() {
    when(calendarioSecciones.obtenerSeccionesAbiertas()).thenReturn(EnumSet.allOf(CategoriaArticulo.class));
    when(clasificadorTitulares.clasificarTitulares(any())).thenAnswer(invocacion -> {
      List<String> titulares = invocacion.getArgument(0);
      // Mockito llama a este stub con null cuando un test lo vuelve a simular.
      if (titulares == null) {
        return Optional.empty();
      }
      CategoriaArticulo[] secciones = CategoriaArticulo.values();
      Map<Integer, CategoriaArticulo> seccionesPorPosicion = new HashMap<>();
      for (int posicion = 0; posicion < titulares.size(); posicion++) {
        seccionesPorPosicion.put(posicion, secciones[posicion % secciones.length]);
      }
      return Optional.of(seccionesPorPosicion);
    });
  }

  private static NoticiaHardwareRss noticia(String identificador, String titulo) {
    return noticiaPublicadaEn(identificador, titulo, "2027-03-16T10:00:00Z");
  }

  private static NoticiaHardwareRss noticiaPublicadaEn(String identificador, String titulo, String instante) {
    return new NoticiaHardwareRss(
        identificador, titulo, "https://www.fuente.example/" + identificador, resumenSuficiente(titulo),
        Instant.parse(instante));
  }

  // Por encima del minimo de texto de fuente que exige el job para redactar.
  private static String resumenSuficiente(String titulo) {
    return ("Resumen de " + titulo + ". ").repeat(40).strip();
  }

  // El RSS de GSMArena trae sus analisis sin texto, por eso el resumen va vacio.
  private NoticiaHardwareRss analisisDeGsmarena(String identificador, String titulo) {
    String enlace = "https://www.gsmarena.com/" + identificador + "-review-1.php";
    when(clienteNoticiaGsmarena.esAnalisisDeGsmarena(enlace)).thenReturn(true);
    return new NoticiaHardwareRss(identificador, titulo, enlace, "", Instant.parse("2027-03-16T10:00:00Z"));
  }

  private static NoticiaHardwareRss noticiaDeGsmarena(String enlace) {
    return new NoticiaHardwareRss(
        "guid-1", "Oppo K14 Plus official", enlace, resumenSuficiente("Oppo K14 Plus official"),
        Instant.parse("2027-03-16T10:00:00Z"));
  }

  private void simularFeeds(List<NoticiaHardwareRss> noticiasPc, List<NoticiaHardwareRss> noticiasMoviles) {
    when(clienteFeed.obtenerNoticiasRecientes(URL_FEED_PC)).thenReturn(noticiasPc);
    when(clienteFeed.obtenerNoticiasRecientes(URL_FEED_MOVILES)).thenReturn(noticiasMoviles);
  }

  @Test
  void persisteElArticuloAprobadoConElTitularDeLaIaYElCreditoALaFuente() {
    simularFeeds(List.of(noticia("guid-1", "RTX 5090 review")), List.of());
    when(pipelineGeneracionContenido.generarArticulo(any())).thenReturn(
        new ResultadoGeneracionContenido.ContenidoAprobado(
            "La RTX 5090 rinde un 40% mas en 4K", "Entradilla de la RTX 5090", "<article><p>Texto</p></article>",
            CategoriaArticulo.HARDWARE_PC, "GeForce RTX 5090",
            List.of(new EspecificacionTecnica("Memoria", "32 GB GDDR7")), List.of("Rinde un 40% mas en 4K")));
    when(servicioIngestaArticulos.ingestarArticulo(any(), anyString(), any()))
        .thenReturn(new ResultadoIngesta.ArticuloCreado(1L));

    trabajo.sincronizarNoticiasHardware();

    ArgumentCaptor<SolicitudGeneracionContenido> capturadorSolicitud =
        ArgumentCaptor.forClass(SolicitudGeneracionContenido.class);
    verify(pipelineGeneracionContenido).generarArticulo(capturadorSolicitud.capture());
    assertThat(capturadorSolicitud.getValue().tituloOriginal()).isEqualTo("RTX 5090 review");
    assertThat(capturadorSolicitud.getValue().resumenOriginal()).isEqualTo(resumenSuficiente("RTX 5090 review"));

    ArgumentCaptor<DatosFuenteArticulo> capturadorDatosFuente = ArgumentCaptor.forClass(DatosFuenteArticulo.class);
    ArgumentCaptor<String> capturadorCuerpo = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<DatosEditorialesArticulo> capturadorDatosEditoriales =
        ArgumentCaptor.forClass(DatosEditorialesArticulo.class);
    verify(servicioIngestaArticulos).ingestarArticulo(
        capturadorDatosFuente.capture(), capturadorCuerpo.capture(), capturadorDatosEditoriales.capture());
    assertThat(capturadorDatosFuente.getValue().idExternoFuente()).isEqualTo("guid-1");
    // El titular publicado es el que redacto la IA, no el crudo en ingles.
    assertThat(capturadorDatosFuente.getValue().titulo()).isEqualTo("La RTX 5090 rinde un 40% mas en 4K");
    // La entradilla de la IA es la meta description, ya no el titular repetido.
    assertThat(capturadorDatosFuente.getValue().descripcion()).isEqualTo("Entradilla de la RTX 5090");
    assertThat(capturadorDatosEditoriales.getValue()).isEqualTo(new DatosEditorialesArticulo(
        CategoriaArticulo.HARDWARE_PC, "GeForce RTX 5090",
        List.of(new EspecificacionTecnica("Memoria", "32 GB GDDR7")), List.of("Rinde un 40% mas en 4K")));
    assertThat(capturadorCuerpo.getValue()).isEqualTo(
        "<article><p>Texto</p></article><p>Fuente original: <a href=\"https://www.fuente.example/guid-1\""
            + " rel=\"nofollow noopener noreferrer\">fuente.example</a></p>");
  }

  @Test
  void noAnadeCreditoCuandoElEnlaceDeLaFuenteNoEsHttp() {
    NoticiaHardwareRss noticiaConEnlaceMalicioso = new NoticiaHardwareRss(
        "guid-1", "RTX 5090 review", "javascript:alert(1)", resumenSuficiente("RTX 5090 review"),
        Instant.parse("2027-03-16T10:00:00Z"));
    simularFeeds(List.of(noticiaConEnlaceMalicioso), List.of());
    when(pipelineGeneracionContenido.generarArticulo(any())).thenReturn(
        new ResultadoGeneracionContenido.ContenidoAprobado("Titular", "Entradilla", "<article></article>", null, null, List.of(), List.of()));
    when(servicioIngestaArticulos.ingestarArticulo(any(), anyString(), any()))
        .thenReturn(new ResultadoIngesta.ArticuloCreado(1L));

    trabajo.sincronizarNoticiasHardware();

    verify(servicioIngestaArticulos).ingestarArticulo(any(), eq("<article></article>"), any());
  }

  @Test
  void procesaLasNoticiasMasRecientesMezclandoTodosLosFeeds() {
    simularFeeds(
        List.of(noticiaPublicadaEn("pc-antigua", "PC antigua", "2027-03-10T10:00:00Z"),
            noticiaPublicadaEn("pc-reciente", "PC reciente", "2027-03-15T10:00:00Z")),
        List.of(noticiaPublicadaEn("movil-reciente", "Movil reciente", "2027-03-16T10:00:00Z")));
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("no importa"));

    trabajo.sincronizarNoticiasHardware();

    // Tope de 2: entran la del feed de moviles y la mas reciente del de PC,
    // aunque el feed de PC se lea primero.
    ArgumentCaptor<SolicitudGeneracionContenido> capturadorSolicitud =
        ArgumentCaptor.forClass(SolicitudGeneracionContenido.class);
    verify(pipelineGeneracionContenido, times(2)).generarArticulo(capturadorSolicitud.capture());
    assertThat(capturadorSolicitud.getAllValues())
        .extracting(SolicitudGeneracionContenido::tituloOriginal)
        .containsExactly("Movil reciente", "PC reciente");
  }

  @Test
  void unFeedCaidoNoImpideProcesarLosDemas() {
    when(clienteFeed.obtenerNoticiasRecientes(URL_FEED_PC))
        .thenThrow(new ResourceAccessException("timeout simulado, reintentos ya agotados"));
    when(clienteFeed.obtenerNoticiasRecientes(URL_FEED_MOVILES))
        .thenReturn(List.of(noticia("movil-1", "Galaxy S27 Ultra")));
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("no importa"));

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, times(1)).generarArticulo(any());
  }

  @Test
  void noPersisteNadaCuandoLaIaDescartaLaNoticiaPorTematica() {
    simularFeeds(List.of(noticia("guid-1", "Parche 1.2 de un juego")), List.of());
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoFueraDeTematica("nota de parche"));

    trabajo.sincronizarNoticiasHardware();

    verify(servicioIngestaArticulos, never()).ingestarArticulo(any(), anyString(), any());
  }

  @Test
  void registraElDescarteParaNoVolverAPreguntarPorLaNoticiaEnOtroArranque() {
    simularFeeds(List.of(noticia("guid-1", "Parche 1.2 de un juego")), List.of());
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoFueraDeTematica("nota de parche"));

    trabajo.sincronizarNoticiasHardware();

    verify(registroDescartadas).registrarDescarte("guid-1", "nota de parche");
  }

  @Test
  void noLlamaALaIaPorNoticiasYaDescartadasEnCiclosAnteriores() {
    simularFeeds(List.of(noticia("guid-1", "Parche 1.2 de un juego")), List.of());
    when(registroDescartadas.estaDescartada("guid-1")).thenReturn(true);

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, never()).generarArticulo(any());
  }

  @Test
  void unFalloAlRegistrarElDescarteNoTumbaElRestoDelLote() {
    simularFeeds(List.of(
        noticiaPublicadaEn("guid-1", "Parche", "2027-03-16T10:00:00Z"),
        noticiaPublicadaEn("guid-2", "RTX 5090", "2027-03-15T10:00:00Z")), List.of());
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoFueraDeTematica("nota de parche"));
    doThrow(new IllegalStateException("base de datos caida"))
        .when(registroDescartadas).registrarDescarte(anyString(), anyString());

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, times(2)).generarArticulo(any());
  }

  @Test
  void vuelveAIntentarUnaNoticiaCuyoContenidoFueRechazadoPorUnFalloTecnico() {
    simularFeeds(List.of(noticia("guid-1", "RTX 5090 review")), List.of());
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("JSON invalido"));

    trabajo.sincronizarNoticiasHardware();
    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, times(2)).generarArticulo(any());
  }

  @Test
  void noLlamaALaIaPorNoticiasQueYaEstanIngeridas() {
    simularFeeds(List.of(noticia("guid-1", "Ya guardada")), List.of());
    when(servicioIngestaArticulos.yaEstaIngerida("guid-1")).thenReturn(true);

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, never()).generarArticulo(any());
  }

  @Test
  void respetaElTopeDeNoticiasPorCicloSinContarLasYaIngeridas() {
    simularFeeds(List.of(
        noticiaPublicadaEn("guid-1", "Ya guardada", "2027-03-16T10:00:00Z"),
        noticiaPublicadaEn("guid-2", "Nueva A", "2027-03-15T10:00:00Z"),
        noticiaPublicadaEn("guid-3", "Nueva B", "2027-03-14T10:00:00Z"),
        noticiaPublicadaEn("guid-4", "Nueva C", "2027-03-13T10:00:00Z")), List.of());
    when(servicioIngestaArticulos.yaEstaIngerida("guid-1")).thenReturn(true);
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("no importa"));

    trabajo.sincronizarNoticiasHardware();

    // Tope de 2: la ya ingerida se salta sin consumir cupo, asi que se
    // procesan guid-2 y guid-3 y guid-4 queda para el siguiente ciclo.
    verify(pipelineGeneracionContenido, times(2)).generarArticulo(any());
  }

  @Test
  void unFalloDeLaIaEnUnaNoticiaNoImpideProcesarLaSiguiente() {
    simularFeeds(List.of(
        noticiaPublicadaEn("guid-1", "Primera", "2027-03-16T10:00:00Z"),
        noticiaPublicadaEn("guid-2", "Segunda", "2027-03-15T10:00:00Z")), List.of());
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenThrow(new IllegalStateException("429 de la IA"))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoAprobado("Titular", "Entradilla", "<article></article>", null, null, List.of(), List.of()));
    when(servicioIngestaArticulos.ingestarArticulo(any(), anyString(), any()))
        .thenReturn(new ResultadoIngesta.ArticuloCreado(2L));

    trabajo.sincronizarNoticiasHardware();

    verify(servicioIngestaArticulos, times(1)).ingestarArticulo(any(), anyString(), any());
  }

  @Test
  void cortaElCicloCuandoNingunProveedorDeIaEstaDisponible() {
    simularFeeds(List.of(
        noticiaPublicadaEn("guid-1", "Primera", "2027-03-16T10:00:00Z"),
        noticiaPublicadaEn("guid-2", "Segunda", "2027-03-15T10:00:00Z")), List.of());
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenThrow(new SinProveedorIADisponibleException("todos los proveedores agotados"));

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, times(1)).generarArticulo(any());
    verify(registroDescartadas, never()).registrarDescarte(anyString(), anyString());
  }

  @Test
  void redactaConElTextoCompletoYLaFichaDeLaPaginaDeGsmarena() {
    NoticiaHardwareRss noticiaDeMovil = noticiaDeGsmarena("https://www.gsmarena.com/oppo_k14_plus-news-1.php");
    String textoCompleto = resumenSuficiente("Oppo K14 Plus official") + " Mas datos de la pagina completa.";
    simularFeeds(List.of(), List.of(noticiaDeMovil));
    when(clienteNoticiaGsmarena.esNoticiaDeGsmarena(noticiaDeMovil.enlaceOriginal())).thenReturn(true);
    when(clienteNoticiaGsmarena.obtenerMaterial(noticiaDeMovil.enlaceOriginal()))
        .thenReturn(new MaterialNoticiaGsmarena(textoCompleto, "Battery - Type: 8000 mAh"));
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("no importa"));

    trabajo.sincronizarNoticiasHardware();

    ArgumentCaptor<SolicitudGeneracionContenido> capturadorSolicitud =
        ArgumentCaptor.forClass(SolicitudGeneracionContenido.class);
    verify(pipelineGeneracionContenido).generarArticulo(capturadorSolicitud.capture());
    assertThat(capturadorSolicitud.getValue().resumenOriginal()).isEqualTo(textoCompleto);
    assertThat(capturadorSolicitud.getValue().fichaTecnica()).isEqualTo("Battery - Type: 8000 mAh");
  }

  @Test
  void redactaConElExtractoDelRssCuandoLaPaginaDeGsmarenaFalla() {
    NoticiaHardwareRss noticiaDeMovil = noticiaDeGsmarena("https://www.gsmarena.com/oppo_k14_plus-news-1.php");
    simularFeeds(List.of(), List.of(noticiaDeMovil));
    when(clienteNoticiaGsmarena.esNoticiaDeGsmarena(noticiaDeMovil.enlaceOriginal())).thenReturn(true);
    when(clienteNoticiaGsmarena.obtenerMaterial(noticiaDeMovil.enlaceOriginal()))
        .thenThrow(new ResourceAccessException("timeout simulado"));
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("no importa"));

    trabajo.sincronizarNoticiasHardware();

    ArgumentCaptor<SolicitudGeneracionContenido> capturadorSolicitud =
        ArgumentCaptor.forClass(SolicitudGeneracionContenido.class);
    verify(pipelineGeneracionContenido).generarArticulo(capturadorSolicitud.capture());
    assertThat(capturadorSolicitud.getValue().resumenOriginal()).isEqualTo(noticiaDeMovil.resumen());
    assertThat(capturadorSolicitud.getValue().fichaTecnica()).isNull();
  }

  @Test
  void descartaLosAnalisisDeGsmarenaSinLlamarALaIaSiNoHayBusquedaConfigurada() {
    NoticiaHardwareRss analisis = analisisDeGsmarena("guid-1", "Apple iPhone 18 Pro review");
    simularFeeds(List.of(), List.of(analisis));

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, never()).generarArticulo(any());
    verify(clienteBusqueda, never()).buscarAnalisis(anyString());
    verify(registroDescartadas).registrarDescarte(eq("guid-1"), anyString());
  }

  @Test
  void resumeElAnalisisConLosFragmentosDeVariosMediosYLosCitaATodos() {
    NoticiaHardwareRss analisis = analisisDeGsmarena("guid-1", "Apple iPhone 18 Pro review");
    simularFeeds(List.of(), List.of(analisis));
    when(clienteBusqueda.estaConfigurado()).thenReturn(true);
    when(clienteBusqueda.buscarAnalisis("Apple iPhone 18 Pro")).thenReturn(List.of(
        new FragmentoAnalisis("gsmarena.com", "iPhone 18 Pro review", analisis.enlaceOriginal(), "GSMArena opina..."),
        new FragmentoAnalisis("theverge.com", "iPhone 18 Pro review", "https://www.theverge.com/iphone-18-pro", "..."),
        new FragmentoAnalisis("engadget.com", "iPhone 18 Pro review", "https://www.engadget.com/iphone-18-pro", "...")));
    when(clienteNoticiaGsmarena.obtenerMaterial(analisis.enlaceOriginal()))
        .thenReturn(new MaterialNoticiaGsmarena("Intro", "Chipset: Apple A20 Pro"));
    when(pipelineGeneracionContenido.generarArticulo(any())).thenReturn(
        new ResultadoGeneracionContenido.ContenidoAprobado(
            "iPhone 18 Pro: que dicen los primeros analisis", "Entradilla", "<article></article>",
            CategoriaArticulo.MOVILES, "Apple iPhone 18 Pro", List.of(), List.of()));
    when(servicioIngestaArticulos.ingestarArticulo(any(), anyString(), any()))
        .thenReturn(new ResultadoIngesta.ArticuloCreado(1L));

    trabajo.sincronizarNoticiasHardware();

    ArgumentCaptor<SolicitudGeneracionContenido> capturadorSolicitud =
        ArgumentCaptor.forClass(SolicitudGeneracionContenido.class);
    verify(pipelineGeneracionContenido).generarArticulo(capturadorSolicitud.capture());
    SolicitudGeneracionContenido solicitud = capturadorSolicitud.getValue();
    assertThat(solicitud.esResumenDeAnalisis()).isTrue();
    assertThat(solicitud.resumenOriginal()).contains("Medio: theverge.com").contains("Medio: engadget.com");
    assertThat(solicitud.fichaTecnica()).isEqualTo("Chipset: Apple A20 Pro");

    ArgumentCaptor<String> capturadorCuerpo = ArgumentCaptor.forClass(String.class);
    verify(servicioIngestaArticulos).ingestarArticulo(any(), capturadorCuerpo.capture(), any());
    assertThat(capturadorCuerpo.getValue())
        .contains("<p>Fuentes: ")
        .contains(">gsmarena.com</a>")
        .contains(">theverge.com</a>")
        .contains(">engadget.com</a>");
  }

  @Test
  void descartaElAnalisisSinGastarIaSiPocosMediosLoHanPublicado() {
    NoticiaHardwareRss analisis = analisisDeGsmarena("guid-1", "Apple iPhone 18 Pro review");
    simularFeeds(List.of(), List.of(analisis));
    when(clienteBusqueda.estaConfigurado()).thenReturn(true);
    when(clienteBusqueda.buscarAnalisis("Apple iPhone 18 Pro")).thenReturn(List.of(
        new FragmentoAnalisis("theverge.com", "iPhone 18 Pro review", "https://www.theverge.com/iphone", "...")));

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, never()).generarArticulo(any());
    verify(registroDescartadas).registrarDescarte(eq("guid-1"), anyString());
  }

  @Test
  void aplazaElAnalisisSinDescartarloSiLaBusquedaFalla() {
    NoticiaHardwareRss analisis = analisisDeGsmarena("guid-1", "Apple iPhone 18 Pro review");
    simularFeeds(List.of(), List.of(analisis));
    when(clienteBusqueda.estaConfigurado()).thenReturn(true);
    when(clienteBusqueda.buscarAnalisis(anyString())).thenThrow(new ResourceAccessException("timeout simulado"));

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, never()).generarArticulo(any());
    verify(registroDescartadas, never()).registrarDescarte(anyString(), anyString());
  }

  @Test
  void buscaComoMaximoUnAnalisisPorCiclo() {
    simularFeeds(List.of(), List.of(
        analisisDeGsmarena("guid-1", "Apple iPhone 18 Pro review"),
        analisisDeGsmarena("guid-2", "Samsung Galaxy S26 FE review")));
    when(clienteBusqueda.estaConfigurado()).thenReturn(true);
    when(clienteBusqueda.buscarAnalisis(anyString())).thenReturn(List.of());

    trabajo.sincronizarNoticiasHardware();

    verify(clienteBusqueda, times(1)).buscarAnalisis(anyString());
    verify(registroDescartadas, never()).registrarDescarte(eq("guid-2"), anyString());
  }

  @Test
  void noReintentaUnResumenDeAnalisisRechazadoParaNoRepetirLaBusqueda() {
    NoticiaHardwareRss analisis = analisisDeGsmarena("guid-1", "Apple iPhone 18 Pro review");
    simularFeeds(List.of(), List.of(analisis));
    when(clienteBusqueda.estaConfigurado()).thenReturn(true);
    when(clienteBusqueda.buscarAnalisis(anyString())).thenReturn(List.of(
        new FragmentoAnalisis("theverge.com", "Review", "https://www.theverge.com/a", "..."),
        new FragmentoAnalisis("engadget.com", "Review", "https://www.engadget.com/b", "..."),
        new FragmentoAnalisis("cnet.com", "Review", "https://www.cnet.com/c", "...")));
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("cuerpo demasiado corto"));

    trabajo.sincronizarNoticiasHardware();

    verify(registroDescartadas).registrarDescarte(eq("guid-1"), anyString());
  }

  @Test
  void completaLasFotosPendientesAunqueNoHayaProveedorDeIa() {
    simularFeeds(List.of(noticia("guid-1", "RTX 5090 review")), List.of());
    when(clasificadorTitulares.clasificarTitulares(any()))
        .thenThrow(new SinProveedorIADisponibleException("todos los proveedores agotados"));

    trabajo.sincronizarNoticiasHardware();

    verify(servicioFotos).completarFotosPendientes();
  }

  @Test
  void descartaSinLlamarALaIaLaNoticiaCuyaFuenteNoTraeTextoSuficiente() {
    NoticiaHardwareRss noticiaSinTexto = new NoticiaHardwareRss(
        "guid-1", "RTX 5090 review", "https://www.fuente.example/guid-1", "Solo una linea.",
        Instant.parse("2027-03-16T10:00:00Z"));
    simularFeeds(List.of(noticiaSinTexto), List.of());

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, never()).generarArticulo(any());
    verify(registroDescartadas).registrarDescarte(eq("guid-1"), anyString());
  }

  @Test
  void reintentaElProximoCicloSiLaPaginaFallaYElExtractoNoBasta() {
    NoticiaHardwareRss noticiaSinExtracto = new NoticiaHardwareRss(
        "guid-1", "Oppo K14 Plus official", "https://www.gsmarena.com/oppo_k14_plus-news-1.php", "",
        Instant.parse("2027-03-16T10:00:00Z"));
    simularFeeds(List.of(), List.of(noticiaSinExtracto));
    when(clienteNoticiaGsmarena.esNoticiaDeGsmarena(noticiaSinExtracto.enlaceOriginal())).thenReturn(true);
    when(clienteNoticiaGsmarena.obtenerMaterial(noticiaSinExtracto.enlaceOriginal()))
        .thenThrow(new ResourceAccessException("timeout simulado"));

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, never()).generarArticulo(any());
    verify(registroDescartadas, never()).registrarDescarte(anyString(), anyString());
  }

  @Test
  void lasNoticiasSinMaterialNoConsumenElTopeDelCiclo() {
    simularFeeds(List.of(
        new NoticiaHardwareRss("guid-1", "Sin texto", "https://www.fuente.example/guid-1", "",
            Instant.parse("2027-03-16T12:00:00Z")),
        noticiaPublicadaEn("guid-2", "RTX 5090 review", "2027-03-16T11:00:00Z"),
        noticiaPublicadaEn("guid-3", "RX 9070 review", "2027-03-16T10:00:00Z")), List.of());
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("no importa"));

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, times(2)).generarArticulo(any());
  }

  @Test
  void soloRedactaLasNoticiasQueApruebaElClasificadorDeTitulares() {
    simularFeeds(List.of(
        noticiaPublicadaEn("guid-1", "GTA 6 vende millones", "2027-03-16T10:00:00Z"),
        noticiaPublicadaEn("guid-2", "RTX 5090 review", "2027-03-15T10:00:00Z")), List.of());
    when(clasificadorTitulares.clasificarTitulares(List.of("GTA 6 vende millones", "RTX 5090 review")))
        .thenReturn(Optional.of(Map.of(1, CategoriaArticulo.HARDWARE_PC)));
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("no importa"));

    trabajo.sincronizarNoticiasHardware();

    ArgumentCaptor<SolicitudGeneracionContenido> capturadorSolicitud =
        ArgumentCaptor.forClass(SolicitudGeneracionContenido.class);
    verify(pipelineGeneracionContenido, times(1)).generarArticulo(capturadorSolicitud.capture());
    assertThat(capturadorSolicitud.getValue().tituloOriginal()).isEqualTo("RTX 5090 review");
    // El rechazo por titular no es definitivo: no se registra como descarte.
    verify(registroDescartadas, never()).registrarDescarte(anyString(), anyString());
  }

  @Test
  void noPublicaSiLaClasificacionFallaPorqueSinSeccionesNoHayRitmoQueRespetar() {
    simularFeeds(List.of(noticia("guid-1", "RTX 5090 review")), List.of());
    when(clasificadorTitulares.clasificarTitulares(any())).thenThrow(new IllegalStateException("respuesta vacia"));

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, never()).generarArticulo(any());
  }

  @Test
  void noLeeLosFeedsNiClasificaSiTodasLasSeccionesPublicaronEnLosUltimosDosDias() {
    when(calendarioSecciones.obtenerSeccionesAbiertas()).thenReturn(EnumSet.noneOf(CategoriaArticulo.class));

    trabajo.sincronizarNoticiasHardware();

    verify(clienteFeed, never()).obtenerNoticiasRecientes(anyString());
    verify(clasificadorTitulares, never()).clasificarTitulares(any());
    // Las fotos pendientes se completan igual.
    verify(servicioFotos).completarFotosPendientes();
  }

  @Test
  void reescribeElArchivoAunqueTodasLasSeccionesEstenCerradasYSuFalloNoFrenaLasFotos() {
    when(calendarioSecciones.obtenerSeccionesAbiertas()).thenReturn(EnumSet.noneOf(CategoriaArticulo.class));
    doThrow(new IllegalStateException("fallo inesperado")).when(servicioReescritura).reescribirArticulosPendientes();

    trabajo.sincronizarNoticiasHardware();

    verify(servicioReescritura).reescribirArticulosPendientes();
    verify(servicioFotos).completarFotosPendientes();
  }

  @Test
  void publicaComoMuchoUnArticuloPorSeccionEnCadaCiclo() {
    simularFeeds(List.of(
        noticiaPublicadaEn("guid-1", "RTX 5090 review", "2027-03-16T12:00:00Z"),
        noticiaPublicadaEn("guid-2", "RX 9070 review", "2027-03-16T11:00:00Z")), List.of());
    when(clasificadorTitulares.clasificarTitulares(any())).thenReturn(Optional.of(Map.of(
        0, CategoriaArticulo.HARDWARE_PC, 1, CategoriaArticulo.HARDWARE_PC)));
    when(pipelineGeneracionContenido.generarArticulo(any())).thenReturn(articuloAprobadoDe(CategoriaArticulo.HARDWARE_PC));
    when(servicioIngestaArticulos.ingestarArticulo(any(), anyString(), any()))
        .thenReturn(new ResultadoIngesta.ArticuloCreado(1L));

    trabajo.sincronizarNoticiasHardware();

    ArgumentCaptor<SolicitudGeneracionContenido> capturadorSolicitud =
        ArgumentCaptor.forClass(SolicitudGeneracionContenido.class);
    verify(pipelineGeneracionContenido, times(1)).generarArticulo(capturadorSolicitud.capture());
    // La mas reciente de la seccion es la que se publica.
    assertThat(capturadorSolicitud.getValue().tituloOriginal()).isEqualTo("RTX 5090 review");
  }

  @Test
  void siUnaRedaccionSeRechazaPruebaConLaSiguienteNoticiaDeLaMismaSeccion() {
    simularFeeds(List.of(
        noticiaPublicadaEn("guid-1", "RTX 5090 review", "2027-03-16T12:00:00Z"),
        noticiaPublicadaEn("guid-2", "RX 9070 review", "2027-03-16T11:00:00Z")), List.of());
    when(clasificadorTitulares.clasificarTitulares(any())).thenReturn(Optional.of(Map.of(
        0, CategoriaArticulo.HARDWARE_PC, 1, CategoriaArticulo.HARDWARE_PC)));
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("cuerpo demasiado corto"))
        .thenReturn(articuloAprobadoDe(CategoriaArticulo.HARDWARE_PC));
    when(servicioIngestaArticulos.ingestarArticulo(any(), anyString(), any()))
        .thenReturn(new ResultadoIngesta.ArticuloCreado(1L));

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, times(2)).generarArticulo(any());
    verify(servicioIngestaArticulos, times(1)).ingestarArticulo(any(), anyString(), any());
  }

  @Test
  void ignoraLasNoticiasDeSeccionesQueYaPublicaronEnLosUltimosDosDias() {
    simularFeeds(List.of(
        noticiaPublicadaEn("guid-1", "RTX 5090 review", "2027-03-16T12:00:00Z"),
        noticiaPublicadaEn("guid-2", "Oppo K14 Plus", "2027-03-16T11:00:00Z")), List.of());
    when(calendarioSecciones.obtenerSeccionesAbiertas()).thenReturn(EnumSet.of(CategoriaArticulo.MOVILES));
    when(clasificadorTitulares.clasificarTitulares(any())).thenReturn(Optional.of(Map.of(
        0, CategoriaArticulo.HARDWARE_PC, 1, CategoriaArticulo.MOVILES)));
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("no importa"));

    trabajo.sincronizarNoticiasHardware();

    ArgumentCaptor<SolicitudGeneracionContenido> capturadorSolicitud =
        ArgumentCaptor.forClass(SolicitudGeneracionContenido.class);
    verify(pipelineGeneracionContenido, times(1)).generarArticulo(capturadorSolicitud.capture());
    assertThat(capturadorSolicitud.getValue().tituloOriginal()).isEqualTo("Oppo K14 Plus");
  }

  @Test
  void redactaConLaCoberturaDeOtrosMediosYLosCitaEnLasFuentes() {
    simularFeeds(List.of(noticia("guid-1", "RTX 5090 review")), List.of());
    when(clienteBusqueda.estaConfigurado()).thenReturn(true);
    when(clienteBusqueda.buscarCobertura("RTX 5090 review", "fuente.example")).thenReturn(List.of(
        new FragmentoAnalisis("tomshardware.com", "RTX 5090", "https://www.tomshardware.com/rtx-5090", "Datos"),
        new FragmentoAnalisis("pcgamer.com", "RTX 5090", "https://www.pcgamer.com/rtx-5090", "Mas datos")));
    when(pipelineGeneracionContenido.generarArticulo(any())).thenReturn(articuloAprobadoDe(CategoriaArticulo.HARDWARE_PC));
    when(servicioIngestaArticulos.ingestarArticulo(any(), anyString(), any()))
        .thenReturn(new ResultadoIngesta.ArticuloCreado(1L));

    trabajo.sincronizarNoticiasHardware();

    ArgumentCaptor<SolicitudGeneracionContenido> capturadorSolicitud =
        ArgumentCaptor.forClass(SolicitudGeneracionContenido.class);
    verify(pipelineGeneracionContenido).generarArticulo(capturadorSolicitud.capture());
    assertThat(capturadorSolicitud.getValue().coberturaOtrosMedios())
        .contains("Medio: tomshardware.com").contains("Medio: pcgamer.com");
    ArgumentCaptor<String> capturadorCuerpo = ArgumentCaptor.forClass(String.class);
    verify(servicioIngestaArticulos).ingestarArticulo(any(), capturadorCuerpo.capture(), any());
    assertThat(capturadorCuerpo.getValue())
        .contains("<p>Fuentes: ")
        .contains(">fuente.example</a>")
        .contains(">tomshardware.com</a>")
        .contains(">pcgamer.com</a>");
  }

  @Test
  void redactaSoloConLaFuenteSiLaBusquedaDeCoberturaFalla() {
    simularFeeds(List.of(noticia("guid-1", "RTX 5090 review")), List.of());
    when(clienteBusqueda.estaConfigurado()).thenReturn(true);
    when(clienteBusqueda.buscarCobertura(anyString(), anyString())).thenThrow(new ResourceAccessException("timeout"));
    when(pipelineGeneracionContenido.generarArticulo(any()))
        .thenReturn(new ResultadoGeneracionContenido.ContenidoRechazado("no importa"));

    trabajo.sincronizarNoticiasHardware();

    ArgumentCaptor<SolicitudGeneracionContenido> capturadorSolicitud =
        ArgumentCaptor.forClass(SolicitudGeneracionContenido.class);
    verify(pipelineGeneracionContenido).generarArticulo(capturadorSolicitud.capture());
    assertThat(capturadorSolicitud.getValue().coberturaOtrosMedios()).isNull();
  }

  private static ResultadoGeneracionContenido.ContenidoAprobado articuloAprobadoDe(CategoriaArticulo seccion) {
    return new ResultadoGeneracionContenido.ContenidoAprobado(
        "Titular", "Entradilla", "<article></article>", seccion, null, List.of(), List.of());
  }

  @Test
  void cortaElCicloSiNoHayProveedorNiParaClasificar() {
    simularFeeds(List.of(noticia("guid-1", "RTX 5090 review")), List.of());
    when(clasificadorTitulares.clasificarTitulares(any()))
        .thenThrow(new SinProveedorIADisponibleException("todos agotados"));

    trabajo.sincronizarNoticiasHardware();

    verify(pipelineGeneracionContenido, never()).generarArticulo(any());
  }
}
