package com.vexelbyte.articulos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticuloRepository extends JpaRepository<Articulo, Long> {

  boolean existsByIdExternoFuente(String idExternoFuente);

  // Filtro borrador=false en la propia consulta, no en Java despues de traer
  // todo: un articulo despublicado nunca pasa por la capa de aplicacion en
  // una respuesta publica, ni por accidente.
  List<Articulo> findByBorradorFalseOrderByFechaPublicacionDesc();

  Optional<Articulo> findBySlugAndBorradorFalse(String slug);

  List<Articulo> findTop50ByBorradorFalseAndFotoRevisadaFalseOrderByFechaPublicacionDesc();

  boolean existsByFotoUrl(String fotoUrl);

  // Los mas recientes primero: son los que mas se leen y los que tienen la
  // cobertura mas facil de encontrar.
  List<Articulo> findByBorradorFalseAndRedaccionRevisadaFalseOrderByFechaPublicacionDesc(Limit limite);

  boolean existsByCategoriaAndFechaCreacionAfter(CategoriaArticulo categoria, OffsetDateTime limite);

  // La foto caducada deja paso a la portada de datos; foto_revisada sigue a
  // true para que el ciclo no le busque otra.
  @Modifying
  @Query("""
      update Articulo articulo set articulo.fotoUrl = null, articulo.fotoAncho = null, articulo.fotoAlto = null,
        articulo.fotoAutor = null, articulo.fotoLicencia = null, articulo.fotoUrlLicencia = null,
        articulo.fotoUrlOrigen = null, articulo.fotoEsIlustrativa = false
      where articulo.fechaPublicacion < :limite and articulo.fotoUrl is not null""")
  int quitarFotoDeArticulosPublicadosAntesDe(@Param("limite") OffsetDateTime limite);

  @Modifying
  @Query("delete from Articulo articulo where articulo.fechaPublicacion < :limite")
  int borrarArticulosPublicadosAntesDe(@Param("limite") OffsetDateTime limite);
}
