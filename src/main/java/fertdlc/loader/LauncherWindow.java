package fertdlc.loader;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.GradientPaint;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.CardLayout;

final class LauncherWindow {
    private static final Color BACKGROUND = new Color(12, 15, 21);
    private static final Color SURFACE = new Color(24, 28, 37);
    private static final Color SURFACE_LIGHT = new Color(31, 37, 48);
    private static final Color TEXT = new Color(239, 244, 250);
    private static final Color MUTED = new Color(145, 157, 173);
    private static final Color ACCENT = new Color(139, 92, 246);
    private static final Color ICE = new Color(196, 181, 253);
    private static final Font BODY = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font BODY_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font TITLE = new Font("Segoe UI", Font.BOLD, 25);
    private static final String LOGIN_CARD = "login";
    private static final String DASHBOARD_CARD = "dashboard";

    private static JFrame frame;
    private static CardLayout cardLayout;
    private static JPanel cards;
    private static JPanel dashboardPanel;
    private static CardLayout pageLayout;
    private static JPanel pageCards;
    private static JButton homeNavigationButton;
    private static JButton versionsNavigationButton;
    private static JButton settingsNavigationButton;
    private static JButton adminNavigationButton;
    private static JLabel loginStatus;
    private static JLabel dashboardStatus;
    private static JLabel statusBadge;
    private static JTextArea eventLog;
    private static JLabel profileName;
    private static JLabel localProfileName;
    private static JProgressBar progress;
    private static JButton startButton;
    private static String verifiedKey;
    private static boolean verifiedAdmin;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Properties settings = loadSettings();

    private LauncherWindow() {
    }

