package com.vexelbyte.automatizacion.imagenes;

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
import com.vexelbyte.articulos.FotoArticuloAlmacenada;
import com.vexelbyte.articulos.FotoArticuloAlmacenadaRepository;
import com.vexelbyte.automatizacion.ia.ClasificadorImagenesIA;
import com.vexelbyte.automatizacion.ia.ClasificadorImagenesIA.RevisionImagen;
import com.vexelbyte.automatizacion.ia.ImagenParaIA;
import com.vexelbyte.automatizacion.ia.SinProveedorIADisponibleException;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

class ServicioFotosArticulosTest {

  private static final String URL_IMAGEN_FUENTE = "https://fdn.gsmarena.com/imgroot/news/oppo/gsmarena_001.jpg";
  private static final String URL_IMAGEN_PEXELS = "https://images.pexels.com/photos/1/foto.jpeg?w=1200";
  private static final ImagenDescargada IMAGEN_GRANDE =
      new ImagenDescargada(new byte[] {1, 2, 3}, "image/jpeg", 2133, 1200);
  private static final FotoIlustrativa FOTO_PEXELS =
      new FotoIlustrativa(URL_IMAGEN_PEXELS, "Ana Fotógrafa", "https://www.pexels.com/photo/1/");

  private final ArticuloRepository repositorioArticulos = mock(ArticuloRepository.class);
  private final FotoArticuloAlmacenadaRepository repositorioFotos = mock(FotoArticuloAlmacenadaRepository.class);
  private final ClienteImagenFuente clienteImagenFuente = mock(ClienteImagenFuente.class);
  private final ClasificadorImagenesIA clasificadorImagenes = mock(ClasificadorImagenesIA.class);
  private final ClientePexels clientePexels = mock(ClientePexels.class);
  private final CompresorImagenes compresorImagenes = mock(CompresorImagenes.class);
  private final ServicioFotosArticulos servicioFotos = new ServicioFotosArticulos(
      repositorioArticulos, repositorioFotos, clienteImagenFuente, clasificadorImagenes, clientePexels,
      compresorImagenes);

