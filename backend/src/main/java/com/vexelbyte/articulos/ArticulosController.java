package com.vexelbyte.articulos;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// API de lectura publica que consume el frontend Astro en build-time: sin
// autenticacion porque es contenido editorial ya publicado, no datos privados.
// SecurityConfig la deja en permitAll solo para GET, nunca para un futuro
// metodo de escritura bajo esta misma ruta.
@RestController
@RequestMapping("/api/articulos")
public class ArticulosController {

  private final ServicioLecturaArticulos servicioLecturaArticulos;

  public ArticulosController(ServicioLecturaArticulos servicioLecturaArticulos) {
    this.servicioLecturaArticulos = servicioLecturaArticulos;
  }

  @GetMapping
  public List<ArticuloResumenResponse> listarArticulosPublicados() {
    return servicioLecturaArticulos.listarPublicados();
  }

  @GetMapping("/{slug}")
  public ResponseEntity<ArticuloDetalleResponse> obtenerArticuloPorSlug(@PathVariable String slug) {
    return servicioLecturaArticulos.obtenerPorSlug(slug);
  }

  @GetMapping("/{slug}/foto")
  public ResponseEntity<byte[]> obtenerFotoDeArticulo(@PathVariable String slug) {
    return servicioLecturaArticulos.obtenerFoto(slug);
  }
}
