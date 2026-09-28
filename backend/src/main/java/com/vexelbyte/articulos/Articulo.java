package com.vexelbyte.articulos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "articulos")
@Getter
@Setter
@NoArgsConstructor
public class Articulo {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Identificador de la noticia en su fuente original (el guid del item RSS).
  // Es la clave de idempotencia de ServicioIngestaArticulos: el feed repite
  // las mismas noticias en cada ciclo y no deben guardarse dos veces.
  @Column(name = "id_externo_fuente", unique = true)
  private String idExternoFuente;

  // Pieza original del medio: de ahi sale la imagen de prensa del articulo.
  // No siempre coincide con el guid (TechPowerUp usa un hash).
  @Column(name = "enlace_fuente", length = 1000)
  private String enlaceFuente;

  @NotBlank
  @Column(nullable = false)
  private String titulo;

  @NotBlank
  @Column(nullable = false, length = 500)
  private String descripcion;

  @NotBlank
  @Column(nullable = false, unique = true)
  private String slug;

  // @Lob mapeaba este String a oid (large object de Postgres), pero la
  // migracion V1 crea la columna como TEXT -- con ddl-auto: validate contra
  // Postgres real (Neon) eso revienta con "wrong column type encountered".
  // JdbcTypeCode(LONGVARCHAR) fuerza el mapeo VARCHAR/TEXT que de verdad
  // coincide con el esquema, en vez del mapeo CLOB/oid por defecto de Hibernate 6.
  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  @Column(nullable = false, columnDefinition = "TEXT")
  private String cuerpoHtml;

  @NotNull
  @Column(nullable = false)
  private OffsetDateTime fechaPublicacion;

  private OffsetDateTime fechaActualizacion;

  // Cuando lo publico VexelByte, no la fuente: marca el ritmo de un articulo
  // por seccion cada dos dias.
  @NotNull
  @Column(name = "fecha_creacion", nullable = false)
  private OffsetDateTime fechaCreacion;

  @NotBlank
  @Column(nullable = false)
  private String autor;

  // La ingesta publica directamente; sigue existiendo para despublicar a mano
  // un articulo concreto sin borrarlo.
  @Column(nullable = false)
  private boolean borrador = true;

  @Enumerated(EnumType.STRING)
  @Column(length = 30)
  private CategoriaArticulo categoria;

  @Column(length = 150)
  private String producto;

  // Guardo las listas como JSON serializado: la conversion la hacen los
  // servicios de ingesta y lectura, asi la entidad no depende de Jackson.
  @Column(name = "especificaciones_json")
  @JdbcTypeCode(SqlTypes.JSON)
  private String especificacionesJson;

  @Column(name = "puntos_clave_json")
  @JdbcTypeCode(SqlTypes.JSON)
  private String puntosClaveJson;

  // Imagen de prensa de la que se descargo la foto (los bytes viven en
  // FotoArticuloAlmacenada). Nula si no hay foto y el frontend usa la portada
  // de datos: solo se rellena cuando la descarga se guardo. foto_autor guarda
  // la marca a la que se atribuye.
  @Column(name = "foto_url", length = 1000)
  private String fotoUrl;

  @Column(name = "foto_ancho")
  private Integer fotoAncho;

  @Column(name = "foto_alto")
  private Integer fotoAlto;

  @Column(name = "foto_autor", length = 300)
  private String fotoAutor;

  @Column(name = "foto_licencia", length = 100)
  private String fotoLicencia;

  @Column(name = "foto_url_licencia", length = 500)
  private String fotoUrlLicencia;

  @Column(name = "foto_url_origen", length = 1000)
  private String fotoUrlOrigen;

  @Column(name = "foto_revisada", nullable = false)
  private boolean fotoRevisada;

  // Foto de banco de imagenes que ilustra el tema, no el producto concreto.
  @Column(name = "foto_es_ilustrativa", nullable = false)
  private boolean fotoEsIlustrativa;

  // false en los articulos anteriores a la cobertura de otros medios (V12):
  // el ciclo los reescribe. Uno nuevo ya nace con cobertura, por eso empieza a true.
  @Column(name = "redaccion_revisada", nullable = false)
  private boolean redaccionRevisada = true;
}
