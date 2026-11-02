FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle fatJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Ищем любой JAR-файл в папке сборки и копируем его как app.jar
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