  @BeforeEach
  void configurarPexelsYCompresor() {
    // El compresor tiene su propio test: aqui devuelve la imagen tal cual.
    when(compresorImagenes.comprimir(any(ImagenDescargada.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    when(clientePexels.estaConfigurado()).thenReturn(true);
    when(clienteImagenFuente.descargarImagen(URL_IMAGEN_PEXELS))
        .thenReturn(new ImagenDescargada(new byte[] {9}, "image/jpeg", 1200, 627));
  }

  private static Articulo articuloDe(long identificador) {
    Articulo articulo = new Articulo();
    articulo.setId(identificador);
    articulo.setTitulo("Oppo muestra el K14 Plus");
    articulo.setProducto("Oppo K14 Plus");
    articulo.setCategoria(CategoriaArticulo.MOVILES);
    articulo.setEnlaceFuente("https://www.gsmarena.com/noticia-" + identificador + ".php");
    return articulo;
  }

  private void simularPendientes(List<Articulo> articulos) {
    when(repositorioArticulos.findTop50ByBorradorFalseAndFotoRevisadaFalseOrderByFechaPublicacionDesc())
        .thenReturn(articulos);
  }

  private void simularImagenDeLaFuente(ImagenDescargada imagen) {
    when(clienteImagenFuente.obtenerUrlImagenPrincipal(anyString())).thenReturn(Optional.of(URL_IMAGEN_FUENTE));
    when(clienteImagenFuente.descargarImagen(URL_IMAGEN_FUENTE)).thenReturn(imagen);
  }

  private void simularRevision(Optional<String> marca, Optional<String> consulta) {
    when(clasificadorImagenes.revisarImagen(any(ImagenParaIA.class), anyString(), anyString()))
        .thenReturn(new RevisionImagen(marca, consulta));
  }

  @Test
  void guardaLaImagenDePrensaAtribuidaALaMarcaSinBuscarEnPexels() {
    Articulo articulo = articuloDe(7L);
    simularPendientes(List.of(articulo));
    simularImagenDeLaFuente(IMAGEN_GRANDE);
    simularRevision(Optional.of("Oppo"), Optional.of("smartphone"));

    servicioFotos.completarFotosPendientes();

    ArgumentCaptor<FotoArticuloAlmacenada> capturadorFoto = ArgumentCaptor.forClass(FotoArticuloAlmacenada.class);
    verify(repositorioFotos).save(capturadorFoto.capture());
    assertThat(capturadorFoto.getValue().getArticuloId()).isEqualTo(7L);
    assertThat(articulo.getFotoAutor()).isEqualTo("Oppo");
    assertThat(articulo.getFotoLicencia()).isEqualTo("Material de prensa");
    assertThat(articulo.getFotoUrlOrigen()).isEqualTo(articulo.getEnlaceFuente());
    assertThat(articulo.isFotoEsIlustrativa()).isFalse();
    assertThat(articulo.isFotoRevisada()).isTrue();
    verify(clientePexels, never()).buscarFoto(anyString(), any());
  }

  @Test
  void poneUnaFotoIlustrativaConLaBusquedaQuePropuesoLaIaSiLaImagenNoEsOficial() {
    Articulo articulo = articuloDe(1L);
    simularPendientes(List.of(articulo));
    simularImagenDeLaFuente(IMAGEN_GRANDE);
    simularRevision(Optional.empty(), Optional.of("graphics card"));
    when(clientePexels.buscarFoto(eq("graphics card"), any())).thenReturn(Optional.of(FOTO_PEXELS));

    servicioFotos.completarFotosPendientes();

    assertThat(articulo.getFotoUrl()).isEqualTo(URL_IMAGEN_PEXELS);
    assertThat(articulo.getFotoAutor()).isEqualTo("Ana Fotógrafa");
    assertThat(articulo.getFotoLicencia()).isEqualTo("Licencia de Pexels");
    assertThat(articulo.getFotoUrlOrigen()).isEqualTo("https://www.pexels.com/photo/1/");
    assertThat(articulo.isFotoEsIlustrativa()).isTrue();
    assertThat(articulo.getFotoAncho()).isEqualTo(1200);
  }

  @Test
  void buscaPorLaCategoriaCuandoLaNoticiaNoTraeImagenOEsDemasiadoPequena() {
    Articulo sinImagen = articuloDe(1L);
    Articulo imagenPequena = articuloDe(2L);
    simularPendientes(List.of(sinImagen, imagenPequena));
    when(clienteImagenFuente.obtenerUrlImagenPrincipal(sinImagen.getEnlaceFuente())).thenReturn(Optional.empty());
    when(clienteImagenFuente.obtenerUrlImagenPrincipal(imagenPequena.getEnlaceFuente()))
        .thenReturn(Optional.of(URL_IMAGEN_FUENTE));
    when(clienteImagenFuente.descargarImagen(URL_IMAGEN_FUENTE))
        .thenReturn(new ImagenDescargada(new byte[] {1}, "image/jpeg", 400, 300));
    when(clientePexels.buscarFoto(eq("smartphone"), any())).thenReturn(Optional.of(FOTO_PEXELS));

    servicioFotos.completarFotosPendientes();

    verify(clientePexels, times(2)).buscarFoto(eq("smartphone"), any());
    // Ninguna de las dos gasta una llamada de vision.
    verify(clasificadorImagenes, never()).revisarImagen(any(ImagenParaIA.class), anyString(), anyString());
    assertThat(sinImagen.isFotoEsIlustrativa()).isTrue();
    assertThat(imagenPequena.isFotoEsIlustrativa()).isTrue();
  }

  @Test
  void recurreALaCategoriaSiLaBusquedaDeLaIaNoDaNada() {
    Articulo articulo = articuloDe(1L);
    simularPendientes(List.of(articulo));
    simularImagenDeLaFuente(IMAGEN_GRANDE);
    simularRevision(Optional.empty(), Optional.of("teleconverter lens"));
    when(clientePexels.buscarFoto(eq("teleconverter lens"), any())).thenReturn(Optional.empty());
    when(clientePexels.buscarFoto(eq("smartphone"), any())).thenReturn(Optional.of(FOTO_PEXELS));

    servicioFotos.completarFotosPendientes();

    assertThat(articulo.getFotoUrl()).isEqualTo(URL_IMAGEN_PEXELS);
  }

  @Test
  void seQuedaSinFotoPeroRevisadoSiPexelsNoEstaConfigurado() {
    Articulo articulo = articuloDe(1L);
    simularPendientes(List.of(articulo));
    when(clientePexels.estaConfigurado()).thenReturn(false);
    when(clienteImagenFuente.obtenerUrlImagenPrincipal(anyString())).thenReturn(Optional.empty());

    servicioFotos.completarFotosPendientes();

    assertThat(articulo.getFotoUrl()).isNull();
    assertThat(articulo.isFotoRevisada()).isTrue();
    verify(repositorioFotos, never()).save(any());
  }

  @Test
  void dejaPendienteElArticuloCuyaFuenteFallaYSigueConElResto() {
    Articulo conFallo = articuloDe(1L);
    Articulo siguiente = articuloDe(2L);
    simularPendientes(List.of(conFallo, siguiente));
    when(clienteImagenFuente.obtenerUrlImagenPrincipal(conFallo.getEnlaceFuente()))
        .thenThrow(new ResourceAccessException("timeout"));
    when(clienteImagenFuente.obtenerUrlImagenPrincipal(siguiente.getEnlaceFuente())).thenReturn(Optional.empty());
    when(clientePexels.buscarFoto(anyString(), any())).thenReturn(Optional.of(FOTO_PEXELS));

    servicioFotos.completarFotosPendientes();

    assertThat(conFallo.isFotoRevisada()).isFalse();
    assertThat(siguiente.isFotoRevisada()).isTrue();
  }

  @Test
  void cortaSinMarcarNadaCuandoNoQuedaCuotaDeVision() {
    Articulo primero = articuloDe(1L);
    Articulo segundo = articuloDe(2L);
    simularPendientes(List.of(primero, segundo));
    simularImagenDeLaFuente(IMAGEN_GRANDE);
    when(clasificadorImagenes.revisarImagen(any(ImagenParaIA.class), anyString(), anyString()))
        .thenThrow(new SinProveedorIADisponibleException("sin cuota"));

    servicioFotos.completarFotosPendientes();

    assertThat(primero.isFotoRevisada()).isFalse();
    assertThat(segundo.isFotoRevisada()).isFalse();
    verify(clientePexels, never()).buscarFoto(anyString(), any());
  }

  @Test
  void revisaComoMucho25ArticulosPorCicloParaNoAgotarLaCuotaDeRedaccion() {
    List<Articulo> pendientes = IntStream.rangeClosed(1, 30).mapToObj(identificador -> articuloDe(identificador)).toList();
    simularPendientes(pendientes);
    when(clienteImagenFuente.obtenerUrlImagenPrincipal(anyString())).thenReturn(Optional.empty());
    when(clientePexels.buscarFoto(anyString(), any())).thenReturn(Optional.empty());

    servicioFotos.completarFotosPendientes();

    verify(clienteImagenFuente, times(25)).obtenerUrlImagenPrincipal(anyString());
    assertThat(pendientes.get(29).isFotoRevisada()).isFalse();
  }
}
