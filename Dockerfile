FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S primenest && adduser -S primenest -G primenest \
    && mkdir -p /app/storage/lease-documents /app/storage/property-photos \
    && chown -R primenest:primenest /app

COPY --from=build --chown=primenest:primenest /app/target/*.jar app.jar

USER primenest
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
