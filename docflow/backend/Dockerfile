ARG MAVEN_JDK_TAG=3.9.6-eclipse-temurin-17
FROM maven:${MAVEN_JDK_TAG} AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

ARG RUNTIME_JAVA_MAJOR=17
FROM eclipse-temurin:${RUNTIME_JAVA_MAJOR}-jre-alpine
ARG RUNTIME_JAVA_MAJOR
WORKDIR /app
COPY --from=build /workspace/target/docflow-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
