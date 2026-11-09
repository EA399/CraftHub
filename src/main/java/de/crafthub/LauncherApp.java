package de.crafthub;

import de.crafthub.MicrosoftAuthenticator.DeviceCode;
import de.crafthub.MicrosoftAuthenticator.MinecraftAccount;
import de.crafthub.MinecraftApi.MinecraftVersion;
import de.crafthub.MinecraftApi.ModResult;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LauncherApp {
    private static final float LEFT_ALIGNMENT = 0.0f;
    private static final Color BACKGROUND = new Color(14, 17, 24);
    private static final Color SIDEBAR = new Color(18, 22, 31);
    private static final Color SURFACE = new Color(27, 32, 43);
    private static final Color SURFACE_LIGHT = new Color(24, 54, 51);
    private static final Color TEXT = new Color(242, 245, 249);
    private static final Color MUTED = new Color(159, 173, 193);
    private static final Color ACCENT = new Color(31, 216, 151);
    private static final Color LINE = new Color(48, 57, 74);

    private final MinecraftApi minecraftApi = new MinecraftApi();
    private final MicrosoftAuthenticator authenticator = new MicrosoftAuthenticator();
    private final InstanceRepository repository;
    private final JPanel pageContainer = new JPanel(new BorderLayout());
    private final JTextField globalSearchField = new JTextField();
    private final Map<String, JButton> navigationButtons = new LinkedHashMap<>();
    private final JLabel footerStatus = label("Verbinde mit Mojang ...", 12, MUTED);
    private final JLabel accountSummary = label("Nicht angemeldet", 12, MUTED);
    private final JButton accountButton = new CraftButton("Mit Microsoft anmelden");
    private JFrame frame;
    private JDialog loginDialog;
    private AtomicBoolean loginCancelled;
    private MinecraftAccount account;
    private List<MinecraftVersion> versions = List.of();
    private String currentPage = "instances";
    private String pendingModSearch = "";

    private LauncherApp() throws IOException {
        String appData = System.getenv("APPDATA");
        Path root = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".crafthub")
                : Path.of(appData, "CraftHub");
        repository = new InstanceRepository(root);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new LauncherApp().show();
            } catch (IOException exception) {
                JOptionPane.showMessageDialog(null, exception.getMessage(), "CraftHub konnte nicht starten",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void show() {
        setSystemLookAndFeel();
        frame = new JFrame("CraftHub");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(980, 680));
        frame.setSize(1180, 780);
        frame.setIconImage(IconGenerator.createImage(64));
        frame.setLocationRelativeTo(null);
        frame.setContentPane(buildShell());
        showPage("instances");
        frame.setVisible(true);
        refreshVersions();
    }

    private JPanel buildShell() {
        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(BACKGROUND);
        JPanel sidebar = new JPanel();
        sidebar.setBackground(SIDEBAR);
        sidebar.setBorder(new EmptyBorder(28, 20, 20, 20));
        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel brand = label("CraftHub", 25, TEXT);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD));
        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brandRow.setOpaque(false);
        brandRow.add(new JLabel(new ImageIcon(IconGenerator.createImage(28))));
        brandRow.add(brand);
        sidebar.add(brandRow);
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(label("MINECRAFT WORKSHOP", 10, MUTED));
        sidebar.add(Box.createVerticalStrut(34));
        sidebar.add(navButton("Übersicht", "home"));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(navButton("Instanzen", "instances"));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(navButton("Mods suchen", "mods"));
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(label("KONTO", 10, MUTED));
        sidebar.add(Box.createVerticalStrut(9));
        accountSummary.setAlignmentX(LEFT_ALIGNMENT);
        sidebar.add(accountSummary);
        sidebar.add(Box.createVerticalStrut(8));
        accountButton.setAlignmentX(LEFT_ALIGNMENT);
        styleButton(accountButton, true);
        accountButton.addActionListener(event -> {
            if (account == null) {
                startLogin();
            } else {
                account = null;
                accountSummary.setText("Nicht angemeldet");
                accountButton.setText("Mit Microsoft anmelden");
                setStatus("Abgemeldet");
                showPage(currentPage);
            }
        });
        sidebar.add(accountButton);
        sidebar.add(Box.createVerticalStrut(20));
        sidebar.add(label("CRAFT HUB   /   1.0.0", 10, MUTED));

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BACKGROUND);
        content.add(buildTopBar(), BorderLayout.NORTH);
        content.add(pageContainer, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(SIDEBAR);
        footer.setBorder(new EmptyBorder(10, 22, 10, 22));
        footer.add(footerStatus, BorderLayout.WEST);
        JLabel footerHint = label("Mojang + Modrinth", 11, MUTED);
        footer.add(footerHint, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        shell.add(sidebar, BorderLayout.WEST);
        shell.add(content, BorderLayout.CENTER);
        return shell;
    }

    private JPanel buildTopBar() {
        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBackground(BACKGROUND);
        topBar.setBorder(new EmptyBorder(20, 28, 14, 28));
        globalSearchField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        globalSearchField.setForeground(TEXT);
        globalSearchField.setCaretColor(TEXT);
        globalSearchField.setBackground(SURFACE);
        globalSearchField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
                new EmptyBorder(10, 12, 10, 12)));
        globalSearchField.putClientProperty("JTextField.placeholderText", "Mods auf Modrinth suchen …");
        JButton search = textButton("Suchen");
        search.setForeground(TEXT);
        search.setBackground(SURFACE);
        search.addActionListener(event -> {
            pendingModSearch = globalSearchField.getText().strip();
            showPage("mods");
        });
        globalSearchField.addActionListener(event -> search.doClick());
        topBar.add(globalSearchField, BorderLayout.CENTER);
        topBar.add(search, BorderLayout.EAST);
        return topBar;
    }

    private JButton navButton(String title, String page) {
        JButton button = new CraftButton(title);
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.setPreferredSize(new Dimension(185, 42));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        styleButton(button, false);
        button.setBackground(SIDEBAR);
        navigationButtons.put(page, button);
        button.addActionListener(event -> showPage(page));
        return button;
    }

    private void showPage(String page) {
        currentPage = page;
        navigationButtons.forEach((key, button) -> {
            boolean selected = key.equals(page);
            button.setBackground(selected ? SURFACE_LIGHT : SIDEBAR);
            button.setForeground(selected ? ACCENT : TEXT);
        });
        pageContainer.removeAll();
        pageContainer.add(switch (page) {
            case "instances" -> instancesPage();
            case "mods" -> modsPage();
            default -> homePage();
        }, BorderLayout.CENTER);
        pageContainer.revalidate();
        pageContainer.repaint();
    }

    private JPanel homePage() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(BACKGROUND);
        body.setBorder(new EmptyBorder(30, 34, 30, 34));
        body.add(pageHeading("Dein Minecraft, dein Setup", "Versionen, Profile und Mods an einem Ort."));
        body.add(Box.createVerticalStrut(22));

        JPanel hero = new JPanel(new BorderLayout(24, 10));
        hero.setBackground(SURFACE_LIGHT);
        hero.setBorder(new EmptyBorder(27, 30, 27, 30));
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
        JPanel heroText = new JPanel();
        heroText.setOpaque(false);
        heroText.setLayout(new BoxLayout(heroText, BoxLayout.Y_AXIS));
        heroText.add(label("MODS   ·   PROFILE   ·   MINECRAFT", 11, ACCENT));
        heroText.add(Box.createVerticalStrut(12));
        JLabel heroTitle = label("Dein nächstes\nLieblings-Setup.", 29, TEXT);
        heroTitle.setFont(heroTitle.getFont().deriveFont(Font.BOLD));
        heroText.add(heroTitle);
        heroText.add(Box.createVerticalStrut(8));
        heroText.add(label("Modpacks starten mit einem guten Profil.", 13, MUTED));
        hero.add(heroText, BorderLayout.CENTER);
        JPanel heroAction = new JPanel();
        heroAction.setOpaque(false);
        heroAction.setLayout(new BoxLayout(heroAction, BoxLayout.Y_AXIS));
        JButton create = new CraftButton("Profil erstellen");
        styleButton(create, true);
        create.addActionListener(event -> createInstance());
        heroAction.add(create);
        heroAction.add(Box.createVerticalStrut(12));
        heroAction.add(label("SPIELSTART: NOCH NICHT ENTHALTEN", 10, MUTED));
        hero.add(heroAction, BorderLayout.EAST);
        body.add(hero);
        body.add(Box.createVerticalStrut(24));

        List<GameInstance> instances = safeInstances();
        JPanel stats = new JPanel(new GridLayout(1, 3, 12, 0));
        stats.setOpaque(false);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 94));
        stats.add(statCard("PROFILE", Integer.toString(instances.size()), "Lokal gespeichert"));
        stats.add(statCard("MINECRAFT", versions.isEmpty() ? "..." : versions.get(0).id(), "Letzter Release"));
        stats.add(statCard("KONTO", account == null ? "Offline" : account.username(),
                account == null ? "Nicht angemeldet" : "Minecraft Java verifiziert"));
        body.add(stats);
        body.add(Box.createVerticalStrut(26));
        JPanel sectionHeader = new JPanel(new BorderLayout());
        sectionHeader.setOpaque(false);
        sectionHeader.add(label("Deine Instanzen", 18, TEXT), BorderLayout.WEST);
        JButton allProfiles = textButton("Alle Instanzen anzeigen");
        allProfiles.addActionListener(event -> showPage("instances"));
        sectionHeader.add(allProfiles, BorderLayout.EAST);
        sectionHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        body.add(sectionHeader);
        body.add(Box.createVerticalStrut(12));
        if (instances.isEmpty()) {
            body.add(emptyState("Noch keine Instanzen", "Erstelle eine Instanz und wähle Minecraft-Version und Loader."));
        } else {
            for (GameInstance instance : instances.stream().limit(3).toList()) {
                body.add(instanceRow(instance, false));
                body.add(Box.createVerticalStrut(8));
            }
        }
        return scroll(body);
    }

    private JPanel instancesPage() {
        JPanel body = pageBody();
        GradientPanel banner = new GradientPanel(new Color(20, 72, 60), new Color(30, 39, 61));
        banner.setLayout(new BorderLayout(18, 0));
        banner.setBorder(new EmptyBorder(20, 22, 20, 22));
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 106));
        JPanel bannerText = new JPanel();
        bannerText.setOpaque(false);
        bannerText.setLayout(new BoxLayout(bannerText, BoxLayout.Y_AXIS));
        JLabel title = label("Instanzen", 25, TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        bannerText.add(title);
        bannerText.add(Box.createVerticalStrut(4));
        bannerText.add(label("Minecraft-Profile getrennt verwalten und kopieren", 12, MUTED));
        banner.add(bannerText, BorderLayout.CENTER);
        JButton create = new CraftButton("+  Neue Instanz");
        styleButton(create, true);
        create.addActionListener(event -> createInstance());
        banner.add(create, BorderLayout.EAST);
        body.add(banner);
        body.add(Box.createVerticalStrut(16));
        List<GameInstance> instances = safeInstances();
        if (instances.isEmpty()) {
            body.add(emptyState("Deine Liste ist noch leer", "Erstelle ein Profil, um Version und Mod-Loader festzulegen."));
        } else {
            instances.forEach(instance -> {
                body.add(instanceRow(instance, true));
                body.add(Box.createVerticalStrut(9));
            });
        }
        return scroll(body);
    }

    private JPanel modsPage() {
        JPanel body = pageBody();
        body.add(pageHeading("Mods entdecken", "Finde passende Mods für deine Minecraft-Instanz."));
        body.add(Box.createVerticalStrut(18));
        List<GameInstance> instances = safeInstances();
        if (instances.isEmpty()) {
            body.add(emptyState("Erst eine Instanz erstellen", "Für Mod-Suche und Installation brauchst du eine Instanz."));
            return scroll(body);
        }

        JComboBox<GameInstance> profilePicker = new JComboBox<>(instances.toArray(GameInstance[]::new));
        styleCombo(profilePicker);
        JTextField query = new JTextField();
        query.setText(pendingModSearch);
        query.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        query.setBackground(SURFACE);
        query.setForeground(TEXT);
        query.setCaretColor(TEXT);
        query.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
                new EmptyBorder(10, 12, 10, 12)));
        query.putClientProperty("JTextField.placeholderText", "Mods auf Modrinth suchen …");
        JButton search = new CraftButton("Suchen");
        styleButton(search, true);
        JPanel form = new JPanel(new GridLayout(2, 1, 0, 10));
        form.setOpaque(false);
        form.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
        form.add(profilePicker);
        JPanel searchRow = new JPanel(new BorderLayout(10, 0));
        searchRow.setOpaque(false);
        searchRow.add(query, BorderLayout.CENTER);
        searchRow.add(search, BorderLayout.EAST);
        form.add(searchRow);
        body.add(form);
        body.add(Box.createVerticalStrut(12));
        JLabel resultsStatus = label("Wähle eine Instanz und suche nach Mods.", 12, MUTED);
        resultsStatus.setAlignmentX(LEFT_ALIGNMENT);
        body.add(resultsStatus);
        body.add(Box.createVerticalStrut(10));
        JPanel results = new JPanel();
        results.setOpaque(false);
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        body.add(results);

        Runnable searchAction = () -> {
            GameInstance selected = (GameInstance) profilePicker.getSelectedItem();
            if (selected == null) {
                return;
            }
            if (selected.loader() == GameInstance.Loader.VANILLA) {
                results.removeAll();
                results.add(emptyState("Vanilla unterstützt keine Mods", "Erstelle eine Instanz mit Fabric, Forge, NeoForge oder Quilt."));
                results.revalidate();
                resultsStatus.setText("Ein Mod-Loader ist erforderlich.");
                return;
            }
            String searchQuery = query.getText().strip();
            resultsStatus.setText("Suche auf Modrinth ...");
            search.setEnabled(false);
            new SwingWorker<List<ModResult>, Void>() {
                @Override
                protected List<ModResult> doInBackground() throws Exception {
                    return minecraftApi.searchMods(searchQuery, selected);
                }

                @Override
                protected void done() {
                    search.setEnabled(true);
                    try {
                        List<ModResult> mods = get();
                        results.removeAll();
                        if (mods.isEmpty()) {
                            results.add(emptyState("Keine passenden Mods gefunden", "Versuche einen anderen Suchbegriff."));
                        } else {
                            for (ModResult mod : mods) {
                                results.add(modRow(mod, selected, resultsStatus, search));
                                results.add(Box.createVerticalStrut(8));
                            }
                        }
                        resultsStatus.setText(mods.size() + " passende Mods für " + selected.version()
                                + " / " + selected.loader().label());
                        results.revalidate();
                        results.repaint();
                        setStatus("Modrinth-Suche abgeschlossen");
                    } catch (Exception exception) {
                        showError(resultsStatus, "Suche fehlgeschlagen", exception);
                    }
                }
            }.execute();
        };
        search.addActionListener(event -> searchAction.run());
        query.addActionListener(event -> searchAction.run());
        profilePicker.addActionListener(event -> {
            results.removeAll();
            results.revalidate();
            results.repaint();
            resultsStatus.setText("Instanz gewählt. Suche nach passenden Mods.");
        });
        if (!pendingModSearch.isBlank()) {
            pendingModSearch = "";
            searchAction.run();
        }
        return scroll(body);
    }

    private JPanel settingsPage() {
        JPanel body = pageBody();
        body.add(pageHeading("Einstellungen", "Lokale Daten und Kontostatus."));
        body.add(Box.createVerticalStrut(22));
        body.add(infoCard("Profilspeicher", repository.root().toString()));
        body.add(Box.createVerticalStrut(10));
        body.add(infoCard("Konto", account == null ? "Nicht angemeldet" : account.username() + " / " + account.uuid()));
        body.add(Box.createVerticalStrut(10));
        body.add(infoCard("Spielstart", "Noch nicht verfügbar. Microsoft-Login braucht eine eigene Entra-App-ID."));
        return scroll(body);
    }

    private JPanel instanceRow(GameInstance instance, boolean showDelete) {
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setBackground(SURFACE);
        row.setBorder(new EmptyBorder(13, 15, 13, 15));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
        JLabel icon = new JLabel(new ImageIcon(IconGenerator.createImage(44)));
        row.add(icon, BorderLayout.WEST);
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel name = label(instance.name(), 15, TEXT);
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        details.add(name);
        details.add(Box.createVerticalStrut(5));
        details.add(label("Minecraft " + instance.version() + "  ·  " + instance.loader().label()
                + "  ·  " + safeModCount(instance) + " Mods", 12, MUTED));
        row.add(details, BorderLayout.CENTER);
        if (showDelete) {
            JButton copy = textButton("Kopieren");
            copy.addActionListener(event -> {
                try {
                    GameInstance duplicate = repository.copy(instance.id());
                    setStatus("Instanz " + duplicate.name() + " kopiert");
                    showPage(currentPage);
                } catch (IOException exception) {
                    showError(footerStatus, "Instanz konnte nicht kopiert werden", exception);
                }
            });
            JButton openFolder = textButton("Ordner öffnen");
            openFolder.addActionListener(event -> openInstanceFolder(instance));
            JButton delete = textButton("Löschen");
            delete.setForeground(new Color(255, 112, 121));
            delete.addActionListener(event -> {
                int answer = JOptionPane.showConfirmDialog(frame,
                        "Instanz „" + instance.name() + "“ und ihre Dateien wirklich löschen?",
                        "Instanz löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (answer == JOptionPane.YES_OPTION) {
                    try {
                        repository.delete(instance.id());
                        setStatus("Instanz gelöscht");
                        showPage(currentPage);
                    } catch (IOException exception) {
                        showError(footerStatus, "Instanz konnte nicht gelöscht werden", exception);
                    }
                }
            });
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.setOpaque(false);
            actions.add(copy);
            actions.add(openFolder);
            actions.add(delete);
            row.add(actions, BorderLayout.EAST);
        }
        return row;
    }

    private long safeModCount(GameInstance instance) {
        try {
            return repository.modCount(instance);
        } catch (IOException exception) {
            return 0;
        }
    }

    private void openInstanceFolder(GameInstance instance) {
        try {
            if (!Desktop.isDesktopSupported()) {
                setStatus("Instanzordner: " + repository.instanceDirectory(instance));
                return;
            }
            Desktop.getDesktop().open(repository.instanceDirectory(instance).toFile());
        } catch (Exception exception) {
            showError(footerStatus, "Ordner konnte nicht geöffnet werden", exception);
        }
    }

    private JPanel modRow(ModResult mod, GameInstance instance, JLabel status, JButton searchButton) {
        JPanel row = new JPanel(new BorderLayout(12, 8));
        row.setBackground(SURFACE);
        row.setBorder(new EmptyBorder(14, 16, 14, 14));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 116));
        JPanel description = new JPanel();
        description.setOpaque(false);
        description.setLayout(new BoxLayout(description, BoxLayout.Y_AXIS));
        JLabel title = label(mod.title(), 14, TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        description.add(title);
        description.add(Box.createVerticalStrut(5));
        String summary = mod.description().replaceAll("\\s+", " ").strip();
        if (summary.length() > 125) {
            summary = summary.substring(0, 122) + "...";
        }
        description.add(label(summary, 11, MUTED));
        description.add(Box.createVerticalStrut(5));
        description.add(label(String.format(Locale.ROOT, "%,d Downloads", mod.downloads()), 10, ACCENT));
        JButton install = new CraftButton("Installieren");
        styleButton(install, true);
        install.addActionListener(event -> {
            install.setEnabled(false);
            status.setText("Lade " + mod.title() + " herunter ...");
            searchButton.setEnabled(false);
            new SwingWorker<Path, Void>() {
                @Override
                protected Path doInBackground() throws Exception {
                    return minecraftApi.installMod(mod, instance, repository.modsDirectory(instance));
                }

                @Override
                protected void done() {
                    install.setEnabled(true);
                    searchButton.setEnabled(true);
                    try {
                        status.setText("Installiert: " + get().getFileName());
                        setStatus(mod.title() + " installiert in " + instance.name());
                    } catch (Exception exception) {
                        showError(status, "Installation fehlgeschlagen", exception);
                    }
                }
            }.execute();
        });
        row.add(description, BorderLayout.CENTER);
        row.add(install, BorderLayout.EAST);
        return row;
    }

    private JPanel statCard(String title, String value, String detail) {
        JPanel card = new JPanel();
        card.setBackground(SURFACE);
        card.setBorder(new EmptyBorder(13, 15, 12, 15));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(label(title, 10, MUTED));
        card.add(Box.createVerticalStrut(4));
        JLabel valueLabel = label(value, 19, TEXT);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD));
        card.add(valueLabel);
        card.add(Box.createVerticalStrut(2));
        card.add(label(detail, 10, MUTED));
        return card;
    }

    private JPanel infoCard(String title, String value) {
        JPanel card = new JPanel(new BorderLayout(10, 6));
        card.setBackground(SURFACE);
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        card.add(label(title.toUpperCase(Locale.ROOT), 10, MUTED), BorderLayout.NORTH);
        card.add(label(value, 13, TEXT), BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        return card;
    }

    private JPanel emptyState(String title, String description) {
        JPanel empty = new JPanel();
        empty.setBackground(SURFACE);
        empty.setBorder(new EmptyBorder(24, 20, 24, 20));
        empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
        JLabel heading = label(title, 15, TEXT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        empty.add(heading);
        empty.add(Box.createVerticalStrut(6));
        JLabel detail = label(description, 12, MUTED);
        detail.setAlignmentX(LEFT_ALIGNMENT);
        empty.add(detail);
        empty.setAlignmentX(LEFT_ALIGNMENT);
        empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        return empty;
    }

    private void createInstance() {
        if (versions.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Die Minecraft-Versionen werden noch geladen.",
                    "Noch nicht bereit", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextField name = new JTextField("Mein Modpack", 18);
        JComboBox<MinecraftVersion> version = new JComboBox<>(versions.toArray(MinecraftVersion[]::new));
        version.setEditable(true);
        styleCombo(version);
        JComboBox<GameInstance.Loader> loader = new JComboBox<>(GameInstance.Loader.values());
        styleCombo(loader);
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 7));
        form.add(label("Profilname", 12, TEXT));
        form.add(name);
        form.add(label("Minecraft-Version", 12, TEXT));
        form.add(version);
        form.add(label("Mod-Loader", 12, TEXT));
        form.add(loader);
        int answer = JOptionPane.showConfirmDialog(frame, form, "Neues Profil", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        try {
                GameInstance created = repository.create(name.getText(), selectedVersion(version),
                    (GameInstance.Loader) loader.getSelectedItem());
            setStatus("Profil " + created.name() + " erstellt");
            showPage(currentPage);
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(frame, exception.getMessage(), "Profil konnte nicht erstellt werden",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private String selectedVersion(JComboBox<MinecraftVersion> selector) {
        Object selected = selector.getSelectedItem();
        String version = selected instanceof MinecraftVersion minecraftVersion
                ? minecraftVersion.id()
                : selected == null ? "" : selected.toString().strip();
        return versions.stream().filter(item -> item.id().equals(version)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Bitte eine offizielle Minecraft-Release-Version auswählen."))
                .id();
    }

    private void startLogin() {
        accountButton.setEnabled(false);
        accountButton.setText("Verbinde ...");
        setStatus("Microsoft-Anmeldung wird vorbereitet");
        AtomicBoolean cancelled = new AtomicBoolean(false);
        loginCancelled = cancelled;
        new SwingWorker<DeviceCode, Void>() {
            @Override
            protected DeviceCode doInBackground() throws Exception {
                return authenticator.requestDeviceCode();
            }

            @Override
            protected void done() {
                try {
                    DeviceCode deviceCode = get();
                    showDeviceCode(deviceCode, cancelled);
                } catch (Exception exception) {
                    accountButton.setEnabled(true);
                    accountButton.setText("Microsoft anmelden");
                    showError(footerStatus, "Anmeldung konnte nicht gestartet werden", exception);
                }
            }
        }.execute();
    }

    private void showDeviceCode(DeviceCode deviceCode, AtomicBoolean cancelled) {
        loginDialog = new JDialog(frame, "Mit Microsoft anmelden", false);
        loginDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel();
        content.setBackground(BACKGROUND);
        content.setBorder(new EmptyBorder(20, 22, 18, 22));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(label("Microsoft-Konto bestätigen", 17, TEXT));
        content.add(Box.createVerticalStrut(9));
        content.add(label("CraftHub speichert weder Passwort noch Anmeldetoken.", 12, MUTED));
        content.add(Box.createVerticalStrut(17));
        JButton openBrowser = new CraftButton("Im Browser öffnen");
        styleButton(openBrowser, true);
        openBrowser.addActionListener(event -> openVerificationPage(deviceCode.verificationUri()));
        openBrowser.setAlignmentX(LEFT_ALIGNMENT);
        content.add(openBrowser);
        content.add(Box.createVerticalStrut(12));
        content.add(label("Diesen Code eingeben:", 12, MUTED));
        content.add(Box.createVerticalStrut(5));
        JTextField code = new JTextField(deviceCode.userCode());
        code.setEditable(false);
        code.setHorizontalAlignment(SwingConstants.CENTER);
        code.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
        code.setBackground(SURFACE);
        code.setForeground(ACCENT);
        code.setBorder(new EmptyBorder(12, 10, 12, 10));
        content.add(code);
        content.add(Box.createVerticalStrut(12));
        content.add(label("Warte auf Bestätigung …", 12, MUTED));
        content.add(Box.createVerticalStrut(17));
        JButton cancel = textButton("Abbrechen");
        cancel.setAlignmentX(LEFT_ALIGNMENT);
        cancel.addActionListener(event -> {
            cancelled.set(true);
            loginDialog.dispose();
        });
        content.add(cancel);
        loginDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (loginCancelled == cancelled) {
                    cancelled.set(true);
                }
            }
        });
        loginDialog.setContentPane(content);
        loginDialog.pack();
        loginDialog.setLocationRelativeTo(frame);
        loginDialog.setVisible(true);

        new SwingWorker<MinecraftAccount, Void>() {
            @Override
            protected MinecraftAccount doInBackground() throws Exception {
                return authenticator.completeLogin(deviceCode, cancelled);
            }

            @Override
            protected void done() {
                loginDialog.dispose();
                accountButton.setEnabled(true);
                try {
                    account = get();
                    accountSummary.setText(account.username());
                    accountButton.setText("Abmelden");
                    setStatus("Minecraft-Konto " + account.username() + " bestätigt");
                    showPage(currentPage);
                } catch (Exception exception) {
                    accountButton.setText("Mit Microsoft anmelden");
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    if (!(cause instanceof IOException && "Anmeldung abgebrochen.".equals(cause.getMessage()))) {
                        showError(footerStatus, "Anmeldung fehlgeschlagen", exception);
                    } else {
                        setStatus("Anmeldung abgebrochen");
                    }
                }
            }
        }.execute();
    }

    private void openVerificationPage(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                setStatus("Öffne im Browser: " + url);
            }
        } catch (Exception exception) {
            setStatus("Browser konnte nicht geöffnet werden: " + url);
        }
    }

    private void refreshVersions() {
        new SwingWorker<List<MinecraftVersion>, Void>() {
            @Override
            protected List<MinecraftVersion> doInBackground() throws Exception {
                return minecraftApi.fetchVersions();
            }

            @Override
            protected void done() {
                try {
                    versions = get();
                    setStatus(versions.size() + " offizielle Minecraft-Releases geladen");
                    if ("home".equals(currentPage)) {
                        showPage("home");
                    }
                } catch (Exception exception) {
                    showError(footerStatus, "Minecraft-Versionen nicht erreichbar", exception);
                }
            }
        }.execute();
    }

    private JPanel pageHeading(String title, String subtitle) {
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel titleLabel = label(title, 25, TEXT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        heading.add(titleLabel);
        heading.add(Box.createVerticalStrut(5));
        heading.add(label(subtitle, 13, MUTED));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        return heading;
    }

    private JPanel pageBody() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(BACKGROUND);
        body.setBorder(new EmptyBorder(30, 34, 30, 34));
        return body;
    }

    private JPanel scroll(JPanel body) {
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private List<GameInstance> safeInstances() {
        try {
            return repository.list();
        } catch (IOException exception) {
            setStatus("Profile konnten nicht gelesen werden: " + exception.getMessage());
            return List.of();
        }
    }

    private void showError(JLabel target, String prefix, Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        target.setText(prefix + ": " + cause.getMessage());
        setStatus(prefix);
    }

    private void setStatus(String message) {
        footerStatus.setText(message);
    }

    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    private static JButton textButton(String text) {
        JButton button = new CraftButton(text);
        button.setForeground(MUTED);
        button.setBackground(SURFACE);
        button.setBorder(new EmptyBorder(7, 9, 7, 9));
        button.setFocusPainted(false);
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return button;
    }

    private static void styleButton(JButton button, boolean primary) {
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setForeground(primary ? new Color(20, 30, 22) : TEXT);
        button.setBackground(primary ? ACCENT : SURFACE);
        button.setBorder(new EmptyBorder(10, 14, 10, 14));
        button.setFocusPainted(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    }

    private static void styleCombo(JComboBox<?> combo) {
        combo.setBackground(SURFACE);
        combo.setForeground(TEXT);
        combo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        combo.setBorder(BorderFactory.createLineBorder(LINE));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        combo.setRenderer((list, value, index, selected, focused) -> {
            JLabel item = label(value == null ? "" : value.toString(), 13, TEXT);
            item.setOpaque(true);
            item.setBackground(selected ? SURFACE_LIGHT : SURFACE);
            item.setBorder(new EmptyBorder(7, 9, 7, 9));
            return item;
        });
    }

    private static void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            UIManager.put("control", BACKGROUND);
            UIManager.put("text", TEXT);
            UIManager.put("nimbusBase", SIDEBAR);
            UIManager.put("nimbusBlueGrey", SURFACE);
            UIManager.put("nimbusLightBackground", SURFACE);
            UIManager.put("nimbusFocus", ACCENT);
        } catch (Exception ignored) {
            UIManager.put("Panel.background", BACKGROUND);
        }
    }

    private static final class CraftButton extends JButton {
        private CraftButton(String text) {
            super(text);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D background = (Graphics2D) graphics.create();
            background.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = isEnabled() ? getBackground() : SURFACE;
            if (isEnabled() && getModel().isPressed()) {
                fill = fill.darker();
            } else if (isEnabled() && getModel().isRollover()) {
                fill = fill.brighter();
            }
            background.setColor(fill);
            background.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            background.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class GradientPanel extends JPanel {
        private final Color start;
        private final Color end;

        private GradientPanel(Color start, Color end) {
            this.start = start;
            this.end = end;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D gradient = (Graphics2D) graphics.create();
            gradient.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gradient.setPaint(new java.awt.GradientPaint(0, 0, start, getWidth(), getHeight(), end));
            gradient.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            gradient.dispose();
            super.paintComponent(graphics);
        }
    }
}