FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /no-sql
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /no-sql
COPY --from=build /no-sql/target/*.jar app.jar

CMD ["java", "-jar", "app.jar"]