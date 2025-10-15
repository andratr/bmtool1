# ---- Build stage (common) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src

RUN mvn -q -DskipTests clean package

# ---- Dev run stage ----
FROM maven:3.9-eclipse-temurin-21 AS dev
WORKDIR /app

# ---- Prod run stage ----
FROM eclipse-temurin:21-jre AS prod
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]

