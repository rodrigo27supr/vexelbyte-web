package com.vexelbyte.articulos;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// Cada reconstruccion de la web pide la misma foto una vez por pagina donde
// sale (unas 700 peticiones para 64 fotos) y cada lectura salia de Neon, cuyo
// plan gratuito corta la base al llegar a 5 GB de transferencia al mes (se
// agoto el 15-10-2026). Aqui se guardan las fotos ya servidas: Neon solo
// responde la primera vez.
class CacheFotosArticulos {

  // Con el heap de 256 MB del plan gratuito de Render, 40 MB son unas 330 fotos
  // comprimidas: caben las 300 que conserva la retencion. Pasado ese limite sale
  // la menos usada.
  private static final long PESO_MAXIMO_BYTES = 40L * 1024 * 1024;

  // La foto solo cambia si la retencion la recomprime: un dia de desfase da igual.
  // Es largo a proposito: la API es publica y quien la pida en bucle solo puede
  // obligar a Neon a releer cada foto una vez al dia, no cada hora.
  private static final Duration VIGENCIA = Duration.ofHours(24);

  private final Clock reloj;
  // Orden de acceso para expulsar la foto menos usada.
  private final Map<String, FotoEnCache> fotosPorSlug = new LinkedHashMap<>(16, 0.75f, true);
  private long pesoActualBytes;

  CacheFotosArticulos(Clock reloj) {
    this.reloj = reloj;
  }

  synchronized void vaciar() {
    fotosPorSlug.clear();
    pesoActualBytes = 0;
  }

  synchronized Optional<FotoServida> obtener(String slug) {
    FotoEnCache fotoEnCache = fotosPorSlug.get(slug);
    if (fotoEnCache == null) {
      return Optional.empty();
    }
    if (fotoEnCache.caducaEn().isBefore(reloj.instant())) {
      pesoActualBytes -= fotosPorSlug.remove(slug).foto().contenido().length;
      return Optional.empty();
    }
    return Optional.of(fotoEnCache.foto());
  }

  // Solo se guarda lo que la base ya devolvio para un articulo publicado: un
  // slug inventado por quien llame a la API nunca llena la cache.
  synchronized void guardar(String slug, FotoServida foto) {
    FotoEnCache anterior = fotosPorSlug.put(slug, new FotoEnCache(foto, reloj.instant().plus(VIGENCIA)));
    if (anterior != null) {
      pesoActualBytes -= anterior.foto().contenido().length;
    }
    pesoActualBytes += foto.contenido().length;
    var fotosPorExpulsar = fotosPorSlug.entrySet().iterator();
    while (pesoActualBytes > PESO_MAXIMO_BYTES && fotosPorExpulsar.hasNext()) {
      pesoActualBytes -= fotosPorExpulsar.next().getValue().foto().contenido().length;
      fotosPorExpulsar.remove();
    }
  }

  record FotoServida(byte[] contenido, String tipoContenido) {
  }

  private record FotoEnCache(FotoServida foto, Instant caducaEn) {
  }
}