    static void showWindow() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        frame = new JFrame("FertDLC");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 560));
        frame.setSize(1000, 620);
        frame.setLocationRelativeTo(null);
        Image avatar = loadAvatar();
        if (avatar != null) {
            frame.setIconImage(avatar);
        }

        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);
        cards.setBackground(BACKGROUND);
        cards.add(createLoginScreen(), LOGIN_CARD);
        frame.setContentPane(cards);
        frame.setVisible(true);
    }

    private static JPanel createLoginScreen() {
        JPanel root = new JPanel(new java.awt.GridBagLayout());
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(24, 24, 24, 24));
        JPanel loginForm = createLoginForm();
        loginForm.setPreferredSize(new Dimension(430, 520));
        root.add(loginForm);
        return root;
    }

    private static JPanel createLoginForm() {
        SurfacePanel panel = new SurfacePanel(new Color(13, 16, 22), 14);
        panel.setLayout(new BorderLayout());

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(30, 32, 24, 32));
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JLabel avatar = imageLabel(42);
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        form.add(Box.createVerticalGlue());
        form.add(avatar);
        form.add(Box.createVerticalStrut(20));

        JLabel title = label("Добро пожаловать", TITLE, TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        form.add(title);
        JLabel subtitle = label("Введи ключ авторизации, чтобы войти", BODY, MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        form.add(Box.createVerticalStrut(7));
        form.add(subtitle);
        form.add(Box.createVerticalStrut(28));

        JLabel keyLabel = label("КЛЮЧ ДОСТУПА", new Font("Segoe UI", Font.BOLD, 10), MUTED);
        keyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(keyLabel);
        form.add(Box.createVerticalStrut(8));

        JPasswordField keyInput = new JPasswordField();
        styleTextField(keyInput);
        keyInput.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        keyInput.setPreferredSize(new Dimension(360, 46));
        keyInput.setToolTipText("Ключ FertDLC");
        keyInput.setText(LoaderApp.savedLicenseKey());
        form.add(keyInput);
        form.add(Box.createVerticalStrut(14));

        JButton loginButton = button("ВОЙТИ", ACCENT, Color.WHITE);
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        loginButton.addActionListener(event -> authenticate(keyInput, loginButton));
        keyInput.addActionListener(event -> authenticate(keyInput, loginButton));
        form.add(loginButton);

        loginStatus = label("Ключ проверяется сервером FertDLC", BODY, MUTED);
        loginStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        form.add(Box.createVerticalStrut(16));
        form.add(loginStatus);
        form.add(Box.createVerticalGlue());
        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel createWelcomePanel() {
        PhotoPanel panel = new PhotoPanel(loadWelcomeBackground(), 18);
        panel.setLayout(new BorderLayout());
        panel.setBorder(new EmptyBorder(24, 26, 24, 26));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel brand = label("FERTDLC  /  MINECRAFT 1.21.4", BODY_BOLD, TEXT);
        top.add(brand, BorderLayout.WEST);
        JLabel online = label("FABRIC CLIENT", new Font("Segoe UI", Font.BOLD, 11), ICE);
        top.add(online, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(Box.createVerticalGlue());
        center.add(Box.createVerticalStrut(36));

        SurfacePanel feature = new SurfacePanel(new Color(13, 17, 25, 224), 14);
        feature.setLayout(new BorderLayout(14, 0));
        feature.setBorder(new EmptyBorder(13, 14, 13, 14));
        feature.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        feature.setAlignmentX(Component.CENTER_ALIGNMENT);
        feature.add(imageLabel(48), BorderLayout.WEST);
        JPanel featureCopy = new JPanel();
        featureCopy.setOpaque(false);
        featureCopy.setLayout(new BoxLayout(featureCopy, BoxLayout.Y_AXIS));
        featureCopy.add(Box.createVerticalGlue());
        featureCopy.add(label("FertDLC Client", BODY_BOLD, TEXT));
        featureCopy.add(Box.createVerticalStrut(5));
        featureCopy.add(label("Fabric  ·  Minecraft 1.21.4", BODY, MUTED));
        featureCopy.add(Box.createVerticalGlue());
        feature.add(featureCopy, BorderLayout.CENTER);
        center.add(feature);
        center.add(Box.createVerticalGlue());

        JPanel caption = new JPanel();
        caption.setOpaque(false);
        caption.setLayout(new BoxLayout(caption, BoxLayout.Y_AXIS));
        JLabel headline = label("Your next session starts here.", new Font("Segoe UI", Font.BOLD, 23), TEXT);
        JLabel detail = label("Verify your key, sign in with Microsoft, and launch Fabric 1.21.4 directly.", BODY, TEXT);
        caption.add(headline);
        caption.add(Box.createVerticalStrut(8));
        caption.add(detail);
        caption.add(Box.createVerticalStrut(4));
        caption.add(label("Microsoft sign-in opens in your browser; the game session stays yours.", BODY, new Color(205, 211, 220)));
        panel.add(caption, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel createDashboard() {
        GradientPanel root = new GradientPanel(BACKGROUND, new Color(16, 20, 28), 0);
        root.setLayout(new BorderLayout(12, 0));
        root.setBorder(new EmptyBorder(10, 14, 12, 14));
        JPanel workspace = new JPanel(new BorderLayout(0, 12));
        workspace.setOpaque(false);

        SurfacePanel header = new SurfacePanel(SURFACE, 14);
        header.setLayout(new BorderLayout());
        header.setBorder(new EmptyBorder(9, 18, 9, 16));
        header.setPreferredSize(new Dimension(1, 58));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        brand.add(imageLabel(38));
        brand.add(label("FertDLC", new Font("Segoe UI", Font.BOLD, 19), TEXT));
        header.add(brand, BorderLayout.WEST);
        SurfacePanel accessBadge = new SurfacePanel(new Color(31, 44, 42), 10);
        accessBadge.setLayout(new FlowLayout(FlowLayout.CENTER, 9, 8));
        accessBadge.add(label("●", BODY_BOLD, new Color(115, 214, 160)));
        accessBadge.add(label(verifiedAdmin ? "ADMIN · ДОСТУП" : "Ключ подтверждён", BODY_BOLD, TEXT));
        header.add(accessBadge, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        PhotoPanel hero = new PhotoPanel(loadWelcomeBackground(), 14);
        hero.setLayout(new BorderLayout());
        hero.setBorder(new EmptyBorder(16, 18, 17, 18));
        JPanel heroTop = new JPanel(new BorderLayout());
        heroTop.setOpaque(false);
        heroTop.add(label("◈  MINECRAFT 1.21.4", BODY_BOLD, TEXT), BorderLayout.WEST);
        heroTop.add(label("FABRIC CLIENT", new Font("Segoe UI", Font.BOLD, 11), ICE), BorderLayout.EAST);
        hero.add(heroTop, BorderLayout.NORTH);
        JPanel heroBottom = new JPanel();
        heroBottom.setOpaque(false);
        heroBottom.setLayout(new BoxLayout(heroBottom, BoxLayout.Y_AXIS));
        heroBottom.add(label("FertDLC  ·  Fabric 1.21.4", BODY_BOLD, TEXT));
        heroBottom.add(Box.createVerticalStrut(5));
        heroBottom.add(label("Клиент, Fabric и игровые файлы подготовятся в выбранной папке, затем игра запустится напрямую.", BODY, new Color(219, 224, 232)));
        hero.add(heroBottom, BorderLayout.SOUTH);
        center.add(hero, BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(10, 0));
        controls.setOpaque(false);
        SurfacePanel localProfile = new SurfacePanel(SURFACE, 12);
        localProfile.setLayout(new BorderLayout(10, 0));
        localProfile.setBorder(new EmptyBorder(10, 14, 10, 14));
        localProfile.add(label("●", BODY_BOLD, ICE), BorderLayout.WEST);
        JPanel localProfileText = new JPanel();
        localProfileText.setOpaque(false);
        localProfileText.setLayout(new BoxLayout(localProfileText, BoxLayout.Y_AXIS));
        localProfileText.add(label("ЛОКАЛЬНЫЙ ПРОФИЛЬ", new Font("Segoe UI", Font.BOLD, 10), MUTED));
        localProfileText.add(Box.createVerticalStrut(4));
        localProfileName = label(localNickname(), BODY_BOLD, TEXT);
        localProfileText.add(localProfileName);
        localProfile.add(localProfileText, BorderLayout.CENTER);
        controls.add(localProfile, BorderLayout.CENTER);

        SurfacePanel version = new SurfacePanel(SURFACE, 12);
        version.setLayout(new BorderLayout(0, 3));
        version.setBorder(new EmptyBorder(6, 10, 6, 10));
        version.add(label("КАТАЛОГ · ДОСТУПНАЯ СБОРКА", new Font("Segoe UI", Font.BOLD, 9), MUTED), BorderLayout.NORTH);
        JPanel releaseDetails = new JPanel();
        releaseDetails.setOpaque(false);
        releaseDetails.setLayout(new BoxLayout(releaseDetails, BoxLayout.Y_AXIS));
        releaseDetails.add(label("FertDLC 1.21.4", BODY_BOLD, TEXT));
        releaseDetails.add(label("Fabric 0.16.14 · стабильная", new Font("Segoe UI", Font.PLAIN, 11), MUTED));
        version.add(releaseDetails, BorderLayout.CENTER);
        version.setPreferredSize(new Dimension(245, 58));
        controls.add(version, BorderLayout.EAST);
        center.add(controls, BorderLayout.SOUTH);

        pageLayout = new CardLayout();
        pageCards = new JPanel(pageLayout);
        pageCards.setOpaque(false);
        pageCards.add(center, "home");
        pageCards.add(createVersionsPage(), "versions");
        pageCards.add(createSettingsPage(), "settings");
        if (verifiedAdmin) {
            pageCards.add(createAdminPage(), "admin");
        }

        JPanel bottomPanel = new JPanel(new BorderLayout(12, 8));
        bottomPanel.setOpaque(false);

        statusBadge = label("● Ожидание", BODY_BOLD, MUTED);
        statusBadge.setOpaque(true);
        statusBadge.setBackground(new Color(25, 29, 36));
        statusBadge.setBorder(new EmptyBorder(8, 10, 8, 10));
        statusBadge.setPreferredSize(new Dimension(120, 34));
        bottomPanel.add(statusBadge, BorderLayout.WEST);

        eventLog = new JTextArea();
        eventLog.setEditable(false);
        eventLog.setOpaque(false);
        eventLog.setBorder(new EmptyBorder(8, 10, 8, 10));
        eventLog.setLineWrap(true);
        eventLog.setWrapStyleWord(true);
        eventLog.setForeground(TEXT);
        eventLog.setFont(BODY);
        eventLog.setText("ПОСЛЕ ПРОВЕРКИ КЛЮЧА:\n1. В браузере откроется Microsoft sign-in.\n2. Проверится Minecraft Java Edition profile.\n3. Загрузятся недостающие Minecraft 1.21.4/Fabric файлы и ресурсы.\n4. FertDLC установится в выбранную папку mods.\n5. Игра запустится напрямую через Fabric.");

        JScrollPane logScroll = new JScrollPane(eventLog);
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(43, 50, 62)));
        logScroll.setPreferredSize(new Dimension(1, 90));
        logScroll.getViewport().setOpaque(false);
        logScroll.setOpaque(false);
        bottomPanel.add(logScroll, BorderLayout.CENTER);

        SurfacePanel footer = new SurfacePanel(SURFACE, 12);
        footer.setLayout(new BorderLayout(12, 0));
        footer.setBorder(new EmptyBorder(9, 12, 9, 12));
        footer.setPreferredSize(new Dimension(1, 62));
        JPanel profile = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        profile.setOpaque(false);
        profile.add(imageLabel(38));
        JPanel profileCopy = new JPanel();
        profileCopy.setOpaque(false);
        profileCopy.setLayout(new BoxLayout(profileCopy, BoxLayout.Y_AXIS));
        profileName = label(localNickname(), BODY_BOLD, TEXT);
        profileCopy.add(profileName);
        profileCopy.add(Box.createVerticalStrut(3));
        profileCopy.add(label("Имя отображается только в loader", BODY, MUTED));
        profile.add(profileCopy);
        footer.add(profile, BorderLayout.WEST);

        JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footerActions.setOpaque(false);
        progress = new JProgressBar();
        progress.setBorderPainted(false);
        progress.setStringPainted(false);
        progress.setForeground(ACCENT);
        progress.setBackground(SURFACE_LIGHT);
        progress.setPreferredSize(new Dimension(260, 8));
        footerActions.add(progress);
        JButton downloadJarButton = button("Скачать JAR", SURFACE_LIGHT, TEXT);
        downloadJarButton.setPreferredSize(new Dimension(132, 40));
        downloadJarButton.setToolTipText("Сохранить Fabric-мод FertDLC отдельным файлом");
        downloadJarButton.addActionListener(event -> downloadClientJar());
        footerActions.add(downloadJarButton);
        startButton = button("▶  Играть", ACCENT, Color.WHITE);
        startButton.setPreferredSize(new Dimension(150, 42));
        startButton.addActionListener(event -> installAndLaunch());
        footerActions.add(startButton);
        footer.add(footerActions, BorderLayout.EAST);

        dashboardStatus = label("Ожидание запуска", BODY, MUTED);
        footer.add(dashboardStatus, BorderLayout.CENTER);
        JPanel lower = new JPanel(new BorderLayout(0, 8));
        lower.setOpaque(false);
        lower.add(bottomPanel, BorderLayout.CENTER);
        lower.add(footer, BorderLayout.SOUTH);
        workspace.add(header, BorderLayout.NORTH);
        workspace.add(pageCards, BorderLayout.CENTER);
        workspace.add(lower, BorderLayout.SOUTH);
        root.add(createSidebar(), BorderLayout.WEST);
        root.add(workspace, BorderLayout.CENTER);
        showPage("home");
        return root;
    }

    private static JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 18));
        sidebar.setBackground(new Color(13, 16, 22));
        sidebar.setBorder(new EmptyBorder(16, 12, 14, 12));
        sidebar.setPreferredSize(new Dimension(194, 1));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        brand.setOpaque(false);
        brand.add(imageLabel(38));
        JPanel brandCopy = new JPanel();
        brandCopy.setOpaque(false);
        brandCopy.setLayout(new BoxLayout(brandCopy, BoxLayout.Y_AXIS));
        brandCopy.add(label("FertDLC", BODY_BOLD, TEXT));
        brandCopy.add(label("CLIENT 1.21.4", new Font("Segoe UI", Font.BOLD, 9), MUTED));
        brand.add(brandCopy);
        sidebar.add(brand, BorderLayout.NORTH);

        JPanel navigation = new JPanel();
        navigation.setOpaque(false);
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        homeNavigationButton = button("Главная", SURFACE, TEXT);
        homeNavigationButton.addActionListener(event -> showPage("home"));
        addNavigationButton(navigation, homeNavigationButton);

        versionsNavigationButton = button("Версии", SURFACE, TEXT);
        versionsNavigationButton.addActionListener(event -> showPage("versions"));
        addNavigationButton(navigation, versionsNavigationButton);

        settingsNavigationButton = button("Настройки", SURFACE, TEXT);
        settingsNavigationButton.addActionListener(event -> showPage("settings"));
        addNavigationButton(navigation, settingsNavigationButton);

        if (verifiedAdmin) {
            adminNavigationButton = button("Админ-панель", SURFACE, TEXT);
            adminNavigationButton.addActionListener(event -> showPage("admin"));
            addNavigationButton(navigation, adminNavigationButton);
        }
        sidebar.add(navigation, BorderLayout.CENTER);

        JButton changeKey = button("Сменить ключ", SURFACE_LIGHT, TEXT);
        changeKey.addActionListener(event -> {
            verifiedKey = null;
            verifiedAdmin = false;
            loginStatus.setForeground(MUTED);
            loginStatus.setText("Введите ключ доступа");
            cardLayout.show(cards, LOGIN_CARD);
        });
        sidebar.add(changeKey, BorderLayout.SOUTH);
        return sidebar;
    }

    private static void addNavigationButton(JPanel navigation, JButton button) {
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        navigation.add(button);
        navigation.add(Box.createVerticalStrut(7));
    }

    private static JPanel createVersionsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setOpaque(false);
        page.setBorder(new EmptyBorder(10, 4, 10, 4));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(label("Выбор версии", new Font("Segoe UI", Font.BOLD, 22), TEXT));
        heading.add(Box.createVerticalStrut(5));
        heading.add(label("Доступные релизы FertDLC", BODY, MUTED));
        page.add(heading, BorderLayout.NORTH);

        SurfacePanel release = new SurfacePanel(SURFACE, 12);
        release.setLayout(new BorderLayout(14, 0));
        release.setBorder(new EmptyBorder(16, 18, 16, 18));
        JPanel releaseInfo = new JPanel();
        releaseInfo.setOpaque(false);
        releaseInfo.setLayout(new BoxLayout(releaseInfo, BoxLayout.Y_AXIS));
        releaseInfo.add(label("FertDLC 1.21.4", BODY_BOLD, TEXT));
        releaseInfo.add(Box.createVerticalStrut(6));
        releaseInfo.add(label("Fabric Loader 0.16.14 · стабильный релиз", BODY, MUTED));
        releaseInfo.add(Box.createVerticalStrut(12));
        releaseInfo.add(label("Устанавливается в папку mods выбранного профиля Minecraft.", BODY, TEXT));
        release.add(releaseInfo, BorderLayout.CENTER);
        JButton select = button("Выбрать", ACCENT, Color.WHITE);
        select.addActionListener(event -> showPage("home"));
        release.add(select, BorderLayout.EAST);
        page.add(release, BorderLayout.CENTER);
        return page;
    }

    private static void showPage(String page) {
        if (pageCards == null || pageLayout == null) {
            return;
        }
        pageLayout.show(pageCards, page);
        setNavigationAppearance(homeNavigationButton, "home".equals(page));
        setNavigationAppearance(versionsNavigationButton, "versions".equals(page));
        setNavigationAppearance(settingsNavigationButton, "settings".equals(page));
        setNavigationAppearance(adminNavigationButton, "admin".equals(page));
    }

    private static void setNavigationAppearance(JButton button, boolean active) {
        if (button != null) {
            button.setBackground(active ? new Color(45, 34, 68) : SURFACE);
            button.setForeground(active ? TEXT : MUTED);
        }
    }

    private static void downloadClientJar() {
        JFileChooser chooser = new JFileChooser(Path.of(System.getProperty("user.home"), "Downloads").toFile());
        chooser.setDialogTitle("Сохранить клиент FertDLC");
        chooser.setSelectedFile(new java.io.File("fertdlc-1.21.4.jar"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path destination = chooser.getSelectedFile().toPath();
        setStatus("WORK", "Сохраняю JAR клиента...");
        appendLog("Скачивание JAR клиента: " + destination);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                LoaderApp.exportClientJar(destination);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    setStatus("SUCCESS", "JAR клиента сохранён: " + destination);
                    appendLog("[OK] JAR клиента сохранён.");
                } catch (Exception exception) {
                    setStatus("ERROR", errorMessage(exception));
                    appendLog("[ERROR] Не удалось сохранить JAR: " + errorMessage(exception));
                }
            }
        }.execute();
    }

    private static void authenticate(JPasswordField keyInput, JButton loginButton) {
        char[] chars = keyInput.getPassword();
        String key = new String(chars).trim();
        Arrays.fill(chars, '\0');
        if (key.isEmpty()) {
            loginStatus.setText("Введите ключ доступа.");
            return;
        }

        loginButton.setEnabled(false);
        loginStatus.setForeground(ICE);
        loginStatus.setText("Проверка ключа...");
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return LoaderApp.validateKey(key);
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                try {
                    verifiedKey = key;
                    verifiedAdmin = get();
                    showDashboard();
                    setStatus("SUCCESS", "Ключ подтверждён. Нажми «Играть», чтобы запустить Minecraft.");
                } catch (Exception exception) {
                    loginStatus.setForeground(new Color(255, 126, 126));
                    loginStatus.setText(errorMessage(exception));
                }
            }
        }.execute();
    }

    private static void showDashboard() {
        if (dashboardPanel != null) {
            cards.remove(dashboardPanel);
        }
        dashboardPanel = createDashboard();
        cards.add(dashboardPanel, DASHBOARD_CARD);
        cardLayout.show(cards, DASHBOARD_CARD);
        frame.revalidate();
        frame.repaint();
    }

    private static void installAndLaunch() {
        String directory = settings.getProperty("gameDir", defaultGameDirectory());
        Path gameDir;
        try {
            gameDir = Path.of(directory);
        } catch (RuntimeException exception) {
            setStatus("ERROR", "Путь к папке игры некорректен.");
            appendLog("Ошибка: путь к папке игры некорректен.");
            return;
        }

        startButton.setEnabled(false);
        startButton.setText("Подготовка...");
        progress.setIndeterminate(true);
        progress.setStringPainted(true);
        progress.setString("Microsoft sign-in");
        setStatus("WORK", "Проверка ключа и авторизация Minecraft...");
        appendLog("[1/5] Проверка ключа FertDLC...");
        new SwingWorker<Process, Void>() {
            @Override
            protected Process doInBackground() throws Exception {
                return LoaderApp.installAndLaunch(verifiedKey, gameDir, message -> SwingUtilities.invokeLater(() -> {
                    dashboardStatus.setText(message);
                    progress.setString(message);
                    appendLog(message);
                }));
            }

            @Override
            protected void done() {
                progress.setIndeterminate(false);
                progress.setStringPainted(false);
                startButton.setEnabled(true);
                startButton.setText("▶  Играть");
                try {
                    Process process = get();
                    if (!process.isAlive()) {
                        throw new IOException("Процесс Minecraft сразу завершился. Проверь лог FertDLC/minecraft-launch.log.");
                    }
                    setStatus("SUCCESS", "Minecraft 1.21.4 запущен через Fabric.");
                    appendLog("[OK] Процесс Minecraft запущен напрямую через Fabric 1.21.4.");
                    appendLog("Лог запуска: " + gameDir.resolve("FertDLC").resolve("minecraft-launch.log"));
                    progress.setValue(100);
                    process.onExit().thenAccept(exited -> SwingUtilities.invokeLater(() -> {
                        if (exited.exitValue() != 0) {
                            setStatus("ERROR", "Minecraft завершился с кодом " + exited.exitValue() + ". Открой лог запуска.");
                            appendLog("[ERROR] Код завершения Minecraft: " + exited.exitValue());
                        } else {
                            dashboardStatus.setText("Minecraft завершил работу.");
                            appendLog("Minecraft завершил работу.");
                        }
                    }));
                } catch (Exception exception) {
                    setStatus("ERROR", errorMessage(exception));
                    appendLog("[ERROR] " + errorMessage(exception));
                    progress.setValue(0);
                }
            }
        }.execute();
    }

    private static void setStatus(String state, String message) {
        if (statusBadge == null) {
            return;
        }
        switch (state) {
            case "SUCCESS" -> {
                statusBadge.setForeground(new Color(138, 233, 163));
                statusBadge.setText("● Готово");
                statusBadge.setBackground(new Color(15, 49, 32));
            }
            case "ERROR" -> {
                statusBadge.setForeground(new Color(255, 145, 145));
                statusBadge.setText("● Ошибка");
                statusBadge.setBackground(new Color(62, 21, 26));
            }
            default -> {
                statusBadge.setForeground(new Color(140, 210, 255));
                statusBadge.setText("● Выполняется");
                statusBadge.setBackground(new Color(18, 25, 38));
            }
        }
        dashboardStatus.setText(message);
    }

    private static void appendLog(String text) {
        if (eventLog == null) {
            return;
        }
        String current = eventLog.getText();
        String next = current.isBlank() ? text : current + "\n" + text;
        eventLog.setText(next);
        eventLog.setCaretPosition(eventLog.getDocument().getLength());
    }

    private static JPanel createSettingsPage() {
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(BACKGROUND);
        content.setBorder(new EmptyBorder(20, 22, 18, 22));
        content.add(label("Настройки", new Font("Segoe UI", Font.BOLD, 22), TEXT), BorderLayout.NORTH);

        JPanel settingsFields = new JPanel();
        settingsFields.setOpaque(false);
        settingsFields.setLayout(new BoxLayout(settingsFields, BoxLayout.Y_AXIS));
        JTextField nicknameInput = new JTextField(settings.getProperty("localNickname", ""));
        styleTextField(nicknameInput);
        nicknameInput.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        settingsFields.add(label("Имя в интерфейсе loader (не меняет игровой аккаунт)", BODY, MUTED));
        settingsFields.add(Box.createVerticalStrut(6));
        settingsFields.add(nicknameInput);
        settingsFields.add(Box.createVerticalStrut(14));

        JPanel pathRow = new JPanel(new BorderLayout(10, 0));
        pathRow.setOpaque(false);
        JTextField pathInput = new JTextField(settings.getProperty("gameDir", defaultGameDirectory()));
        styleTextField(pathInput);
        pathRow.add(pathInput, BorderLayout.CENTER);
        JButton browse = button("Обзор", SURFACE_LIGHT, TEXT);
        browse.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser(pathInput.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                pathInput.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        pathRow.add(browse, BorderLayout.EAST);
        settingsFields.add(label("Папка игры", BODY, MUTED));
        settingsFields.add(Box.createVerticalStrut(6));
        settingsFields.add(pathRow);
        JScrollPane settingsScroll = new JScrollPane(settingsFields);
        settingsScroll.setBorder(null);
        settingsScroll.setOpaque(false);
        settingsScroll.getViewport().setOpaque(false);
        settingsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        settingsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        content.add(settingsScroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(label("Для запуска нужен Microsoft-аккаунт с Minecraft Java Edition.", BODY, MUTED), BorderLayout.CENTER);
        JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footerActions.setOpaque(false);
        JButton save = button("Сохранить", ACCENT, Color.WHITE);
        save.addActionListener(event -> {
            settings.setProperty("gameDir", pathInput.getText().trim());
            settings.setProperty("localNickname", nicknameInput.getText().trim());
            try {
                saveSettings();
                profileName.setText(localNickname());
                localProfileName.setText(localNickname());
                setStatus("SUCCESS", "Настройки сохранены.");
                showPage("home");
            } catch (IOException exception) {
                setStatus("ERROR", "Не удалось сохранить настройки: " + exception.getMessage());
            }
        });
        footerActions.add(save);
        footer.add(footerActions, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        return content;
    }

    private static JPanel createAdminPage() {
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(BACKGROUND);
        content.setBorder(new EmptyBorder(18, 20, 18, 20));
        JPanel heading = new JPanel(new BorderLayout(0, 4));
        heading.setOpaque(false);
        heading.add(label("Управление лицензиями", new Font("Segoe UI", Font.BOLD, 22), TEXT), BorderLayout.NORTH);
        heading.add(label("FERTDLC ADMIN  ·  Ключи и роли", BODY, MUTED), BorderLayout.SOUTH);
        content.add(heading, BorderLayout.NORTH);

        DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"ID КЛЮЧА", "СОЗДАН", "СТАТУС", "РОЛЬ"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(32);
        table.setFont(BODY);
        table.setForeground(TEXT);
        table.setBackground(SURFACE);
        table.setGridColor(new Color(43, 50, 62));
        table.setSelectionBackground(new Color(28, 76, 103));
        table.setSelectionForeground(Color.WHITE);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer();
        cellRenderer.setBackground(SURFACE);
        cellRenderer.setForeground(TEXT);
        cellRenderer.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        for (int column = 0; column < table.getColumnModel().getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setCellRenderer(cellRenderer);
        }
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setOpaque(true);
        headerRenderer.setBackground(SURFACE_LIGHT);
        headerRenderer.setForeground(MUTED);
        headerRenderer.setFont(new Font("Segoe UI", Font.BOLD, 11));
        headerRenderer.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        table.getTableHeader().setDefaultRenderer(headerRenderer);
        table.getTableHeader().setPreferredSize(new Dimension(1, 36));
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(43, 50, 62)));
        tableScroll.getViewport().setBackground(SURFACE);
        content.add(tableScroll, BorderLayout.CENTER);

        JTextField keyInput = new JTextField();
        styleTextField(keyInput);
        keyInput.setToolTipText("Вставь полный ключ для назначения роли или отзыва");
        JComboBox<String> roleChoice = new JComboBox<>(new String[]{"user", "admin"});
        roleChoice.setFont(BODY_BOLD);
        roleChoice.setForeground(TEXT);
        roleChoice.setBackground(SURFACE_LIGHT);
        JLabel status = label("Готово к управлению ключами", BODY, MUTED);

        JPanel actionArea = new JPanel();
        actionArea.setOpaque(false);
        actionArea.setLayout(new BoxLayout(actionArea, BoxLayout.Y_AXIS));
        JPanel issueRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        issueRow.setOpaque(false);
        JButton issueUser = button("Выдать user", ACCENT, Color.WHITE);
        JButton issueAdmin = button("Выдать admin", new Color(91, 68, 34), new Color(255, 213, 135));
        JButton refresh = button("Обновить", SURFACE_LIGHT, TEXT);
        issueRow.add(issueUser);
        issueRow.add(issueAdmin);
        issueRow.add(refresh);
        actionArea.add(issueRow);
        actionArea.add(Box.createVerticalStrut(10));

        JPanel roleRow = new JPanel(new BorderLayout(8, 0));
        roleRow.setOpaque(false);
        roleRow.add(keyInput, BorderLayout.CENTER);
        JPanel roleControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        roleControls.setOpaque(false);
        JButton copyKey = button("Копировать", SURFACE_LIGHT, TEXT);
        roleControls.add(roleChoice);
        roleControls.add(copyKey);
        JButton setRole = button("Назначить роль", SURFACE_LIGHT, TEXT);
        JButton revoke = button("Отозвать", new Color(126, 48, 54), Color.WHITE);
        roleControls.add(setRole);
        roleControls.add(revoke);
        roleRow.add(roleControls, BorderLayout.EAST);
        actionArea.add(roleRow);
        actionArea.add(Box.createVerticalStrut(8));
        actionArea.add(status);
        content.add(actionArea, BorderLayout.SOUTH);

        copyKey.addActionListener(event -> {
            String key = keyInput.getText().trim();
            if (key.isEmpty()) {
                status.setText("Сначала выбери или создай ключ.");
                return;
            }
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(key), null);
                status.setForeground(MUTED);
                status.setText("Ключ скопирован.");
            } catch (RuntimeException exception) {
                status.setForeground(new Color(255, 126, 126));
                status.setText("Не удалось скопировать ключ.");
            }
        });

        Runnable refreshList = () -> refreshAdminTable(tableModel, status);
        issueUser.addActionListener(event -> runAdminAction(issueUser, status, "Выпускаю ключ...",
            () -> adminRequest("/manage/issue", ""), key -> {
                keyInput.setText(key);
                status.setText("User-ключ создан. Скопируй его и сохрани.");
                refreshList.run();
            }));
        issueAdmin.addActionListener(event -> runAdminAction(issueAdmin, status, "Выпускаю admin-ключ...",
            () -> adminRequest("/manage/issue-admin", ""), key -> {
                keyInput.setText(key);
                roleChoice.setSelectedItem("admin");
                status.setText("Admin-ключ создан. Скопируй его и сохрани.");
                refreshList.run();
            }));
        setRole.addActionListener(event -> {
            String key = keyInput.getText().trim();
            String role = String.valueOf(roleChoice.getSelectedItem());
            if (!key.isEmpty()) {
                runAdminAction(setRole, status, "Обновляю роль...",
                    () -> adminRequest("/manage/set-role", key + "\t" + role), result -> {
                        status.setText("Роль ключа обновлена: " + role + ".");
                        refreshList.run();
                    });
            }
        });
        revoke.addActionListener(event -> {
            String key = keyInput.getText().trim();
            if (!key.isEmpty()) {
                runAdminAction(revoke, status, "Отзываю ключ...",
                    () -> adminRequest("/manage/revoke", key), result -> {
                        keyInput.setText("");
                        status.setText("Ключ отозван.");
                        refreshList.run();
                    });
            }
        });
        refresh.addActionListener(event -> refreshList.run());
        SwingUtilities.invokeLater(refreshList);
        return content;
    }

    private static void refreshAdminTable(DefaultTableModel model, JLabel status) {
        runAdminAction(null, status, "Обновляю список...", () -> adminRequest("/manage/list", ""), body -> {
            model.setRowCount(0);
            if (!body.isBlank()) {
                for (String line : body.split("\\R")) {
                    String[] fields = line.split("\\t", -1);
                    if (fields.length >= 4) {
                        model.addRow(new Object[]{fields[0], fields[1], fields[2], fields[3]});
                    }
                }
            }
            status.setText("Лицензий в списке: " + model.getRowCount());
        });
    }

    private static void runAdminAction(JButton button, JLabel status, String pending, Callable<String> action, Consumer<String> success) {
        if (button != null) {
            button.setEnabled(false);
        }
        status.setForeground(MUTED);
        status.setText(pending);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return action.call();
            }

            @Override
            protected void done() {
                if (button != null) {
                    button.setEnabled(true);
                }
                try {
                    success.accept(get());
                } catch (Exception exception) {
                    status.setForeground(new Color(255, 126, 126));
                    status.setText(errorMessage(exception));
                }
            }
        }.execute();
    }

    private static String adminRequest(String route, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(LoaderApp.apiBase() + route))
            .timeout(Duration.ofSeconds(8))
            .header("Authorization", "Bearer " + verifiedKey)
            .header("Content-Type", "text/plain; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(response.body().isBlank() ? "Admin API error: " + response.statusCode() : response.body());
        }
        return response.body().trim();
    }

    private static Properties loadSettings() {
        Properties values = new Properties();
        Path file = settingsFile();
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                values.load(input);
            } catch (IOException ignored) {
            }
        }
        values.putIfAbsent("gameDir", defaultGameDirectory());
        return values;
    }

    private static String localNickname() {
        String nickname = settings.getProperty("localNickname", "").trim();
        return nickname.isEmpty() ? "Игрок" : nickname;
    }

    private static void saveSettings() throws IOException {
        Path file = settingsFile();
        Files.createDirectories(file.getParent());
        try (var output = Files.newOutputStream(file)) {
            settings.store(output, "FertDLC settings");
        }
    }

    private static Path settingsFile() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
            ? Path.of(System.getProperty("user.home"))
            : Path.of(appData);
        return base.resolve("FertDLC").resolve("settings.properties");
    }

    private static String defaultGameDirectory() {
        try {
            return LoaderApp.minecraftDirectory().toString();
        } catch (IOException exception) {
            return Path.of(System.getProperty("user.home"), ".minecraft").toString();
        }
    }

    private static JLabel imageLabel(int size) {
        Image image = loadAvatar();
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        if (image != null) {
            label.setIcon(new ImageIcon(image.getScaledInstance(size, size, Image.SCALE_SMOOTH)));
        }
        return label;
    }

    private static Image loadAvatar() {
        try (InputStream input = LauncherWindow.class.getResourceAsStream("/dummy-loader.png")) {
            return input == null ? null : ImageIO.read(input);
        } catch (IOException exception) {
            return null;
        }
    }

    private static BufferedImage loadWelcomeBackground() {
        try (InputStream input = LauncherWindow.class.getResourceAsStream("/fertdlc-background.png")) {
            return input == null ? null : ImageIO.read(input);
        } catch (IOException exception) {
            return null;
        }
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static JButton button(String text, Color background, Color foreground) {
        JButton button = new FlatButton(text, background, foreground);
        button.setFont(BODY_BOLD);
        button.setForeground(foreground);
        button.setBackground(background);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        return button;
    }

    private static void styleTextField(JTextField field) {
        field.setFont(BODY);
        field.setForeground(TEXT);
        field.setBackground(SURFACE_LIGHT);
        field.setCaretColor(TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55, 66, 82)),
            BorderFactory.createEmptyBorder(9, 12, 9, 12)
        ));
    }

    private static String errorMessage(Exception exception) {
        Throwable contextual = exception;
        while (contextual != null) {
            if (contextual instanceof IOException && contextual.getCause() != null
                    && contextual.getMessage() != null && !contextual.getMessage().isBlank()) {
                return contextual.getMessage();
            }
            contextual = contextual.getCause();
        }
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof java.net.ConnectException || cause instanceof java.net.http.HttpTimeoutException) {
            return "Сервер ключей недоступен. Запусти key-server и попробуй ещё раз.";
        }
        if (cause instanceof java.net.UnknownHostException) {
            return "Не найден сервер ключей. Проверь адрес FERTDLC_API_BASE.";
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return "Не удалось выполнить операцию.";
        }
        return message;
    }

    private static class SurfacePanel extends JPanel {
        private final Color fill;
        private final int radius;

        SurfacePanel(Color fill, int radius) {
            this.fill = fill;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class FlatButton extends JButton {
        private FlatButton(String text, Color background, Color foreground) {
            super(text);
            setBackground(background);
            setForeground(foreground);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setFocusPainted(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = getBackground();
            if (!isEnabled()) {
                fill = new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 110);
            } else if (getModel().isPressed()) {
                fill = fill.darker();
            } else if (getModel().isRollover()) {
                fill = fill.brighter();
            }
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class PhotoPanel extends SurfacePanel {
        private final BufferedImage image;
        private final int radius;

        PhotoPanel(BufferedImage image, int radius) {
            super(BACKGROUND, radius);
            this.image = image;
            this.radius = radius;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = Math.max(1, getWidth());
            int height = Math.max(1, getHeight());
            RoundRectangle2D clip = new RoundRectangle2D.Float(0, 0, width, height, radius, radius);
            g.setClip(clip);
            if (image != null) {
                double scale = Math.max(width / (double) image.getWidth(), height / (double) image.getHeight());
                int imageWidth = (int) Math.ceil(image.getWidth() * scale);
                int imageHeight = (int) Math.ceil(image.getHeight() * scale);
                int x = (width - imageWidth) / 2;
                int y = (height - imageHeight) / 2;
                g.drawImage(image, x, y, imageWidth, imageHeight, null);
            }
            g.setPaint(new GradientPaint(
                0, 0, new Color(7, 11, 17, 45), 0, height, new Color(7, 11, 17, 190)
            ));
            g.fill(clip);
            g.dispose();
        }
    }

    private static final class GradientPanel extends SurfacePanel {
        private final Color start;
        private final Color end;

        GradientPanel(Color start, Color end, int radius) {
            super(new Color(0, 0, 0, 0), radius);
            this.start = start;
            this.end = end;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float width = Math.max(1, getWidth());
            float height = Math.max(1, getHeight());
            g.setPaint(new LinearGradientPaint(0, 0, width, height, new float[]{0f, 1f}, new Color[]{start, end}));
            g.fill(new RoundRectangle2D.Float(0, 0, width, height, 20, 20));
            g.setColor(new Color(255, 255, 255, 10));
            for (int i = 0; i < 9; i++) {
                int x = (int) (width * (0.12 + i * 0.1));
                int y = (int) (height * ((i * 37 % 83) / 100.0));
                g.fillOval(x, y, 2, 2);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}