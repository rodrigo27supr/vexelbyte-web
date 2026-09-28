package com.vexelbyte.automatizacion.persistencia;

import com.vexelbyte.articulos.ArticuloRepository;
import com.vexelbyte.articulos.FotoArticuloAlmacenada;
import com.vexelbyte.articulos.FotoArticuloAlmacenadaRepository;
import com.vexelbyte.automatizacion.imagenes.CompresorImagenes;
import com.vexelbyte.automatizacion.imagenes.ImagenDescargada;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Decision del Tech Lead (2026-09-27) para que la base de datos gratuita no se
// llene nunca: la foto sale al ano y medio (es lo que pesa) y el articulo
// entero a los diez anos. Asi entra lo mismo que sale y la base se estabiliza
// en torno a un tercio de los 0,5 GB del plan gratuito.
@Service
public class ServicioRetencionContenido {

  private static final Logger REGISTRO = LoggerFactory.getLogger(ServicioRetencionContenido.class);

  private static final Period ANTIGUEDAD_MAXIMA_FOTO = Period.ofMonths(18);
  private static final Period ANTIGUEDAD_MAXIMA_ARTICULO = Period.ofYears(10);
  // Los feeds solo traen noticias de los ultimos dias: pasado un mes un descarte
  // ya no evita ninguna llamada a la IA y solo ocupa sitio.
  private static final Period ANTIGUEDAD_MAXIMA_DESCARTE = Period.ofDays(30);

  // Una foto comprimida ronda los 60-140 KB: por encima de esto no lo esta.
  private static final int PESO_MAXIMO_FOTO_BYTES = 200_000;
  private static final int FOTOS_RECOMPRIMIDAS_POR_PASADA = 10;

  private final ArticuloRepository repositorioArticulos;
  private final FotoArticuloAlmacenadaRepository repositorioFotos;
  private final NoticiaDescartadaRepository repositorioDescartadas;
  private final CompresorImagenes compresorImagenes;
  private final Clock reloj;

  public ServicioRetencionContenido(
      ArticuloRepository repositorioArticulos, FotoArticuloAlmacenadaRepository repositorioFotos,
      NoticiaDescartadaRepository repositorioDescartadas, CompresorImagenes compresorImagenes, Clock reloj) {
    this.repositorioArticulos = repositorioArticulos;
    this.repositorioFotos = repositorioFotos;
    this.repositorioDescartadas = repositorioDescartadas;
    this.compresorImagenes = compresorImagenes;
    this.reloj = reloj;
  }

  // El backend duerme cuando no hay trafico: esto corre en cada arranque y
  // luego cada 12 horas mientras siga despierto. Borrar es barato e idempotente.
  @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT12H")
  @Transactional
  public void aplicarRetencion() {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    OffsetDateTime limiteFotos = ahora.minus(ANTIGUEDAD_MAXIMA_FOTO);
    int fotosBorradas = repositorioFotos.borrarFotosDeArticulosPublicadosAntesDe(limiteFotos);
    repositorioArticulos.quitarFotoDeArticulosPublicadosAntesDe(limiteFotos);
    // Sus fotos ya se borraron al ano y medio, asi que el articulo sale limpio.
    int articulosBorrados = repositorioArticulos.borrarArticulosPublicadosAntesDe(ahora.minus(ANTIGUEDAD_MAXIMA_ARTICULO));
    int descartesBorrados = repositorioDescartadas.borrarDescartadasAntesDe(ahora.minus(ANTIGUEDAD_MAXIMA_DESCARTE));
    int fotosRecomprimidas = recomprimirFotosPesadas();
    if (fotosBorradas + articulosBorrados + descartesBorrados + fotosRecomprimidas > 0) {
      REGISTRO.info("Retencion: {} fotos, {} articulos y {} descartes caducados borrados; {} fotos recomprimidas",
          fotosBorradas, articulosBorrados, descartesBorrados, fotosRecomprimidas);
    }
  }

  // Las fotos guardadas antes de comprimirlas al guardar (y cualquiera que se
  // escape) se recomprimen aqui, pocas por pasada para no disparar la memoria.
  private int recomprimirFotosPesadas() {
    int fotosRecomprimidas = 0;
    for (Long articuloId : repositorioFotos.buscarFotosMasPesadasQue(PESO_MAXIMO_FOTO_BYTES, FOTOS_RECOMPRIMIDAS_POR_PASADA)) {
      FotoArticuloAlmacenada foto = repositorioFotos.findById(articuloId).orElseThrow();
      ImagenDescargada comprimida = compresorImagenes.comprimir(new ImagenDescargada(
          foto.getContenido(), foto.getTipoContenido(), 0, 0));
      if (comprimida.contenido().length >= foto.getContenido().length) {
        continue;
      }
      foto.setContenido(comprimida.contenido());
      foto.setTipoContenido(comprimida.tipoContenido());
      repositorioArticulos.findById(articuloId).ifPresent(articulo -> {
        articulo.setFotoAncho(comprimida.ancho());
        articulo.setFotoAlto(comprimida.alto());
      });
      fotosRecomprimidas++;
    }
    return fotosRecomprimidas;
  }
}
