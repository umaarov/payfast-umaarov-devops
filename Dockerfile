FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace

ARG SERVICE

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY warehouse-service/pom.xml warehouse-service/pom.xml
COPY delivery-service/pom.xml delivery-service/pom.xml
COPY tracking-service/pom.xml tracking-service/pom.xml
RUN chmod +x mvnw

COPY ${SERVICE}/src ${SERVICE}/src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -pl ${SERVICE} -am package -DskipTests \
    && cp ${SERVICE}/target/${SERVICE}-*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-noble

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/app.jar /app/app.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
