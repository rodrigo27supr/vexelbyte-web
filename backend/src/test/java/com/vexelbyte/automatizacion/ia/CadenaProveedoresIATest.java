package com.vexelbyte.automatizacion.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class CadenaProveedoresIATest {

  private final ClienteModeloIA primerProveedor = crearProveedorSimulado("primero");
  private final ClienteModeloIA segundoProveedor = crearProveedorSimulado("segundo");
  private final CadenaProveedoresIA cadena = new CadenaProveedoresIA(List.of(primerProveedor, segundoProveedor));

  private static ClienteModeloIA crearProveedorSimulado(String nombre) {
    ClienteModeloIA proveedor = mock(ClienteModeloIA.class);
    when(proveedor.describirProveedor()).thenReturn(nombre);
    return proveedor;
  }

  private static HttpClientErrorException errorDeCuota() {
    return HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "cuota agotada", null, null, null);
  }

  @Test
  void usaElPrimerProveedorCuandoResponde() {
    when(primerProveedor.generarTexto(anyString(), anyString())).thenReturn("respuesta del primero");

    assertThat(cadena.generarTexto("sistema", "usuario")).isEqualTo("respuesta del primero");
    verify(segundoProveedor, never()).generarTexto(anyString(), anyString());
  }

  @Test
  void pasaAlSiguienteProveedorCuandoElPrimeroAgotaSuCuota() {
    when(primerProveedor.generarTexto(anyString(), anyString())).thenThrow(errorDeCuota());
    when(segundoProveedor.generarTexto(anyString(), anyString())).thenReturn("respuesta del segundo");

    assertThat(cadena.generarTexto("sistema", "usuario")).isEqualTo("respuesta del segundo");
  }

  @Test
  void noVuelveAPreguntarAlProveedorConCuotaAgotadaEnLaSiguienteNoticia() {
    when(primerProveedor.generarTexto(anyString(), anyString())).thenThrow(errorDeCuota());
    when(segundoProveedor.generarTexto(anyString(), anyString())).thenReturn("respuesta del segundo");

    cadena.generarTexto("sistema", "primera noticia");
    cadena.generarTexto("sistema", "segunda noticia");

    verify(primerProveedor, times(1)).generarTexto(anyString(), anyString());
    verify(segundoProveedor, times(2)).generarTexto(anyString(), anyString());
  }

  @Test
  void pasaAlSiguienteSinSuspenderAnteUnFalloPuntualDelServidor() {
    when(primerProveedor.generarTexto(anyString(), anyString()))
        .thenThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "caido", null, null, null))
        .thenReturn("el primero vuelve a responder");
    when(segundoProveedor.generarTexto(anyString(), anyString())).thenReturn("respuesta del segundo");

    assertThat(cadena.generarTexto("sistema", "primera noticia")).isEqualTo("respuesta del segundo");
    assertThat(cadena.generarTexto("sistema", "segunda noticia")).isEqualTo("el primero vuelve a responder");
  }

  @Test
  void lanzaSinProveedorDisponibleCuandoTodosFallanPorTimeoutORespuestaVacia() {
    when(primerProveedor.generarTexto(anyString(), anyString()))
        .thenThrow(new ResourceAccessException("timeout de lectura"));
    when(segundoProveedor.generarTexto(anyString(), anyString()))
        .thenThrow(new IllegalStateException("respuesta sin contenido"));

    assertThatThrownBy(() -> cadena.generarTexto("sistema", "usuario"))
        .isInstanceOf(SinProveedorIADisponibleException.class);
  }

  @Test
  void lanzaSinProveedorDisponibleCuandoLaCadenaEstaVacia() {
    CadenaProveedoresIA cadenaVacia = new CadenaProveedoresIA(List.of());

    assertThatThrownBy(() -> cadenaVacia.generarTexto("sistema", "usuario"))
        .isInstanceOf(SinProveedorIADisponibleException.class);
  }

  @Test
  void saltaSinSuspenderloAlProveedorQueNoVeImagenes() {
    ImagenParaIA imagen = new ImagenParaIA(new byte[] {1}, "image/png");
    when(primerProveedor.generarTextoConImagen(anyString(), anyString(), any(ImagenParaIA.class)))
        .thenThrow(new UnsupportedOperationException("primero no admite imagenes"));
    when(segundoProveedor.generarTextoConImagen(anyString(), anyString(), any(ImagenParaIA.class)))
        .thenReturn("respuesta con imagen");
    when(primerProveedor.generarTexto(anyString(), anyString())).thenReturn("respuesta de texto");

    assertThat(cadena.generarTextoConImagen("sistema", "usuario", imagen)).isEqualTo("respuesta con imagen");
    // Que no vea imagenes no lo aparta para el texto.
    assertThat(cadena.generarTexto("sistema", "usuario")).isEqualTo("respuesta de texto");
  }

  @Test
  void laCuotaAgotadaConImagenTambienApartaAlProveedorParaElTexto() {
    ImagenParaIA imagen = new ImagenParaIA(new byte[] {1}, "image/png");
    when(primerProveedor.generarTextoConImagen(anyString(), anyString(), any(ImagenParaIA.class)))
        .thenThrow(errorDeCuota());
    when(segundoProveedor.generarTextoConImagen(anyString(), anyString(), any(ImagenParaIA.class)))
        .thenReturn("respuesta con imagen");
    when(segundoProveedor.generarTexto(anyString(), anyString())).thenReturn("respuesta del segundo");

    cadena.generarTextoConImagen("sistema", "usuario", imagen);

    assertThat(cadena.generarTexto("sistema", "usuario")).isEqualTo("respuesta del segundo");
    verify(primerProveedor, never()).generarTexto(anyString(), anyString());
  }
}
