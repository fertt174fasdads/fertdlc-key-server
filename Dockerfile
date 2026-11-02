FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
# Просто компилируем код (без сборки JAR)
RUN gradle compileJava

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Копируем скомпилированные классы и зависимости
COPY --from=build /app/build/classes/java/main /app/classes
COPY --from=build /app/build/resources/main /app/resources
# Запускаем через classpath (без JAR)
ENTRYPOINT ["java", "-cp", "/app/classes:/app/resources", "fertdlc.keys.KeyServer"]
