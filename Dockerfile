FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /no-sql
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /no-sql
COPY --from=build /no-sql/target/*.jar app.jar

CMD ["java", "-jar", "app.jar"]