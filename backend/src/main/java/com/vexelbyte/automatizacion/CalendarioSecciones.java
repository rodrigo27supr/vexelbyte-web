package com.vexelbyte.automatizacion;

import com.vexelbyte.articulos.ArticuloRepository;
import com.vexelbyte.articulos.CategoriaArticulo;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// Decision del Tech Lead (2026-09-27): una noticia de cada seccion en dias
// alternos, desde las 08:00 de Madrid, empezando el 28 de septiembre de 2026.
// Un calendario fijo y no "dos dias desde el ultimo articulo": con este ultimo,
// cada ciclo empieza unos minutos mas tarde y las secciones se iban desfasando.
@Component
public class CalendarioSecciones {

  static final ZoneId ZONA_EDITORIAL = ZoneId.of("Europe/Madrid");
  static final LocalDate PRIMER_DIA_DE_PUBLICACION = LocalDate.of(2026, 9, 28);
  static final int DIAS_ENTRE_PUBLICACIONES = 2;
  static final LocalTime HORA_DE_PUBLICACION = LocalTime.of(8, 0);

  private final ArticuloRepository repositorioArticulos;
  private final Clock reloj;

  public CalendarioSecciones(ArticuloRepository repositorioArticulos, Clock reloj) {
    this.repositorioArticulos = repositorioArticulos;
    this.reloj = reloj;
  }

  // En un dia de publicacion, a partir de las 08:00, las secciones que aun no
  // han publicado ese dia. Si el ciclo de la manana no encuentra noticia para
  // una seccion, el de la tarde lo vuelve a intentar.
  public Set<CategoriaArticulo> obtenerSeccionesAbiertas() {
    ZonedDateTime ahora = ZonedDateTime.now(reloj.withZone(ZONA_EDITORIAL));
    if (!esDiaDePublicacion(ahora.toLocalDate()) || ahora.toLocalTime().isBefore(HORA_DE_PUBLICACION)) {
      return EnumSet.noneOf(CategoriaArticulo.class);
    }
    OffsetDateTime inicioDelDia = ahora.toLocalDate().atStartOfDay(ZONA_EDITORIAL).toOffsetDateTime();
    return Arrays.stream(CategoriaArticulo.values())
        .filter(seccion -> !repositorioArticulos.existsByCategoriaAndFechaCreacionAfter(seccion, inicioDelDia))
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(CategoriaArticulo.class)));
  }

  private boolean esDiaDePublicacion(LocalDate dia) {
    long diasDesdeElInicio = ChronoUnit.DAYS.between(PRIMER_DIA_DE_PUBLICACION, dia);
    return diasDesdeElInicio >= 0 && diasDesdeElInicio % DIAS_ENTRE_PUBLICACIONES == 0;
  }
}
