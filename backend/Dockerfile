# Fase de compilacion -- imagen completa con Maven y JDK 21 (misma version
# que fija java.version en pom.xml). Solo existe para generar el jar; no
# forma parte de la imagen final que Render despliega y ejecuta.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copio primero solo el pom.xml para que Docker cachee la capa de
# dependencias por separado del codigo fuente -- un cambio en una clase Java
# no invalida esta capa, asi que un rebuild no vuelve a descargar todo Maven
# Central cada vez que cambia una linea de src/.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# Fase de ejecucion -- solo el JRE (no el JDK completo ni Maven), sobre
# Alpine para que la imagen final sea lo mas ligera posible: el plan
# gratuito de Render da poca RAM total al contenedor, y no tiene sentido
# gastarla en herramientas de build que ya cumplieron su funcion.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /build/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# -Xmx256m es obligatorio en el plan gratuito de Render (RAM total limitada
# para el contenedor entero, sistema operativo incluido) -- sin este techo
# explicito, la JVM calcula su heap por defecto en funcion de la memoria que
# detecta en el host, no de la que Render realmente le asigna al contenedor,
# y termina provocando un OOM kill del proceso.
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
