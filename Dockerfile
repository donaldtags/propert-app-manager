FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# fontconfig + a font: the JRE image has none, and server-side image rendering (listing share
# image, Java2D) fails without them. PDFs use built-in fonts and don't need this.
RUN apk add --no-cache fontconfig ttf-dejavu \
    && addgroup -S estatex && adduser -S estatex -G estatex \
    && mkdir -p /app/storage/lease-documents /app/storage/property-photos \
       /app/storage/mandate-documents /app/storage/agency-documents \
    && chown -R estatex:estatex /app

COPY --from=build --chown=estatex:estatex /app/target/*.jar app.jar

USER estatex
EXPOSE 8081
# Sized for small containers (Render free = 512 MB): cap the heap below the container limit,
# headless AWT for image rendering, and a container-friendly GC.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -Djava.awt.headless=true"
ENTRYPOINT ["java", "-jar", "app.jar"]
