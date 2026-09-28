package com.vexelbyte.automatizacion.imagenes;

import com.vexelbyte.articulos.Articulo;
import com.vexelbyte.articulos.ArticuloRepository;
import com.vexelbyte.articulos.CategoriaArticulo;
import com.vexelbyte.articulos.FotoArticuloAlmacenada;
import com.vexelbyte.articulos.FotoArticuloAlmacenadaRepository;
import com.vexelbyte.automatizacion.ia.ClasificadorImagenesIA;
import com.vexelbyte.automatizacion.ia.ClasificadorImagenesIA.RevisionImagen;
import com.vexelbyte.automatizacion.ia.ImagenParaIA;
import com.vexelbyte.automatizacion.ia.SinProveedorIADisponibleException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Todo articulo lleva foto (decision del Tech Lead: una portada sin imagen
// afea la web). Primero la imagen de prensa del fabricante que trae la noticia
// de origen; si no es material oficial, una foto ilustrativa de Pexels que el
// pie presenta como tal. La foto se guarda aqui para que el build de la web no
// dependa de ningun tercero.
@Service
public class ServicioFotosArticulos {

  private static final Logger REGISTRO = LoggerFactory.getLogger(ServicioFotosArticulos.class);

  // Cada revision gasta una llamada de vision de la cuota de Gemini que
  // tambien redacta. Con el calendario de dias alternos entran como mucho
  // cuatro articulos por ciclo, asi que el tope solo pesa al poner al dia un
  // archivo atrasado: con 8 el archivo de produccion (21 sin foto tras V10)
  // tardaba tres ciclos, y con 25 se completa en uno.
  private static final int MAXIMO_REVISIONES_POR_CICLO = 25;

  // Sin agente de vision (cuota agotada o modelos de Gemini retirados), una
  // noticia espera a que vuelva para validar la foto oficial; pasado este
  // plazo lleva ya un ciclo con cuota nueva sin exito y recibe una de Pexels,
  // que no necesita IA. Es menor que el dia que la API la tiene oculta.
  private static final Duration ESPERA_MAXIMA_VISION = Duration.ofHours(12);

  // Por debajo se ve pixelada en la cabecera del articulo.
  private static final int ANCHO_MINIMO_FOTO = 800;

  private static final String LICENCIA_MATERIAL_PRENSA = "Material de prensa";
  private static final String LICENCIA_PEXELS = "Licencia de Pexels";
  private static final String URL_LICENCIA_PEXELS = "https://www.pexels.com/license/";

  // Busqueda de respaldo cuando la IA no propone ninguna o no llega a ver una
  // imagen (la noticia no traia ninguna): el tema general de la categoria.
  private static final String CONSULTA_GENERICA = "computer technology";
  private static final Map<CategoriaArticulo, String> CONSULTAS_POR_CATEGORIA = Map.of(
      CategoriaArticulo.HARDWARE_PC, "computer hardware",
      CategoriaArticulo.MOVILES, "smartphone",
      CategoriaArticulo.PERIFERICOS, "computer keyboard",
      CategoriaArticulo.RENDIMIENTO, "computer processor");

  private final ArticuloRepository repositorioArticulos;
  private final FotoArticuloAlmacenadaRepository repositorioFotos;
  private final ClienteImagenFuente clienteImagenFuente;
  private final ClasificadorImagenesIA clasificadorImagenes;
  private final ClientePexels clientePexels;
  private final CompresorImagenes compresorImagenes;
  private final Clock reloj;

  public ServicioFotosArticulos(
      ArticuloRepository repositorioArticulos, FotoArticuloAlmacenadaRepository repositorioFotos,
      ClienteImagenFuente clienteImagenFuente, ClasificadorImagenesIA clasificadorImagenes,
      ClientePexels clientePexels, CompresorImagenes compresorImagenes, Clock reloj) {
    this.repositorioArticulos = repositorioArticulos;
    this.repositorioFotos = repositorioFotos;
    this.clienteImagenFuente = clienteImagenFuente;
    this.clasificadorImagenes = clasificadorImagenes;
    this.clientePexels = clientePexels;
    this.compresorImagenes = compresorImagenes;
    this.reloj = reloj;
  }

  @Transactional
  public void completarFotosPendientes() {
    List<Articulo> articulosPendientes = repositorioArticulos
        .findTop50ByBorradorFalseAndFotoRevisadaFalseOrderByFechaPublicacionDesc().stream()
        .limit(MAXIMO_REVISIONES_POR_CICLO)
        .toList();
    int fotosGuardadas = 0;
    boolean hayProveedorDeVision = true;
    for (Articulo articulo : articulosPendientes) {
      try {
        if (hayProveedorDeVision) {
          try {
            fotosGuardadas += revisarArticulo(articulo) ? 1 : 0;
            articulo.setFotoRevisada(true);
            continue;
          } catch (SinProveedorIADisponibleException sinProveedor) {
            REGISTRO.warn("Sin proveedor de IA para revisar imagenes; solo pongo foto de Pexels a las noticias "
                + "que llevan mas de {} h esperando", ESPERA_MAXIMA_VISION.toHours());
            hayProveedorDeVision = false;
          }
        }
        if (haAgotadoLaEsperaDeVision(articulo)) {
          fotosGuardadas += ponerFotoIlustrativa(articulo, Optional.empty()) ? 1 : 0;
          articulo.setFotoRevisada(true);
        }
      } catch (RuntimeException falloFuente) {
        // Sin marcarlo como revisado: se reintenta en el siguiente ciclo.
        REGISTRO.warn("No pude obtener la imagen de '{}': {}", articulo.getTitulo(), falloFuente.getMessage());
      }
    }
    if (!articulosPendientes.isEmpty()) {
      REGISTRO.info("Fotos: {} guardadas de {} articulos revisados", fotosGuardadas, articulosPendientes.size());
    }
  }

  private boolean haAgotadoLaEsperaDeVision(Articulo articulo) {
    return articulo.getFechaCreacion().isBefore(OffsetDateTime.now(reloj).minus(ESPERA_MAXIMA_VISION));
  }

  private boolean revisarArticulo(Articulo articulo) {
    Optional<String> consultaSugerida = Optional.empty();
    Optional<String> urlImagenFuente = articulo.getEnlaceFuente() == null
        ? Optional.empty()
        : clienteImagenFuente.obtenerUrlImagenPrincipal(articulo.getEnlaceFuente());
    if (urlImagenFuente.isPresent()) {
      ImagenDescargada imagen = clienteImagenFuente.descargarImagen(urlImagenFuente.get());
      if (imagen.ancho() >= ANCHO_MINIMO_FOTO) {
        RevisionImagen revision = clasificadorImagenes.revisarImagen(
            new ImagenParaIA(imagen.contenido(), imagen.tipoContenido()), articulo.getTitulo(), articulo.getProducto());
        if (revision.marca().isPresent()) {
          guardarFoto(articulo, urlImagenFuente.get(), imagen, new CreditoFoto(
              revision.marca().get(), LICENCIA_MATERIAL_PRENSA, null, articulo.getEnlaceFuente(), false));
          return true;
        }
        consultaSugerida = revision.consultaIlustrativa();
      }
    }
    return ponerFotoIlustrativa(articulo, consultaSugerida);
  }

  private boolean ponerFotoIlustrativa(Articulo articulo, Optional<String> consultaSugerida) {
    if (!clientePexels.estaConfigurado()) {
      return false;
    }
    String consultaCategoria = CONSULTAS_POR_CATEGORIA.getOrDefault(articulo.getCategoria(), CONSULTA_GENERICA);
    Optional<FotoIlustrativa> foto = clientePexels.buscarFoto(
        consultaSugerida.orElse(consultaCategoria), repositorioArticulos::existsByFotoUrl);
    // Una consulta muy concreta puede no dar nada nuevo: la de la categoria siempre tiene fotos.
    if (foto.isEmpty() && consultaSugerida.isPresent()) {
      foto = clientePexels.buscarFoto(consultaCategoria, repositorioArticulos::existsByFotoUrl);
    }
    if (foto.isEmpty()) {
      return false;
    }
    FotoIlustrativa fotoIlustrativa = foto.get();
    guardarFoto(articulo, fotoIlustrativa.urlImagen(), clienteImagenFuente.descargarImagen(fotoIlustrativa.urlImagen()),
        new CreditoFoto(fotoIlustrativa.fotografo(), LICENCIA_PEXELS, URL_LICENCIA_PEXELS,
            fotoIlustrativa.urlPagina(), true));
    return true;
  }

  private void guardarFoto(Articulo articulo, String urlImagen, ImagenDescargada imagenOriginal, CreditoFoto credito) {
    ImagenDescargada imagen = compresorImagenes.comprimir(imagenOriginal);
    FotoArticuloAlmacenada fotoAlmacenada = new FotoArticuloAlmacenada();
    fotoAlmacenada.setArticuloId(articulo.getId());
    fotoAlmacenada.setContenido(imagen.contenido());
    fotoAlmacenada.setTipoContenido(imagen.tipoContenido());
    repositorioFotos.save(fotoAlmacenada);

    articulo.setFotoUrl(urlImagen);
    articulo.setFotoAncho(imagen.ancho());
    articulo.setFotoAlto(imagen.alto());
    articulo.setFotoAutor(credito.autor());
    articulo.setFotoLicencia(credito.licencia());
    articulo.setFotoUrlLicencia(credito.urlLicencia());
    articulo.setFotoUrlOrigen(credito.urlOrigen());
    articulo.setFotoEsIlustrativa(credito.esIlustrativa());
  }

  private record CreditoFoto(
      String autor, String licencia, String urlLicencia, String urlOrigen, boolean esIlustrativa) {
  }
}
