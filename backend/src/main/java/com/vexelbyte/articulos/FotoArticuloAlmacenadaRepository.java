package com.vexelbyte.articulos;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FotoArticuloAlmacenadaRepository extends JpaRepository<FotoArticuloAlmacenada, Long> {

  @Modifying
  @Query("""
      delete from FotoArticuloAlmacenada foto
      where foto.articuloId in (select articulo.id from Articulo articulo where articulo.fechaPublicacion < :limite)""")
  int borrarFotosDeArticulosPublicadosAntesDe(@Param("limite") OffsetDateTime limite);

  @Query(value = "select articulo_id from fotos_articulos where octet_length(contenido) > :limiteBytes limit :maximo", nativeQuery = true)
  List<Long> buscarFotosMasPesadasQue(@Param("limiteBytes") int limiteBytes, @Param("maximo") int maximo);
}
