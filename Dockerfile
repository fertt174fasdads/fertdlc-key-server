FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
# Запускаем сборку именно через shadowJar
RUN gradle shadowJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Копируем файл, который создал shadowJar
COPY --from=build /app/build/libs/fertdlc-1.0.0-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
