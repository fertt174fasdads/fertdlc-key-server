FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
# Компилируем проект
RUN gradle compileJava

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Копируем скомпилированные классы
COPY --from=build /app/build/classes/java/main /app/classes
# Запускаем сервер, указывая путь к классам
ENTRYPOINT ["java", "-cp", "/app/classes", "fertdlc.keys.KeyServer"]
