FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
# Собираем проект с помощью shadowJar
RUN gradle shadowJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Копируем именно "толстый" JAR
COPY --from=build /app/build/libs/fertdlc-1.0.0-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
