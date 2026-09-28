package com.vexelbyte;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VexelbyteApplicationTests {

  @Test
  void elContextoDeSpringArrancaCorrectamente() {
    // Si el contexto no levanta (mapeo JPA invalido, config de seguridad rota,
    // etc.) este test falla — es la red minima antes de escribir tests de negocio.
  }
}
