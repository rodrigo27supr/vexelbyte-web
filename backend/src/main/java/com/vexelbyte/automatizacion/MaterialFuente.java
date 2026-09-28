package com.vexelbyte.automatizacion;

import com.vexelbyte.automatizacion.ia.SolicitudGeneracionContenido;
import java.util.List;

// Resultado de preparar el material de una noticia antes de llamar a la IA.
// Aplazado no se registra como descarte: el fallo fue de red, no de la fuente.
sealed interface MaterialFuente {

  record MaterialListo(SolicitudGeneracionContenido solicitud, List<String> enlacesFuentes) implements MaterialFuente {
  }

  record SinMaterial(String motivo) implements MaterialFuente {
  }

  record Aplazado() implements MaterialFuente {
  }
}
