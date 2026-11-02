FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
# Копируем весь проект
COPY . .
# Заходим в папку с исходниками
WORKDIR /app/src/main/java
# Компилируем файл KeyServer.java в папку /app/classes
RUN javac -d /app/classes fertdlc/keys/KeyServer.java

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Копируем скомпилированные классы
COPY --from=build /app/classes /app/classes
# Запускаем
ENTRYPOINT ["java", "-cp", "/app/classes", "fertdlc.keys.KeyServer"]
