FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package \
    && cp target/aurinko-mcp-*.jar /build/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system aurinko \
    && useradd --system --gid aurinko --no-create-home aurinko
COPY --from=build /build/app.jar /app/app.jar
COPY start.sh /app/start.sh
RUN chmod 755 /app/start.sh
ENV HOST=0.0.0.0
ENV PORT=3000
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 3000
USER aurinko
ENTRYPOINT ["/app/start.sh"]
