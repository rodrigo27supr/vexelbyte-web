package com.vexelbyte.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain cadenaFiltrosSeguridad(HttpSecurity configuracionHttp) throws Exception {
    // API sin estado (sin cookies de sesion), por eso desactivo CSRF — la
    // proteccion CSRF solo tiene sentido cuando la autenticacion viaja en cookie.
    configuracionHttp
        .sessionManagement(gestionSesion -> gestionSesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(autorizacion -> autorizacion
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            // La API de lectura de articulos es publica a proposito: el frontend
            // Astro la consume en build-time sin autenticacion, y es contenido ya
            // publicado (ServicioLecturaArticulos solo devuelve borrador=false).
            // Restrinjo el permitAll a GET: un futuro POST/PUT/DELETE bajo la
            // misma ruta caeria en el anyRequest().authenticated() de abajo.
            .requestMatchers(HttpMethod.GET, "/api/articulos/**").permitAll()
            // No hay proveedor de autenticacion: el resto de la API queda
            // cerrada por defecto en vez de abierta.
            .anyRequest().authenticated());

    return configuracionHttp.build();
  }
}
