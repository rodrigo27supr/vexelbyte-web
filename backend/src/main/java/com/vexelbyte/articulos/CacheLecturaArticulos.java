package com.vexelbyte.articulos;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// La API es publica y cada lectura sale de Neon, cuyo plan gratuito corta la
// base a los 5 GB de transferencia al mes (se agoto el 15-10-2026). Quien pida
// la lista o un articulo en bucle no cuesta dinero, pero pararia la publicacion
// hasta fin de mes: con esta cache Neon responde cada cosa una vez por vigencia,
// pida lo que pida el visitante. El ciclo la vacia al publicar (invalidar) para
// que la reconstruccion de la web ya vea la noticia nueva.
@Component
public class CacheLecturaArticulos {

  // Si una noticia sale por la espera maxima de foto y no por el ciclo, es la
  // unica que tarda en verse: seis horas es el intervalo entre ciclos.
  private static final Duration VIGENCIA_LISTA = Duration.ofHours(6);

  // Un articulo solo cambia al reescribirse o despublicarse, y ambas cosas son raras.
  private static final Duration VIGENCIA_DETALLE = Duration.ofHours(24);

  // Unos 12 MB con articulos de 12 KB; el heap del plan gratuito de Render es de 256 MB.
  private static final int MAXIMO_DETALLES = 1000;

  private final Clock reloj;
  private final CacheFotosArticulos cacheFotos;
  // Orden de acceso para expulsar el detalle menos usado.
  private final Map<String, DetalleEnCache> detallesPorSlug = new LinkedHashMap<>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, DetalleEnCache> masAntiguo) {
      return size() > MAXIMO_DETALLES;
    }
  };
  private List<ArticuloResumenResponse> lista;
  private Set<String> slugsPublicados = Set.of();
  private Instant listaCaducaEn = Instant.MIN;

  public CacheLecturaArticulos(Clock reloj) {
    this.reloj = reloj;
    this.cacheFotos = new CacheFotosArticulos(reloj);
  }

  public synchronized void invalidar() {
    lista = null;
    slugsPublicados = Set.of();
    listaCaducaEn = Instant.MIN;
    detallesPorSlug.clear();
    cacheFotos.vaciar();
  }

  CacheFotosArticulos fotos() {
    return cacheFotos;
  }

  synchronized Optional<List<ArticuloResumenResponse>> obtenerLista() {
    return hayListaVigente() ? Optional.of(lista) : Optional.empty();
  }

  synchronized void guardarLista(List<ArticuloResumenResponse> nuevaLista) {
    lista = nuevaLista;
    slugsPublicados = nuevaLista.stream().map(ArticuloResumenResponse::slug).collect(Collectors.toSet());
    listaCaducaEn = reloj.instant().plus(VIGENCIA_LISTA);
  }

  // Con la lista vigente a mano, un slug que no esta en ella se rechaza sin
  // preguntar a la base: pedir slugs inventados en bucle no gasta nada de Neon.
  synchronized boolean sabeQueNoEstaPublicado(String slug) {
    return hayListaVigente() && !slugsPublicados.contains(slug);
  }

  synchronized Optional<ArticuloDetalleResponse> obtenerDetalle(String slug) {
    DetalleEnCache detalleEnCache = detallesPorSlug.get(slug);
    if (detalleEnCache == null) {
      return Optional.empty();
    }
    if (detalleEnCache.caducaEn().isBefore(reloj.instant())) {
      detallesPorSlug.remove(slug);
      return Optional.empty();
    }
    return Optional.of(detalleEnCache.detalle());
  }

  // Solo se guarda lo que la base ya devolvio como publicado.
  synchronized void guardarDetalle(String slug, ArticuloDetalleResponse detalle) {
    detallesPorSlug.put(slug, new DetalleEnCache(detalle, reloj.instant().plus(VIGENCIA_DETALLE)));
  }

  private boolean hayListaVigente() {
    return lista != null && !listaCaducaEn.isBefore(reloj.instant());
  }

  private record DetalleEnCache(ArticuloDetalleResponse detalle, Instant caducaEn) {
  }
}
