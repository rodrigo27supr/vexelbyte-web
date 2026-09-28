package com.vexelbyte;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Excluyo el usuario en memoria que Spring Security crea por defecto: la API
// solo expone lectura publica, y una contrasena generada en cada arranque es
// una credencial huerfana que nadie usa pero que queda impresa en el log.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class VexelbyteApplication {

  public static void main(String[] argumentosLineaComandos) {
    SpringApplication.run(VexelbyteApplication.class, argumentosLineaComandos);
  }
}
