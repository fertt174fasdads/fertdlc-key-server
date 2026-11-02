package fertdlc.loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class MinecraftInstaller {
    
    // Твой ник в игре
    private static final String NICKNAME = "fertyikasd2";
    
    // Версия Minecraft и Fabric (должны совпадать с твоим клиентом)
    private static final String MC_VERSION = "1.21.4";
    private static final String FABRIC_VERSION = "0.16.14";
    private static final String INSTALLER_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar";

    public static Process installAndLaunch(Path gameDir, Object ignoredSession, Consumer<String> status) throws Exception {
        // Создаем папку C:\FertDLC, если её нет
        Files.createDirectories(gameDir);

        // Проверяем, установлена ли игра (есть ли папка versions)
        Path versionsDir = gameDir.resolve("versions");
        boolean isInstalled = Files.exists(versionsDir) && Files.exists(versionsDir.resolve(MC_VERSION));

        if (!isInstalled) {
            status.accept("Скачивание и установка Minecraft + Fabric (это может занять время)...");
            downloadAndRunFabricInstaller(gameDir, status);
        }

        status.accept("Подготовка к запуску FertDlc...");


        // 1. Ищем Java (нужна Java 21 для версии 1.21.4)
        String javaPath = "C:\\Program Files\\Java\\jdk-21\\bin\\javaw.exe"; 
        if (!Files.exists(Path.of(javaPath))) {
            javaPath = "javaw";
        }

        // 2. Ищем папку с Fabric версией
        Path fabricVersionDir = null;
        try (var stream = Files.list(versionsDir)) {
            fabricVersionDir = stream
                .filter(p -> p.getFileName().toString().contains("fabric-loader") && p.getFileName().toString().contains(MC_VERSION))
                .findFirst().orElse(null);
        }

        if (fabricVersionDir == null) {
            throw new IOException("Не найдена установленная версия Fabric в папке " + versionsDir);
        }

        String versionName = fabricVersionDir.getFileName().toString();
        Path fabricJar = fabricVersionDir.resolve(versionName + ".jar");
        Path vanillaJar = versionsDir.resolve(MC_VERSION).resolve(MC_VERSION + ".jar");

        // 3. Собираем Classpath
        List<String> cpList = new ArrayList<>();
        cpList.add(fabricJar.toString());
        if (Files.exists(vanillaJar)) {
            cpList.add(vanillaJar.toString());
        }
        
        Path librariesDir = gameDir.resolve("libraries");
        if (Files.exists(librariesDir)) {
            try (var stream = Files.walk(librariesDir)) {
                stream.filter(p -> p.toString().endsWith(".jar")).forEach(p -> cpList.add(p.toString()));
            }
        }
        
        String classpath = String.join(";", cpList);

        // 4. Формируем аргументы запуска
        List<String> args = new ArrayList<>();
        args.add(javaPath);
        args.add("-Xmx4G");
        
        Path nativesDir = versionsDir.resolve(MC_VERSION).resolve("natives-windows");
        args.add("-Djava.library.path=" + nativesDir.toString());
        
        args.add("-cp");
        args.add(classpath);
        
        args.add("net.fabricmc.loader.impl.launch.knot.KnotClient");
        
        args.add("--username");
        args.add(NICKNAME);
        args.add("--version");
        args.add(MC_VERSION);
        args.add("--gameDir");
        args.add(gameDir.toString());
        args.add("--assetsDir");
        args.add(gameDir.resolve("assets").toString());
        args.add("--assetIndex");
        args.add(MC_VERSION);
        args.add("--uuid");
        args.add(UUID.nameUUIDFromBytes(NICKNAME.getBytes()).toString().replace("-", ""));
        args.add("--accessToken");
        args.add("0"); 
        args.add("--userType");
        args.add("legacy"); 

        status.accept("Запуск игры...");
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);
        
        Path logFile = gameDir.resolve("FertDLC").resolve("minecraft-launch.log");
        Files.createDirectories(logFile.getParent());
        pb.redirectOutput(logFile.toFile());
        
        return pb.start();
    }

    // Метод скачивания и запуска установщика Fabric
    private static void downloadAndRunFabricInstaller(Path gameDir, Consumer<String> status) throws Exception {
        Path installerPath = gameDir.resolve("fabric-installer.jar");
        try (InputStream bundledInstaller = MinecraftInstaller.class.getResourceAsStream("/installer/fabric-installer.jar")) {
            if (bundledInstaller != null) {
                Path temporaryInstaller = gameDir.resolve("fabric-installer.jar.tmp");
                try {
                    Files.copy(bundledInstaller, temporaryInstaller, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temporaryInstaller, installerPath, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporaryInstaller);
                }
            }
        }

        if (!Files.exists(installerPath)) {
            status.accept("Скачивание установщика Fabric...");
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(INSTALLER_URL)).GET().build();
            HttpResponse<Path> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofFile(installerPath));
            } catch (java.net.ConnectException | java.net.http.HttpTimeoutException exception) {
                Files.deleteIfExists(installerPath);
                throw new IOException("Не удалось подключиться к maven.fabricmc.net для скачивания установщика.", exception);
            }
            if (response.statusCode() != 200) {
                Files.deleteIfExists(installerPath);
                throw new IOException("Не удалось скачать установщик Fabric. Код: " + response.statusCode());
            }
        }

        status.accept("Установка Minecraft и Fabric...");
        // Ищем Java для запуска установщика
        String javaPath = "C:\\Program Files\\Java\\jdk-21\\bin\\java.exe";
        if (!Files.exists(Path.of(javaPath))) {
            javaPath = "java";
        }

        ProcessBuilder pb = new ProcessBuilder(
            javaPath, "-jar", installerPath.toString(),
            "client", "-dir", gameDir.toString(),
            "-mcversion", MC_VERSION,
            "-loader", FABRIC_VERSION,
            "-noprofile"
        );
        pb.inheritIO(); // Чтобы видеть прогресс установки в консоли
        Process p = pb.start();
        int exitCode = p.waitFor();
        
        if (exitCode != 0) {
            throw new IOException("Установщик Fabric завершился с ошибкой. Код: " + exitCode);
        }
        
        // Удаляем установщик после использования (опционально)
        Files.deleteIfExists(installerPath);
    }
}