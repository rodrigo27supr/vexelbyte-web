package com.vexelbyte.automatizacion.common;

import java.time.Duration;
import java.time.Instant;

// Limitador de frecuencia minimo: fuerza un intervalo de espera entre
// llamadas consecutivas. No implemento un token bucket ni una ventana
// deslizante porque los clientes de IA se llaman de forma
// secuencial dentro de un unico job programado, no en paralelo a alto
// volumen — un intervalo minimo fijo es suficiente y mucho mas facil de
// razonar y probar.
public class LimitadorFrecuencia {

  private final Duration intervaloMinimoEntreLlamadas;
  private Instant instanteUltimaLlamada = Instant.EPOCH;

  public LimitadorFrecuencia(double llamadasPermitidasPorSegundo) {
    this.intervaloMinimoEntreLlamadas = Duration.ofMillis(Math.round(1000.0 / llamadasPermitidasPorSegundo));
  }

  public synchronized void esperarTurno() {
    Duration tiempoTranscurridoDesdeUltimaLlamada = Duration.between(instanteUltimaLlamada, Instant.now());
    Duration tiempoRestanteDeEspera = intervaloMinimoEntreLlamadas.minus(tiempoTranscurridoDesdeUltimaLlamada);

    if (!tiempoRestanteDeEspera.isNegative() && !tiempoRestanteDeEspera.isZero()) {
      dormir(tiempoRestanteDeEspera);
    }

    instanteUltimaLlamada = Instant.now();
  }

  private void dormir(Duration duracion) {
    try {
      Thread.sleep(duracion.toMillis());
    } catch (InterruptedException excepcionInterrupcion) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrumpido mientras esperaba el turno del limitador de frecuencia", excepcionInterrupcion);
    }
  }
}
