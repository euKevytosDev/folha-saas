# Build
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src ./src
RUN chmod +x mvnw && ./mvnw -B -DskipTests package \
    && cp target/folha-platform-*.jar /app/app.jar

# Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --uid 10001 --create-home folha

COPY --from=build /app/app.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh \
    && chown -R folha:folha /app

USER folha

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
