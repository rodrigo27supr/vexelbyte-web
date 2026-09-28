package com.vexelbyte.automatizacion.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "noticias_descartadas")
@Getter
@Setter
@NoArgsConstructor
public class NoticiaDescartada {

  // El guid del item en su feed: la misma clave que articulos.id_externo_fuente,
  // asi una noticia nunca esta a la vez ingerida y descartada.
  @Id
  @Column(name = "id_externo_fuente", length = 1000)
  private String idExternoFuente;

  @Column(nullable = false, length = 500)
  private String motivo;

  @Column(name = "fecha_descarte", nullable = false)
  private OffsetDateTime fechaDescarte;
}
