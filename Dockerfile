# Build context: raiz do repositório (Render Dockerfile Path = Dockerfile)
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY backend/pom.xml backend/mvnw ./
COPY backend/.mvn .mvn
COPY backend/src ./src
COPY frontend /frontend

RUN chmod +x mvnw && ./mvnw -B -DskipTests package \
    && cp target/folha-platform-*.jar /app/app.jar

# Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --uid 10001 --create-home folha

COPY --from=build /app/app.jar /app/app.jar
COPY backend/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh \
    && chown -R folha:folha /app

USER folha

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
