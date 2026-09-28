package com.vexelbyte.automatizacion.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import com.vexelbyte.articulos.Articulo;
import com.vexelbyte.articulos.ArticuloRepository;
import com.vexelbyte.articulos.FotoArticuloAlmacenada;
import com.vexelbyte.articulos.FotoArticuloAlmacenadaRepository;
import com.vexelbyte.automatizacion.imagenes.CompresorImagenes;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Contra una base H2 real: lo que hay que probar son las consultas de borrado,
// y un repositorio simulado no las ejecutaria.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:vexelbyte-test-retencion;DB_CLOSE_DELAY=-1")
class ServicioRetencionContenidoTest {

  private static final Clock RELOJ_FIJO = Clock.fixed(Instant.parse("2037-06-01T00:00:00Z"), ZoneOffset.UTC);

  @Autowired
  private ArticuloRepository repositorioArticulos;

  @Autowired
  private FotoArticuloAlmacenadaRepository repositorioFotos;

  @Autowired
  private NoticiaDescartadaRepository repositorioDescartadas;

  @Autowired
  private PlatformTransactionManager gestorTransacciones;

  @AfterEach
  void limpiar() {
    repositorioFotos.deleteAll();
    repositorioArticulos.deleteAll();
    repositorioDescartadas.deleteAll();
  }

  private ServicioRetencionContenido crearServicio() {
    return new ServicioRetencionContenido(
        repositorioArticulos, repositorioFotos, repositorioDescartadas, new CompresorImagenes(), RELOJ_FIJO);
  }

  // Creo el servicio a mano para fijar el reloj, asi que su @Transactional no
  // actua: la transaccion la abro yo, como haria el proxy de Spring.
  private void aplicarRetencionEnTransaccion() {
    new TransactionTemplate(gestorTransacciones).executeWithoutResult(estado -> crearServicio().aplicarRetencion());
  }

  private Articulo guardarArticuloConFoto(String slug, String fechaPublicacion) {
    Articulo articulo = new Articulo();
    articulo.setSlug(slug);
    articulo.setTitulo("Titulo " + slug);
    articulo.setDescripcion("Descripcion");
    articulo.setCuerpoHtml("<p>Cuerpo</p>");
    articulo.setFechaPublicacion(OffsetDateTime.parse(fechaPublicacion));
    articulo.setFechaCreacion(OffsetDateTime.parse(fechaPublicacion));
    articulo.setAutor("Equipo VexelByte");
    articulo.setBorrador(false);
    articulo.setFotoUrl("https://images.example/" + slug + ".jpg");
    articulo.setFotoAncho(1200);
    articulo.setFotoAlto(627);
    articulo.setFotoAutor("Autor");
    articulo.setFotoLicencia("Material de prensa");
    articulo.setFotoUrlOrigen("https://fuente.example/" + slug);
    articulo.setFotoRevisada(true);
    Articulo guardado = repositorioArticulos.save(articulo);

    FotoArticuloAlmacenada foto = new FotoArticuloAlmacenada();
    foto.setArticuloId(guardado.getId());
    foto.setContenido(new byte[] {1, 2, 3});
    foto.setTipoContenido("image/jpeg");
    repositorioFotos.save(foto);
    return guardado;
  }

  private void guardarDescarte(String identificador, String fechaDescarte) {
    NoticiaDescartada descartada = new NoticiaDescartada();
    descartada.setIdExternoFuente(identificador);
    descartada.setMotivo("Fuera de tematica");
    descartada.setFechaDescarte(OffsetDateTime.parse(fechaDescarte));
    repositorioDescartadas.save(descartada);
  }

  @Test
  void quitaLaFotoAlAnoYMedioPeroConservaElArticulo() {
    Articulo reciente = guardarArticuloConFoto("reciente", "2037-01-01T00:00:00Z");
    Articulo antiguo = guardarArticuloConFoto("antiguo", "2035-10-01T00:00:00Z");

    aplicarRetencionEnTransaccion();

    Articulo antiguoTrasRetencion = repositorioArticulos.findById(antiguo.getId()).orElseThrow();
    assertThat(antiguoTrasRetencion.getFotoUrl()).isNull();
    assertThat(antiguoTrasRetencion.isFotoRevisada()).isTrue();
    assertThat(repositorioFotos.existsById(antiguo.getId())).isFalse();
    assertThat(repositorioArticulos.findById(reciente.getId()).orElseThrow().getFotoUrl()).isNotNull();
    assertThat(repositorioFotos.existsById(reciente.getId())).isTrue();
  }

  @Test
  void borraElArticuloEnteroALosDiezAnos() {
    Articulo decenal = guardarArticuloConFoto("decenal", "2027-05-01T00:00:00Z");
    Articulo casiDecenal = guardarArticuloConFoto("casi-decenal", "2027-07-01T00:00:00Z");

    aplicarRetencionEnTransaccion();

    assertThat(repositorioArticulos.existsById(decenal.getId())).isFalse();
    assertThat(repositorioFotos.existsById(decenal.getId())).isFalse();
    assertThat(repositorioArticulos.existsById(casiDecenal.getId())).isTrue();
  }

  @Test
  void borraLosDescartesDeMasDeUnMes() {
    guardarDescarte("antiguo", "2037-04-15T00:00:00Z");
    guardarDescarte("reciente", "2037-05-20T00:00:00Z");

    aplicarRetencionEnTransaccion();

    assertThat(repositorioDescartadas.existsById("antiguo")).isFalse();
    assertThat(repositorioDescartadas.existsById("reciente")).isTrue();
  }

  @Test
  void recomprimeLasFotosGuardadasSinComprimirYActualizaSusDimensiones() throws Exception {
    Articulo articulo = guardarArticuloConFoto("pesada", "2037-05-01T00:00:00Z");
    BufferedImage imagen = new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB);
    Random generador = new Random(7);
    for (int fila = 0; fila < 1200; fila++) {
      for (int columna = 0; columna < 2400; columna++) {
        imagen.setRGB(columna, fila, generador.nextInt(0xFFFFFF));
      }
    }
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    ImageIO.write(imagen, "png", salida);
    FotoArticuloAlmacenada foto = repositorioFotos.findById(articulo.getId()).orElseThrow();
    foto.setContenido(salida.toByteArray());
    foto.setTipoContenido("image/png");
    repositorioFotos.save(foto);

    aplicarRetencionEnTransaccion();

    FotoArticuloAlmacenada recomprimida = repositorioFotos.findById(articulo.getId()).orElseThrow();
    assertThat(recomprimida.getTipoContenido()).isEqualTo("image/jpeg");
    assertThat(recomprimida.getContenido().length).isLessThan(salida.size());
    assertThat(repositorioArticulos.findById(articulo.getId()).orElseThrow().getFotoAncho()).isEqualTo(1600);
  }
}
