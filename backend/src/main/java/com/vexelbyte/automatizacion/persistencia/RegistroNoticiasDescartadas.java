package com.vexelbyte.automatizacion.persistencia;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Memoria duradera de las noticias que la IA ya descarto por tematica: un
// veredicto editorial definitivo que no merece volver a pagarse en cuota.
@Service
public class RegistroNoticiasDescartadas {

  // Coincide con la columna motivo (VARCHAR(500)): el motivo lo redacta la IA
  // y no controlo su longitud.
  private static final int LONGITUD_MAXIMA_MOTIVO = 500;

  private final NoticiaDescartadaRepository repositorioDescartadas;

  public RegistroNoticiasDescartadas(NoticiaDescartadaRepository repositorioDescartadas) {
    this.repositorioDescartadas = repositorioDescartadas;
  }

  @Transactional(readOnly = true)
  public boolean estaDescartada(String idExternoFuente) {
    return repositorioDescartadas.existsById(idExternoFuente);
  }

  @Transactional
  public void registrarDescarte(String idExternoFuente, String motivo) {
    NoticiaDescartada noticiaDescartada = new NoticiaDescartada();
    noticiaDescartada.setIdExternoFuente(idExternoFuente);
    noticiaDescartada.setMotivo(
        motivo.length() > LONGITUD_MAXIMA_MOTIVO ? motivo.substring(0, LONGITUD_MAXIMA_MOTIVO) : motivo);
    noticiaDescartada.setFechaDescarte(OffsetDateTime.now(ZoneOffset.UTC));
    repositorioDescartadas.save(noticiaDescartada);
  }
}
