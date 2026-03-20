FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
COPY mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline

COPY src src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system appgroup && adduser --system appuser --ingroup appgroup

COPY --from=build /app/target/mySpaCoverSkuRecommendation-0.0.1-SNAPSHOT.jar /app/app.jar

RUN mkdir -p /app/data && chown -R appuser:appgroup /app

USER appuser
EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
