FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/codelens-backend-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 1001 codelens \
    && mkdir -p /var/codelens/repos \
    && chown codelens /var/codelens/repos
USER codelens
WORKDIR /app
COPY --from=build /src/app.jar app.jar
EXPOSE 8090
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
