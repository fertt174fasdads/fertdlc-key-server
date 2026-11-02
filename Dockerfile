FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Копируем ВСЁ, что есть в репозитории
COPY . .

# Ищем файл KeyServer.java во всех папках и компилируем его
RUN find . -name "KeyServer.java" -exec javac -d /app/classes {} +

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Копируем скомпилированные классы
COPY --from=build /app/classes /app/classes

# Запускаем сервер
ENTRYPOINT ["java", "-cp", "/app/classes", "fertdlc.keys.KeyServer"]
