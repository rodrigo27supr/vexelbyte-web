package com.vexelbyte.automatizacion.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vexelbyte.articulos.Articulo;
import com.vexelbyte.articulos.ArticuloRepository;
import com.vexelbyte.articulos.CategoriaArticulo;
import com.vexelbyte.articulos.EspecificacionTecnica;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ServicioIngestaArticulosTest {

  private static final Clock RELOJ_FIJO = Clock.fixed(Instant.parse("2027-03-16T08:00:00Z"), ZoneOffset.UTC);

  private final ArticuloRepository repositorioArticulosSimulado = mock(ArticuloRepository.class);
  private final ServicioIngestaArticulos servicioIngesta = new ServicioIngestaArticulos(
      repositorioArticulosSimulado, new ObjectMapper(), RELOJ_FIJO);

  private static final DatosEditorialesArticulo DATOS_EDITORIALES_BASE = new DatosEditorialesArticulo(
      CategoriaArticulo.MOVILES, "Oppo K14 Plus",
      List.of(new EspecificacionTecnica("Batería", "8000 mAh")), List.of("Batería enorme"));

  private static final DatosFuenteArticulo DATOS_FUENTE_BASE = new DatosFuenteArticulo(
      "gid-123456",
      "https://www.fuente.example/parche-7-36",
      "Parche 7.36",
      "Novedades de Half-Life 3: Parche 7.36",
      OffsetDateTime.parse("2027-03-15T10:00:00Z"));

  @Test
  void creaElArticuloYaPublicadoCuandoElIdExternoFuenteNoExisteTodavia() {
    when(repositorioArticulosSimulado.existsByIdExternoFuente("gid-123456")).thenReturn(false);
    when(repositorioArticulosSimulado.save(any(Articulo.class))).thenAnswer(invocacion -> {
      Articulo articuloGuardado = invocacion.getArgument(0);
      articuloGuardado.setId(1L);
      return articuloGuardado;
    });

    ResultadoIngesta resultado = servicioIngesta.ingestarArticulo(DATOS_FUENTE_BASE, "<article><p>Texto</p></article>", DATOS_EDITORIALES_BASE);

    assertThat(resultado).isInstanceOf(ResultadoIngesta.ArticuloCreado.class);
    assertThat(((ResultadoIngesta.ArticuloCreado) resultado).idArticulo()).isEqualTo(1L);

    ArgumentCaptor<Articulo> capturadorArticulo = ArgumentCaptor.forClass(Articulo.class);
    verify(repositorioArticulosSimulado).save(capturadorArticulo.capture());
    Articulo articuloGuardado = capturadorArticulo.getValue();

    assertThat(articuloGuardado.isBorrador())
        .as("la ingesta publica directamente: el contenido llega filtrado y sanitizado")
        .isFalse();
    assertThat(articuloGuardado.getIdExternoFuente()).isEqualTo("gid-123456");
    // La fecha de alta es la nuestra, no la de la fuente: marca el ritmo por seccion.
    assertThat(articuloGuardado.getFechaCreacion()).isEqualTo(OffsetDateTime.parse("2027-03-16T08:00:00Z"));
    assertThat(articuloGuardado.getEnlaceFuente()).isEqualTo("https://www.fuente.example/parche-7-36");
    assertThat(articuloGuardado.getCuerpoHtml()).isEqualTo("<article><p>Texto</p></article>");
    assertThat(articuloGuardado.getCategoria()).isEqualTo(CategoriaArticulo.MOVILES);
    assertThat(articuloGuardado.getProducto()).isEqualTo("Oppo K14 Plus");
    assertThat(articuloGuardado.getEspecificacionesJson()).isEqualTo("[{\"nombre\":\"Batería\",\"valor\":\"8000 mAh\"}]");
    assertThat(articuloGuardado.getPuntosClaveJson()).isEqualTo("[\"Batería enorme\"]");
    // Sufijo = primeros 12 hex del SHA-256 de "gid-123456".
    assertThat(articuloGuardado.getSlug()).isEqualTo("parche-7-36-e250498b4f9d");
  }

  @Test
  void generaUnSlugSinBarrasCuandoElIdExternoFuenteEsUnaUrl() {
    // El guid de GSMArena es la URL completa de la noticia.
    DatosFuenteArticulo datosConGuidUrl = new DatosFuenteArticulo(
        "https://www.gsmarena.com/oppo_k14_plus-news-74784.php", "https://www.gsmarena.com/oppo_k14_plus-news-74784.php",
        "Oppo K14 Plus",
        "Descripcion", DATOS_FUENTE_BASE.fechaPublicacion());
    when(repositorioArticulosSimulado.save(any(Articulo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

    servicioIngesta.ingestarArticulo(datosConGuidUrl, "<article></article>", DATOS_EDITORIALES_BASE);

    ArgumentCaptor<Articulo> capturadorArticulo = ArgumentCaptor.forClass(Articulo.class);
    verify(repositorioArticulosSimulado).save(capturadorArticulo.capture());
    assertThat(capturadorArticulo.getValue().getSlug()).matches("oppo-k14-plus-[0-9a-f]{12}");
  }

  @Test
  void esIdempotenteAnteElMismoIdExternoFuenteProcesadoDosVeces() {
    // Simulo exactamente el escenario que pide la Fase 2.3: el job procesa la
    // misma noticia dos veces (por ejemplo, tras un reinicio a mitad de
    // ciclo). La primera vez el articulo no existe; despues de "guardarlo",
    // la segunda comprobacion debe encontrarlo.
    when(repositorioArticulosSimulado.existsByIdExternoFuente("gid-123456")).thenReturn(false, true);
    when(repositorioArticulosSimulado.save(any(Articulo.class))).thenAnswer(invocacion -> {
      Articulo articuloGuardado = invocacion.getArgument(0);
      articuloGuardado.setId(1L);
      return articuloGuardado;
    });

    ResultadoIngesta primerResultado = servicioIngesta.ingestarArticulo(DATOS_FUENTE_BASE, "<article></article>", DATOS_EDITORIALES_BASE);
    ResultadoIngesta segundoResultado = servicioIngesta.ingestarArticulo(DATOS_FUENTE_BASE, "<article></article>", DATOS_EDITORIALES_BASE);

    assertThat(primerResultado).isInstanceOf(ResultadoIngesta.ArticuloCreado.class);
    assertThat(segundoResultado).isInstanceOf(ResultadoIngesta.ArticuloYaExistente.class);
    assertThat(((ResultadoIngesta.ArticuloYaExistente) segundoResultado).idExternoFuente()).isEqualTo("gid-123456");

    // La comprobacion real de idempotencia: sin importar cuantas veces se
    // llame con el mismo idExternoFuente, el INSERT solo ocurre una vez.
    verify(repositorioArticulosSimulado, times(1)).save(any(Articulo.class));
  }

  @Test
  void noIntentaGuardarCuandoElArticuloYaExiste() {
    when(repositorioArticulosSimulado.existsByIdExternoFuente("gid-123456")).thenReturn(true);

    servicioIngesta.ingestarArticulo(DATOS_FUENTE_BASE, "<article></article>", DATOS_EDITORIALES_BASE);

    verify(repositorioArticulosSimulado, never()).save(any(Articulo.class));
  }
}
