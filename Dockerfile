FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle compileJava

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Копируем только скомпилированные классы
COPY --from=build /app/build/classes/java/main /app/classes
# Запускаем сервер
ENTRYPOINT ["java", "-cp", "/app/classes", "fertdlc.keys.KeyServer"]
