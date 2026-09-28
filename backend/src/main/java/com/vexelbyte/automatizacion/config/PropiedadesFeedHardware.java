package com.vexelbyte.automatizacion.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// cantidadMaximaPorCiclo acota las llamadas a la IA por ejecucion del job: los
// feeds traen decenas de noticias y el plan gratuito de Gemini limita a pocas
// peticiones por minuto.
@ConfigurationProperties(prefix = "vexelbyte.feed-hardware")
public record PropiedadesFeedHardware(List<String> urlsFeed, int cantidadMaximaPorCiclo) {
}
