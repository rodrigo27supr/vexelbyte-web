package com.vexelbyte.automatizacion.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LimitadorFrecuenciaTest {

  @Test
  void obligaAEsperarElIntervaloMinimoEntreLlamadasConsecutivas() {
    // 20 llamadas/seg = 50ms de intervalo minimo. Uso un valor pequeno para
    // que el test sea rapido mientras sigue siendo representativo del
    // comportamiento real del limitador.
    LimitadorFrecuencia limitador = new LimitadorFrecuencia(20.0);

    Instant inicio = Instant.now();
    limitador.esperarTurno();
    limitador.esperarTurno();
    limitador.esperarTurno();
    Duration tiempoTranscurrido = Duration.between(inicio, Instant.now());

    // La primera llamada no espera; la segunda y la tercera si (50ms cada
    // una). Uso un margen de tolerancia (90ms en vez de 100ms) para no
    // volver el test fragil ante jitter normal del sistema operativo.
    assertThat(tiempoTranscurrido).isGreaterThanOrEqualTo(Duration.ofMillis(90));
  }

  @Test
  void noEsperaSiYaHaPasadoElIntervaloMinimo() {
    LimitadorFrecuencia limitador = new LimitadorFrecuencia(1000.0); // 1ms de intervalo minimo

    limitador.esperarTurno();

    Instant inicioSegundaLlamada = Instant.now();
    limitador.esperarTurno();
    Duration tiempoTranscurrido = Duration.between(inicioSegundaLlamada, Instant.now());

    assertThat(tiempoTranscurrido).isLessThan(Duration.ofMillis(50));
  }
}
