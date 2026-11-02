package fertdlc.loader;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;

public final class LoaderApp {
    private static final String API_BASE = System.getenv().getOrDefault("FERTDLC_API_BASE", "http://127.0.0.1:8787");
    private static final String CLIENT_FILENAME = "fertdlc-1.21.4.jar";
    
    // Ссылка на Dropbox с dl=1
    private static final String CLIENT_DOWNLOAD_URL = "https://www.dropbox.com/scl/fi/6u1x39ceeh2ts1c7gdd7q/fertdlc-1.21.4.jar?rlkey=83ms7zl439eygku7di36vahlm&st=13on92ny&dl=1";

    // Папка установки
    private static final String TARGET_DIR = "C:\\FertDLC";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private static final HttpClient DOWNLOAD_HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private LoaderApp() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("The loader requires a desktop environment.");
            return;
        }
        SwingUtilities.invokeLater(LauncherWindow::showWindow);
    }

    private static void showWindow() {
        JFrame frame = new JFrame("FertDLC Loader");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(420, 190));
        frame.setResizable(false);
        try (InputStream icon = LoaderApp.class.getResourceAsStream("/fertdlc-avatar.png")) {
            if (icon != null) {
                frame.setIconImage(ImageIO.read(icon));
            }
        } catch (IOException exception) {
            System.err.println("Could not load FertDLC avatar: " + exception.getMessage());
        }

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
        JLabel title = new JLabel("FertDLC | доступ к клиенту");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 19f));
        JLabel status = new JLabel("Введите ключ доступа");
        JPasswordField keyInput = new JPasswordField();
        keyInput.setPreferredSize(new Dimension(360, 34));
        JButton launch = new JButton("Проверить и открыть Minecraft");
        launch.addActionListener(event -> {
            char[] chars = keyInput.getPassword();
            String key = new String(chars).trim();
            java.util.Arrays.fill(chars, '\0');
            if (key.isEmpty()) {
                status.setText("Введите ключ доступа");
                return;
            }
            launch.setEnabled(false);
            status.setText("Проверка ключа...");
            new Thread(() -> {
                try {
                    Consumer<String> updateStatus = message -> SwingUtilities.invokeLater(() -> status.setText(message));
                    validateAndPrepare(key, updateStatus);
                    SwingUtilities.invokeLater(() -> {
                        status.setText("Ключ подтвержден. Запуск...");
                        launch.setText("Доступ подтвержден");
                    });
                } catch (Exception exception) {
                    SwingUtilities.invokeLater(() -> {
                        status.setText(exception.getMessage());
                        launch.setEnabled(true);
                    });
                }
            }, "fertdlc-loader-auth").start();
        });

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.add(keyInput, BorderLayout.NORTH);
        center.add(status, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.add(launch);
        content.add(title, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);
        frame.setContentPane(content);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        keyInput.requestFocusInWindow();
    }

    static boolean validateKey(String key) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase() + "/validate"))
            .timeout(Duration.ofSeconds(8))
            .header("Content-Type", "text/plain; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(key, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        String result = response.body().trim();
        if (response.statusCode() != 200 || !("valid".equals(result) || "valid:user".equals(result) || "valid:admin".equals(result))) {
            throw new IOException("Ключ недействителен или отозван.");
        }
        return "valid:admin".equals(result);
    }

    static String apiBase() throws IOException {
        String base = API_BASE.trim();
        URI uri;
        try {
            uri = URI.create(base);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Адрес сервера ключей некорректен.", exception);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IOException("Адрес сервера ключей некорректен.");
        }
        boolean localHost = "localhost".equalsIgnoreCase(host)
            || "127.0.0.1".equals(host)
            || "::1".equals(host)
            || "[::1]".equals(host);
        boolean secure = "https".equalsIgnoreCase(scheme);
        boolean localHttp = "http".equalsIgnoreCase(scheme) && localHost;
        if (!secure && !localHttp) {
            throw new IOException("Для удалённого сервера ключей требуется HTTPS.");
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    static Process installAndLaunch(String key, Path gameDir, Consumer<String> statusConsumer) throws Exception {
        if (statusConsumer != null) {
            statusConsumer.accept("Проверяю ключ...");
        }
        
        // validateKey(key); // Раскомментируй, если сервер ключей запущен

        Path targetDirectory = Path.of(TARGET_DIR);
        Path configDir = targetDirectory.resolve("FertDLC");
        Path modsDir = targetDirectory.resolve("mods");
        
        Files.createDirectories(configDir);
        Files.createDirectories(modsDir);
        Files.writeString(configDir.resolve("license.key"), key, StandardCharsets.UTF_8);
        
        Path installedClient = modsDir.resolve(CLIENT_FILENAME);

        if (!Files.exists(installedClient)) {
            if (statusConsumer != null) {
                statusConsumer.accept("Скачивание клиента FertDLC...");
            }
            downloadFile(CLIENT_DOWNLOAD_URL, installedClient);
        }

        Consumer<String> updateStatus = statusConsumer == null ? ignored -> { } : statusConsumer;
        
        return MinecraftInstaller.installAndLaunch(targetDirectory, null, updateStatus);
    }

    private static void downloadFile(String url, Path destination) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(5))
            .GET()
            .build();
        
        HttpResponse<Path> response = DOWNLOAD_HTTP.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        
        if (response.statusCode() != 200) {
            Files.deleteIfExists(destination);
            throw new IOException("Ошибка скачивания: сервер вернул код " + response.statusCode());
        }
        
        if (Files.size(destination) < 10000) { 
             String content = Files.readString(destination);
             if (content.contains("<html") || content.contains("<!DOCTYPE")) {
                 Files.deleteIfExists(destination);
                 throw new IOException("Сервер вернул страницу вместо файла. Проверь ссылку.");
             }
        }
    }

    static void exportClientJar(Path destination) throws IOException {
        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            downloadFile(CLIENT_DOWNLOAD_URL, destination);
        } catch (Exception e) {
            throw new IOException("Не удалось скачать JAR: " + e.getMessage(), e);
        }
    }

    private static void validateAndPrepare(String key, Consumer<String> statusConsumer) throws Exception {
        installAndLaunch(key, minecraftDirectory(), statusConsumer);
    }

    static Path minecraftDirectory() throws IOException {
        return Path.of(TARGET_DIR);
    }

    static String savedLicenseKey() {
        try {
            Path licenseFile = minecraftDirectory().resolve("FertDLC").resolve("license.key");
            return Files.isRegularFile(licenseFile) ? Files.readString(licenseFile, StandardCharsets.UTF_8).trim() : "";
        } catch (IOException exception) {
            return "";
        }
    }
}