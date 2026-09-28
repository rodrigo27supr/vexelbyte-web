package com.vexelbyte.articulos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Bytes de la foto de un articulo, en tabla aparte de articulos para que el
// listado no los cargue. La sirve la API y el build de Astro la optimiza.
@Entity
@Table(name = "fotos_articulos")
@Getter
@Setter
@NoArgsConstructor
public class FotoArticuloAlmacenada {

  // Tope de la descarga (ClienteImagenesWikimedia): tambien fija la columna en H2.
  public static final int TAMANO_MAXIMO_BYTES = 5_000_000;

  @Id
  @Column(name = "articulo_id")
  private Long articuloId;

  @Column(nullable = false, length = TAMANO_MAXIMO_BYTES)
  private byte[] contenido;

  @Column(name = "tipo_contenido", nullable = false, length = 50)
  private String tipoContenido;
}
