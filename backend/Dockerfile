# syntax=docker/dockerfile:1
# Contexto de build: raíz del repositorio (necesita /backend y /database).
#   docker build -f backend/Dockerfile -t smartops-api .

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY database ./database
COPY backend/pom.xml backend/pom.xml
COPY backend/smartops-domain/pom.xml backend/smartops-domain/pom.xml
COPY backend/smartops-application/pom.xml backend/smartops-application/pom.xml
COPY backend/smartops-infrastructure/pom.xml backend/smartops-infrastructure/pom.xml
COPY backend/smartops-batch/pom.xml backend/smartops-batch/pom.xml
COPY backend/smartops-api/pom.xml backend/smartops-api/pom.xml
# Capa de dependencias cacheable: solo se invalida si cambia algún pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -q -f backend/pom.xml dependency:go-offline -DskipTests || true
COPY backend ./backend
RUN --mount=type=cache,target=/root/.m2 mvn -q -f backend/pom.xml package -DskipTests \
    && java -Djarmode=tools -jar backend/smartops-api/target/smartops-api.jar extract --layers --launcher --destination /app

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S smartops && adduser -S smartops -G smartops \
    && mkdir -p /data/inbox && chown -R smartops:smartops /data
WORKDIR /app
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
USER smartops
ENV SMARTOPS_INBOX=/data/inbox \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q UP || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
