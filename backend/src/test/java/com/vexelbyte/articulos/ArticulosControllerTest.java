package com.vexelbyte.articulos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// La logica de filtrado y mapeo vive en ServicioLecturaArticulos (ver su
// propio test): este controlador solo delega.
class ArticulosControllerTest {

  private final ServicioLecturaArticulos servicioLectura = mock(ServicioLecturaArticulos.class);
  private final ArticulosController controlador = new ArticulosController(servicioLectura);

  @Test
  void listarArticulosPublicadosDelegaEnElServicio() {
    List<ArticuloResumenResponse> listaSimulada = List.of();
    when(servicioLectura.listarPublicados()).thenReturn(listaSimulada);

    assertThat(controlador.listarArticulosPublicados()).isSameAs(listaSimulada);
  }

  @Test
  void obtenerArticuloPorSlugDelegaEnElServicio() {
    ResponseEntity<ArticuloDetalleResponse> respuestaSimulada = ResponseEntity.notFound().build();
    when(servicioLectura.obtenerPorSlug("un-slug")).thenReturn(respuestaSimulada);

    ResponseEntity<ArticuloDetalleResponse> resultado = controlador.obtenerArticuloPorSlug("un-slug");

    assertThat(resultado.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(servicioLectura).obtenerPorSlug("un-slug");
  }
}
