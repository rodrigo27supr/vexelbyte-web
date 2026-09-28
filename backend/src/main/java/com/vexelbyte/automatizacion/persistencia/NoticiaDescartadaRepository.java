package com.vexelbyte.automatizacion.persistencia;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticiaDescartadaRepository extends JpaRepository<NoticiaDescartada, String> {

  @Modifying
  @Query("delete from NoticiaDescartada descartada where descartada.fechaDescarte < :limite")
  int borrarDescartadasAntesDe(@Param("limite") OffsetDateTime limite);
}
