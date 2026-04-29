import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;

public class AppFrame extends JFrame {
    private static final String CARD_NAME = "name";
    private static final String CARD_START = "start";
    private static final String CARD_SEND = "send";
    private static final String CARD_RECEIVE = "receive";

    private static final Color APP_BG = new Color(8, 28, 56);
    private static final Color PANEL_BG = new Color(18, 48, 89);
    private static final Color ACCENT = new Color(44, 130, 246);
    private static final Color ACCENT_DARK = new Color(27, 101, 205);
    private static final Color TEXT = new Color(236, 244, 255);
    private static final Color SUBTLE = new Color(176, 198, 230);
    private static final Color BORDER = new Color(56, 92, 145);
    private static final Color INPUT_BG = new Color(14, 38, 73);
    private static final Color INPUT_TEXT = new Color(242, 248, 255);
    private static final Color CHAT_BG = new Color(10, 34, 68);
    private static final Color CHAT_BUBBLE_MINE = new Color(36, 125, 83);
    private static final Color CHAT_BUBBLE_OTHER = new Color(30, 64, 108);
    private static final Color CHAT_BUBBLE_SYSTEM = new Color(58, 88, 128);
    private static final Color CHAT_META = new Color(196, 216, 242);
    private static final Font FONT_H1 = new Font("Segoe UI", Font.BOLD, 30);
    private static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 15);
    private static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 18);

    private final DefaultListModel<DiscoveredPeer> peersModel = new DefaultListModel<>();
    private final JList<DiscoveredPeer> peersList = new JList<>(peersModel);
    private final JTextField nameInputField = new JTextField(24);
    private final JLabel deviceLabel = new JLabel("Name: -");
    private final JLabel ipLabel = new JLabel("Local IP: " + LocalNet.firstLocalIpv4());

    private final JTextArea logArea = new JTextArea();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final JPanel chatWrap = new JPanel(new BorderLayout());

    private final JTextField sendPinField = new JTextField(8);
    private final JLabel sendConnectionLabel = new JLabel("Not connected");
    private final JButton discoverBtn = new JButton("Discover Devices");
    private final JButton connectBtn = new JButton("Connect with PIN");
    private final JButton disconnectBtn = new JButton("Disconnect");
    private JButton continueBtn;

    private final DefaultListModel<FileQueueItem> sendFilesModel = new DefaultListModel<>();
    private final JList<FileQueueItem> sendFilesList = new JList<>(sendFilesModel);
    private final JButton addFilesBtn = new JButton("Add Files");
    private final JButton removeFilesBtn = new JButton("Remove Selected");
    private final JButton sendQueueBtn = new JButton("Send Queue");

    private final JLabel sendCurrentFileLabel = new JLabel("Current file: -");
    private final JLabel sendSpeedLabel = new JLabel("Speed: 0 KB/s");
    private final JLabel sendEncryptLabel = new JLabel("Encryption: 0%");
    private final JLabel sendStatusLabel = new JLabel("Status: Waiting");
    private final JProgressBar sendProgressBar = new JProgressBar(0, 1000);
    private final JProgressBar sendEncryptProgressBar = new JProgressBar(0, 1000);

    private final JLabel receivePinLabel = new JLabel("PIN: -", SwingConstants.CENTER);
    private final JLabel receiveConnectionLabel = new JLabel("Waiting for sender...");
    private final JLabel receiveFolderLabel = new JLabel();
    private final DefaultListModel<String> incomingModel = new DefaultListModel<>();
    private final JList<String> incomingList = new JList<>(incomingModel);
    private final JLabel receiveCurrentFileLabel = new JLabel("Current file: -");
    private final JLabel receiveSpeedLabel = new JLabel("Speed: 0 KB/s");
    private final JLabel receiveDecryptLabel = new JLabel("Decryption: 0%");
    private final JLabel receiveStatusLabel = new JLabel("Status: Waiting");
    private final JProgressBar receiveProgressBar = new JProgressBar(0, 1000);
    private final JProgressBar receiveDecryptProgressBar = new JProgressBar(0, 1000);
    private final JButton regeneratePinBtn = new JButton("Regenerate PIN");

    private final DefaultListModel<ChatMessage> chatModel = new DefaultListModel<>();
    private final JList<ChatMessage> chatList = new JList<>(chatModel);
    private final JTextField chatInput = new JTextField(16);
    private final JButton chatSendBtn = new JButton("Send");

    private final ExecutorService ioPool = Executors.newCachedThreadPool();
    private final AtomicBoolean sendQueueRunning = new AtomicBoolean(false);
    private final Path receiveDir = Paths.get(System.getProperty("user.home"), "Downloads", "P2PTransfer Received");
    private final Map<String, Integer> incomingIndexByName = new HashMap<>();

    private volatile FileQueueItem activeSendItem;
    private volatile String currentDeviceName;

    private PeerServer server;
    private PeerConnection connection;
    private DiscoveryService discoveryService;

    public AppFrame() {
        setTitle("P2P file transfer system");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 740));
        setSize(1160, 780);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(APP_BG);

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);
        applyComponentTheme();

        wireActions();
        goToName();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownResources();
            }
        });
    }

    private JPanel buildTopBar() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(APP_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        JLabel title = new JLabel("P2P file transfer system");
        title.setFont(FONT_H1);
        title.setForeground(TEXT);
        panel.add(title, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setBackground(APP_BG);
        deviceLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        ipLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        deviceLabel.setForeground(TEXT);
        ipLabel.setForeground(SUBTLE);
        right.add(deviceLabel);
        right.add(Box.createHorizontalStrut(10));
        right.add(ipLabel);
        right.add(Box.createHorizontalStrut(10));
        styleDisconnectButton(disconnectBtn);
        disconnectBtn.setEnabled(false);
        right.add(disconnectBtn);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildCenter() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(APP_BG);
        root.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));

        cardPanel.setBackground(APP_BG);
        cardPanel.add(buildNameCard(), CARD_NAME);
        cardPanel.add(buildStartCard(), CARD_START);
        cardPanel.add(buildSendCard(), CARD_SEND);
        cardPanel.add(buildReceiveCard(), CARD_RECEIVE);
        root.add(cardPanel, BorderLayout.CENTER);

        chatWrap.add(buildChatPanel(), BorderLayout.CENTER);
        chatWrap.setPreferredSize(new Dimension(300, 200));
        chatWrap.setVisible(false);
        root.add(chatWrap, BorderLayout.EAST);
        return root;
    }

    private JPanel buildNameCard() {
        JPanel card = baseCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(36, 36, 36, 36)
        ));
        card.setPreferredSize(new Dimension(680, 440));

        JLabel heading = new JLabel("Welcome");
        heading.setFont(FONT_H1);
        heading.setForeground(TEXT);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Please enter your name to continue");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        subtitle.setForeground(SUBTLE);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel special = new JLabel("Fast. Private. Seamless across your LAN.");
        special.setFont(new Font("Segoe UI", Font.BOLD, 17));
        special.setForeground(new Color(151, 210, 255));
        special.setAlignmentX(Component.CENTER_ALIGNMENT);

        nameInputField.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        nameInputField.setMargin(new Insets(10, 12, 10, 12));
        nameInputField.setMaximumSize(new Dimension(440, 48));

        continueBtn = new JButton("Continue");
        stylePrimaryButton(continueBtn);
        continueBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        continueBtn.addActionListener(e -> confirmNameAndContinue());

        card.add(heading);
        card.add(Box.createVerticalStrut(12));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(10));
        card.add(special);
        card.add(Box.createVerticalStrut(20));
        card.add(nameInputField);
        card.add(Box.createVerticalStrut(20));
        card.add(continueBtn);
        card.add(Box.createVerticalGlue());
        return centerInPanel(card);
    }

    private JPanel buildStartCard() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 16, 16));
        panel.setBackground(APP_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(24, 36, 24, 36));

        JButton sendModeBtn = new JButton("Send Files");
        stylePrimaryButton(sendModeBtn);
        sendModeBtn.addActionListener(e -> goToSend());

        JButton receiveModeBtn = new JButton("Receive Files");
        stylePrimaryButton(receiveModeBtn);
        receiveModeBtn.addActionListener(e -> goToReceive());

        panel.add(buildModeCard(
                "Send Files",
                "Discover devices, connect with PIN, then drag and drop one or many files.",
                sendModeBtn
        ));
        panel.add(buildModeCard(
                "Receive Files",
                "Generate PIN and wait for sender. Incoming files save automatically.",
                receiveModeBtn
        ));
        return panel;
    }

    private JPanel buildModeCard(String title, String body, JButton action) {
        JPanel panel = baseCard();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(22, 22, 22, 22)
        ));

        JLabel heading = new JLabel(title);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 24));
        heading.setForeground(TEXT);

        JTextArea info = new JTextArea(body);
        info.setEditable(false);
        info.setWrapStyleWord(true);
        info.setLineWrap(true);
        info.setOpaque(false);
        info.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        info.setForeground(SUBTLE);

        panel.add(heading, BorderLayout.NORTH);
        panel.add(info, BorderLayout.CENTER);
        panel.add(action, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSendCard() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(APP_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        panel.add(buildSendConnectionPanel(), BorderLayout.WEST);
        panel.add(buildSendFilePanel(), BorderLayout.CENTER);
        panel.add(buildSendProgressPanel(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSendConnectionPanel() {
        JPanel panel = baseCard();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(330, 100));
        panel.setBorder(BorderFactory.createCompoundBorder(
                titledBorder("Device Connection"),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        peersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peersList.setCellRenderer(new PeerCellRenderer());
        peersList.setFont(FONT_BODY);
        JScrollPane peersScroll = new JScrollPane(peersList);
        peersScroll.setPreferredSize(new Dimension(290, 280));

        styleActionButton(discoverBtn);
        styleActionButton(connectBtn);
        sendPinField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        sendConnectionLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        sendConnectionLabel.setForeground(SUBTLE);

        panel.add(discoverBtn);
        panel.add(Box.createVerticalStrut(10));
        panel.add(label("Available devices"));
        panel.add(Box.createVerticalStrut(6));
        panel.add(peersScroll);
        panel.add(Box.createVerticalStrut(10));
        panel.add(label("Enter receiver PIN"));
        panel.add(Box.createVerticalStrut(6));
        panel.add(sendPinField);
        panel.add(Box.createVerticalStrut(10));
        panel.add(connectBtn);
        panel.add(Box.createVerticalStrut(8));
        panel.add(sendConnectionLabel);

        JButton back = new JButton("Back");
        styleActionButton(back);
        back.addActionListener(e -> goToStart());
        panel.add(Box.createVerticalStrut(8));
        panel.add(back);
        return panel;
    }

    private JPanel buildSendFilePanel() {
        JPanel panel = baseCard();
        panel.setLayout(new BorderLayout(8, 8));
        panel.setBorder(titledBorder("File List (Drag & Drop)"));

        sendFilesList.setFont(FONT_BODY);
        sendFilesList.setCellRenderer(new FileQueueRenderer());
        sendFilesList.setTransferHandler(new FileDropHandler(this::addFilesToQueue));
        sendFilesList.setDropMode(javax.swing.DropMode.INSERT);
        panel.add(new JScrollPane(sendFilesList), BorderLayout.CENTER);

        JPanel controls = new JPanel();
        controls.setBackground(PANEL_BG);
        styleActionButton(addFilesBtn);
        styleActionButton(removeFilesBtn);
        styleActionButton(sendQueueBtn);
        controls.add(addFilesBtn);
        controls.add(removeFilesBtn);
        controls.add(sendQueueBtn);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSendProgressPanel() {
        JPanel panel = baseCard();
        panel.setLayout(new GridLayout(6, 1, 6, 6));
        panel.setBorder(titledBorder("Transfer Progress"));
        sendCurrentFileLabel.setFont(FONT_BODY);
        sendSpeedLabel.setFont(FONT_BODY);
        sendEncryptLabel.setFont(FONT_BODY);
        sendStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        sendProgressBar.setStringPainted(true);
        sendEncryptProgressBar.setStringPainted(true);
        panel.add(sendCurrentFileLabel);
        panel.add(sendProgressBar);
        panel.add(sendSpeedLabel);
        panel.add(sendEncryptLabel);
        panel.add(sendEncryptProgressBar);
        panel.add(sendStatusLabel);
        return panel;
    }

    private JPanel buildReceiveCard() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(APP_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel left = baseCard();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setPreferredSize(new Dimension(330, 100));
        left.setBorder(BorderFactory.createCompoundBorder(
                titledBorder("Device Connection"),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        receivePinLabel.setFont(new Font("Consolas", Font.BOLD, 30));
        receivePinLabel.setForeground(Color.white);
        receiveConnectionLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        receiveFolderLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        receiveConnectionLabel.setForeground(SUBTLE);
        receiveFolderLabel.setForeground(SUBTLE);

        left.add(label("Share this PIN with sender"));
        left.add(Box.createVerticalStrut(8));
        left.add(receivePinLabel);
        left.add(Box.createVerticalStrut(8));
        left.add(receiveConnectionLabel);
        left.add(Box.createVerticalStrut(8));
        left.add(receiveFolderLabel);
        left.add(Box.createVerticalStrut(12));
        styleActionButton(regeneratePinBtn);
        left.add(regeneratePinBtn);

        JButton back = new JButton("Back");
        styleActionButton(back);
        back.addActionListener(e -> goToStart());
        left.add(Box.createVerticalStrut(8));
        left.add(back);
        panel.add(left, BorderLayout.WEST);

        incomingList.setFont(FONT_BODY);
        incomingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane incomingScroll = new JScrollPane(incomingList);
        incomingScroll.setBorder(incomingFilesBorder());
        panel.add(incomingScroll, BorderLayout.CENTER);

        JPanel progress = baseCard();
        progress.setLayout(new GridLayout(6, 1, 6, 6));
        progress.setBorder(titledBorder("Transfer Progress"));
        receiveCurrentFileLabel.setFont(FONT_BODY);
        receiveSpeedLabel.setFont(FONT_BODY);
        receiveDecryptLabel.setFont(FONT_BODY);
        receiveStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        receiveProgressBar.setStringPainted(true);
        receiveDecryptProgressBar.setStringPainted(true);
        progress.add(receiveCurrentFileLabel);
        progress.add(receiveProgressBar);
        progress.add(receiveSpeedLabel);
        progress.add(receiveDecryptLabel);
        progress.add(receiveDecryptProgressBar);
        progress.add(receiveStatusLabel);
        panel.add(progress, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildChatPanel() {
        JPanel panel = baseCard();
        panel.setLayout(new BorderLayout(8, 8));
        panel.setBorder(titledBorder("Mini Chat"));

        chatList.setCellRenderer(new ChatBubbleRenderer());
        chatList.setBackground(CHAT_BG);
        chatList.setSelectionBackground(CHAT_BG);
        chatList.setSelectionForeground(INPUT_TEXT);
        chatList.setFixedCellHeight(-1);
        chatList.setFocusable(false);
        chatList.setOpaque(true);
        JScrollPane chatScroll = new JScrollPane(chatList);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.getViewport().setBackground(CHAT_BG);
        panel.add(chatScroll, BorderLayout.CENTER);

        JPanel input = new JPanel(new BorderLayout(6, 6));
        input.setBackground(PANEL_BG);
        chatInput.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        chatInput.setEnabled(false);
        chatInput.setBackground(CHAT_BG);
        chatInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        styleChatSendButton(chatSendBtn);
        chatSendBtn.setEnabled(false);
        input.add(chatInput, BorderLayout.CENTER);
        input.add(chatSendBtn, BorderLayout.EAST);
        panel.add(input, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildBottom() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(APP_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        logArea.setEditable(false);
        logArea.setRows(6);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(statusBoxBorder());
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void wireActions() {
        discoverBtn.addActionListener(e -> {
            ensureDiscoveryRunning();
            sendStatus("Waiting");
            log("Discovery started. Searching LAN devices...");
        });
        nameInputField.addActionListener(e -> confirmNameAndContinue());
        sendPinField.addActionListener(e -> onConnectToSelectedPeer());
        connectBtn.addActionListener(e -> onConnectToSelectedPeer());
        addFilesBtn.addActionListener(e -> openFilePicker());
        removeFilesBtn.addActionListener(e -> removeSelectedFiles());
        sendQueueBtn.addActionListener(e -> startSendQueue());
        regeneratePinBtn.addActionListener(e -> startReceiverSession(true));
        chatSendBtn.addActionListener(e -> sendChatFromInput());
        chatInput.addActionListener(e -> sendChatFromInput());
        disconnectBtn.addActionListener(e -> disconnectByUser());
    }

    private void confirmNameAndContinue() {
        String name = nameInputField.getText().trim();
        if (name.isEmpty()) {
            showError("Please enter your name.");
            return;
        }
        currentDeviceName = name;
        deviceLabel.setText("Name: " + currentDeviceName);
        addChatLine("System", "Welcome " + currentDeviceName + " \uD83D\uDC4B");
        goToStart();
    }

    private void goToName() {
        cardLayout.show(cardPanel, CARD_NAME);
        if (continueBtn != null) {
            getRootPane().setDefaultButton(continueBtn);
        }
        chatWrap.setVisible(false);
        disconnectCurrentConnection("Returning to name screen");
        stopHosting();
    }

    private void goToStart() {
        cardLayout.show(cardPanel, CARD_START);
        getRootPane().setDefaultButton(null);
        chatWrap.setVisible(false);
        if (connection == null) {
            stopHosting();
        }
    }

    private void goToSend() {
        if (!validateName()) {
            goToName();
            return;
        }
        cardLayout.show(cardPanel, CARD_SEND);
        getRootPane().setDefaultButton(connectBtn);
        chatWrap.setVisible(true);
        if (connection == null) {
            stopHosting();
            ensureDiscoveryRunning();
            sendConnectionLabel.setText("Not connected");
        } else {
            sendConnectionLabel.setText("Connected to " + connection.getPeerName());
        }
        sendStatus("Waiting");
    }

    private void goToReceive() {
        if (!validateName()) {
            goToName();
            return;
        }
        cardLayout.show(cardPanel, CARD_RECEIVE);
        getRootPane().setDefaultButton(null);
        chatWrap.setVisible(true);
        if (connection == null) {
            ensureDiscoveryRunning();
            startReceiverSession(false);
        } else {
            receiveConnectionLabel.setText("Connected to " + connection.getPeerName());
            receiveStatus("Waiting");
        }
    }

    private void disconnectByUser() {
        PeerConnection active = connection;
        if (active == null) {
            return;
        }
        disconnectCurrentConnection("Disconnected by user");
        log("Disconnected by user.");
    }

    private boolean validateName() {
        return currentDeviceName != null && !currentDeviceName.isBlank();
    }

    private void startReceiverSession(boolean regenerate) {
        if (!validateName()) {
            showError("Please enter your name first.");
            return;
        }
        try {
            if (regenerate) {
                stopHosting();
            }
            if (server == null) {
                server = new PeerServer();
            }
            String pin = SessionCode.generate();
            incomingModel.clear();
            incomingIndexByName.clear();
            receiveProgressBar.setValue(0);
            receiveProgressBar.setString("Waiting");
            receiveDecryptProgressBar.setValue(0);
            receiveDecryptProgressBar.setString("Waiting");
            receiveDecryptLabel.setText("Decryption: 0%");
            receiveConnectionLabel.setText("Waiting for sender...");
            receiveStatusLabel.setText("Status: Waiting");
            receivePinLabel.setText("PIN: " + pin);
            receiveFolderLabel.setText("Save folder: " + receiveDir.toAbsolutePath());
            server.start(pin, currentDeviceName, this::attachConnection, this::log);
            log("Receiver ready on port " + Protocol.PORT + ". PIN: " + pin);
        } catch (IOException e) {
            stopHosting();
            showError("Failed to start receiver: " + e.getMessage());
            log("Receiver start failed: " + e.getMessage());
        }
    }

    private void onConnectToSelectedPeer() {
        String code = sendPinField.getText().trim();
        DiscoveredPeer selected = peersList.getSelectedValue();
        if (selected == null) {
            showError("Select a device from discovery list.");
            return;
        }
        if (!validateName() || code.isEmpty()) {
            showError("Name and PIN are required.");
            return;
        }

        sendConnectionLabel.setText("Connecting to " + selected.displayName() + "...");
        sendStatus("Waiting");
        ioPool.submit(() -> {
            try {
                PeerConnection conn = PeerClient.connect(selected.ipAddress(), selected.port(), code, currentDeviceName);
                attachConnection(conn);
                log("Connected to " + selected.displayName() + " (" + selected.ipAddress() + ")");
                addChatLine("System", "Connected with " + selected.displayName() + " \uD83D\uDD17");
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    sendConnectionLabel.setText("Connection failed");
                    sendStatus("Failed");
                    showError("Connection failed. Please verify PIN and network.\n" + ex.getMessage());
                });
                log("Connection failed: " + ex.getMessage());
            }
        });
    }

    private synchronized void attachConnection(PeerConnection conn) {
        disconnectCurrentConnection("New connection accepted");
        connection = conn;
        connection.setReceiveDirectory(receiveDir);
        connection.setAutoAcceptIncoming(true);
        connection.setListener(new PeerConnection.Listener() {
            @Override
            public void onStatus(String message) {
                log(message);
            }

            @Override
            public void onIncomingFileOffered(String fileName, long sizeBytes) {
                SwingUtilities.invokeLater(() -> {
                    String label = fileName + " (" + PeerConnection.humanSize(sizeBytes) + ") - Waiting";
                    incomingIndexByName.put(fileName, incomingModel.size());
                    incomingModel.addElement(label);
                    receiveStatus("Waiting");
                });
            }

            @Override
            public void onProgress(String fileName, long transferredBytes, long totalBytes, double bytesPerSec, boolean sending) {
                SwingUtilities.invokeLater(() -> {
                    int value = totalBytes == 0 ? 0 : (int) Math.min(1000L, 1000L * transferredBytes / totalBytes);
                    String progressText = String.format("%d%% (%s / %s)",
                            totalBytes == 0 ? 0 : (int) (100L * transferredBytes / totalBytes),
                            PeerConnection.humanSize(transferredBytes),
                            PeerConnection.humanSize(totalBytes));
                    if (sending) {
                        sendProgressBar.setValue(value);
                        sendProgressBar.setString(progressText);
                        sendCurrentFileLabel.setText("Current file: " + fileName);
                        sendSpeedLabel.setText("Speed: " + speedText(bytesPerSec));
                        sendStatus("Sending");
                    } else {
                        receiveProgressBar.setValue(value);
                        receiveProgressBar.setString(progressText);
                        receiveCurrentFileLabel.setText("Current file: " + fileName);
                        receiveSpeedLabel.setText("Speed: " + speedText(bytesPerSec));
                        receiveStatus("Receiving");
                        Integer index = incomingIndexByName.get(fileName);
                        if (index != null && index >= 0 && index < incomingModel.size()) {
                            incomingModel.set(index, fileName + " (" + PeerConnection.humanSize(totalBytes) + ") - "
                                    + (totalBytes == 0 ? 0 : (100L * transferredBytes / totalBytes)) + "%");
                        }
                    }
                });
            }

            @Override
            public void onCryptoProgress(String fileName, long processedBytes, long totalBytes, boolean sending) {
                SwingUtilities.invokeLater(() -> {
                    int value = totalBytes == 0 ? 0 : (int) Math.min(1000L, 1000L * processedBytes / totalBytes);
                    int percent = totalBytes == 0 ? 0 : (int) Math.min(100L, 100L * processedBytes / totalBytes);
                    if (sending) {
                        sendEncryptLabel.setText("Encryption: " + percent + "%");
                        sendEncryptProgressBar.setValue(value);
                        sendEncryptProgressBar.setString(percent + "% Encrypted");
                    } else {
                        receiveDecryptLabel.setText("Decryption: " + percent + "%");
                        receiveDecryptProgressBar.setValue(value);
                        receiveDecryptProgressBar.setString(percent + "% Decrypted");
                    }
                });
            }

            @Override
            public void onFileCompleted(String fileName, Path localPath, boolean sending) {
                SwingUtilities.invokeLater(() -> {
                    if (sending) {
                        sendStatus("Completed");
                        sendProgressBar.setValue(1000);
                        sendProgressBar.setString("100% Completed");
                        sendEncryptLabel.setText("Encryption: 100%");
                        sendEncryptProgressBar.setValue(1000);
                        sendEncryptProgressBar.setString("100% Encrypted");
                        addChatLine("System", "Sent \u2705 " + fileName);
                        if (activeSendItem != null && activeSendItem.path().equals(localPath)) {
                            setFileStatus(activeSendItem, "Completed");
                        }
                    } else {
                        receiveStatus("Completed");
                        receiveProgressBar.setValue(1000);
                        receiveProgressBar.setString("100% Completed");
                        receiveDecryptLabel.setText("Decryption: 100%");
                        receiveDecryptProgressBar.setValue(1000);
                        receiveDecryptProgressBar.setString("100% Decrypted");
                        Integer index = incomingIndexByName.get(fileName);
                        String savedAs = localPath == null ? fileName : localPath.getFileName().toString();
                        if (index != null && index >= 0 && index < incomingModel.size()) {
                            incomingModel.set(index, savedAs + " - Completed");
                        } else {
                            incomingModel.addElement(savedAs + " - Completed");
                        }
                        addChatLine("System", "Received \u2705 " + savedAs);
                    }
                });
            }

            @Override
            public void onChatMessage(String message, boolean incoming) {
                addChatLine(incoming ? conn.getPeerName() : "You", message);
            }

            @Override
            public void onClosed(String reason) {
                SwingUtilities.invokeLater(() -> {
                    sendConnectionLabel.setText("Not connected");
                    receiveConnectionLabel.setText("Waiting for sender...");
                    chatInput.setEnabled(false);
                    chatSendBtn.setEnabled(false);
                    disconnectBtn.setEnabled(false);
                    sendStatus("Failed");
                    receiveStatus("Waiting");
                    addChatLine("System", conn.getPeerName() + " is disconnected.");
                    log("Disconnected: " + reason);
                });
            }
        });
        connection.start();
        SwingUtilities.invokeLater(() -> {
            String text = "Connected to " + conn.getPeerName();
            sendConnectionLabel.setText(text);
            receiveConnectionLabel.setText(text);
            chatInput.setEnabled(true);
            chatSendBtn.setEnabled(true);
            disconnectBtn.setEnabled(true);
            sendStatus("Waiting");
            receiveStatus("Waiting");
            sendProgressBar.setValue(0);
            sendProgressBar.setString("Ready");
            sendEncryptLabel.setText("Encryption: 0%");
            sendEncryptProgressBar.setValue(0);
            sendEncryptProgressBar.setString("Ready");
            receiveProgressBar.setValue(0);
            receiveProgressBar.setString("Ready");
            receiveDecryptLabel.setText("Decryption: 0%");
            receiveDecryptProgressBar.setValue(0);
            receiveDecryptProgressBar.setString("Ready");
            addChatLine("System", "Connection ready. Start transfer or chat \uD83D\uDCAC");
        });
    }

    private void sendChatFromInput() {
        String message = chatInput.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        PeerConnection conn = connection;
        if (conn == null) {
            showError("Connect a peer before sending chat.");
            return;
        }
        chatInput.setText("");
        addChatLine("You", message);
        ioPool.submit(() -> {
            try {
                conn.sendChatMessage(message);
            } catch (IOException e) {
                addChatLine("System", "Failed to send message \u274C");
                log("Chat send failed: " + e.getMessage());
            }
        });
    }

    private void openFilePicker() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] files = chooser.getSelectedFiles();
        List<Path> paths = new ArrayList<>();
        for (File file : files) {
            paths.add(file.toPath());
        }
        addFilesToQueue(paths);
    }

    private void addFilesToQueue(List<Path> paths) {
        int added = 0;
        for (Path path : paths) {
            if (path == null || !Files.isRegularFile(path) || containsPath(path)) {
                continue;
            }
            try {
                long size = Files.size(path);
                sendFilesModel.addElement(new FileQueueItem(path, size, "Waiting"));
                added++;
            } catch (IOException ignored) {
            }
        }
        if (added > 0) {
            sendStatus("Waiting");
            log("Added " + added + " file(s) to queue.");
        }
    }

    private boolean containsPath(Path path) {
        for (int i = 0; i < sendFilesModel.size(); i++) {
            if (sendFilesModel.get(i).path().equals(path)) {
                return true;
            }
        }
        return false;
    }

    private void removeSelectedFiles() {
        List<FileQueueItem> selected = sendFilesList.getSelectedValuesList();
        if (selected.isEmpty()) {
            return;
        }
        for (FileQueueItem item : selected) {
            sendFilesModel.removeElement(item);
        }
        log("Removed " + selected.size() + " file(s) from queue.");
    }

    private void startSendQueue() {
        PeerConnection conn = connection;
        if (conn == null) {
            showError("No connected receiver. Connect a device first.");
            return;
        }
        List<FileQueueItem> snapshot = snapshotPendingQueue();
        if (snapshot.isEmpty()) {
            showError("Queue is empty. Add files first.");
            return;
        }
        if (!sendQueueRunning.compareAndSet(false, true)) {
            return;
        }
        sendQueueBtn.setEnabled(false);
        sendStatus("Sending");
        ioPool.submit(() -> {
            try {
                for (FileQueueItem item : snapshot) {
                    PeerConnection activeConn = connection;
                    if (activeConn == null) {
                        throw new IllegalStateException("Connection lost");
                    }
                    activeSendItem = item;
                    SwingUtilities.invokeLater(() -> {
                        setFileStatus(item, "Sending");
                        sendCurrentFileLabel.setText("Current file: " + item.path().getFileName());
                        sendEncryptLabel.setText("Encryption: 0%");
                        sendEncryptProgressBar.setValue(0);
                        sendEncryptProgressBar.setString("0% Encrypted");
                        addChatLine("System", "Sending file now \uD83D\uDCE4 " + item.path().getFileName());
                    });
                    try {
                        activeConn.sendFile(item.path());
                        SwingUtilities.invokeLater(() -> setFileStatus(item, "Completed"));
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() -> {
                            setFileStatus(item, "Failed");
                            sendStatus("Failed");
                            addChatLine("System", "Send failed \u274C " + item.path().getFileName());
                        });
                        log("Failed sending " + item.path().getFileName() + ": " + ex.getMessage());
                    }
                }
            } catch (IllegalStateException ex) {
                SwingUtilities.invokeLater(() -> {
                    sendStatus("Failed");
                    showError("Sending stopped. " + ex.getMessage());
                });
                log("Queue stopped: " + ex.getMessage());
            } finally {
                activeSendItem = null;
                sendQueueRunning.set(false);
                SwingUtilities.invokeLater(() -> sendQueueBtn.setEnabled(true));
            }
        });
    }

    private List<FileQueueItem> snapshotPendingQueue() {
        List<FileQueueItem> list = new ArrayList<>();
        for (int i = 0; i < sendFilesModel.size(); i++) {
            FileQueueItem item = sendFilesModel.get(i);
            if (!"Completed".equals(item.status())) {
                list.add(item);
            }
        }
        return list;
    }

    private void setFileStatus(FileQueueItem target, String status) {
        for (int i = 0; i < sendFilesModel.size(); i++) {
            FileQueueItem item = sendFilesModel.get(i);
            if (item.path().equals(target.path())) {
                sendFilesModel.set(i, new FileQueueItem(item.path(), item.sizeBytes(), status));
                break;
            }
        }
    }

    private void ensureDiscoveryRunning() {
        if (!validateName()) {
            return;
        }
        if (discoveryService != null) {
            return;
        }
        discoveryService = new DiscoveryService();
        try {
            discoveryService.start(currentDeviceName, Protocol.PORT, this::updatePeers);
        } catch (IOException e) {
            discoveryService = null;
            log("Discovery failed: " + e.getMessage());
            showError("Could not start discovery service.");
        }
    }

    private void updatePeers(List<DiscoveredPeer> peers) {
        SwingUtilities.invokeLater(() -> {
            DiscoveredPeer selected = peersList.getSelectedValue();
            peersModel.clear();
            for (DiscoveredPeer peer : peers) {
                peersModel.addElement(peer);
            }
            if (selected == null) {
                return;
            }
            for (int i = 0; i < peersModel.size(); i++) {
                if (peersModel.get(i).ipAddress().equals(selected.ipAddress())) {
                    peersList.setSelectedIndex(i);
                    break;
                }
            }
        });
    }

    private synchronized void disconnectCurrentConnection(String reason) {
        if (connection != null) {
            connection.close(reason);
            connection = null;
        }
    }

    private synchronized void stopHosting() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private synchronized void shutdownResources() {
        disconnectCurrentConnection("Application closed");
        stopHosting();
        if (discoveryService != null) {
            discoveryService.stop();
            discoveryService = null;
        }
        ioPool.shutdownNow();
    }

    private void sendStatus(String text) {
        sendStatusLabel.setText("Status: " + text);
    }

    private void receiveStatus(String text) {
        receiveStatusLabel.setText("Status: " + text);
    }

    private void addChatLine(String who, String message) {
        SwingUtilities.invokeLater(() -> {
            chatModel.addElement(new ChatMessage(who, message, System.currentTimeMillis()));
            chatList.ensureIndexIsVisible(chatModel.size() - 1);
        });
    }

    private void log(String text) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(text + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void showError(String text) {
        JOptionPane.showMessageDialog(this, text, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private JPanel baseCard() {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL_BG);
        return panel;
    }

    private void applyComponentTheme() {
        styleInputField(nameInputField);
        styleInputField(sendPinField);
        styleInputField(chatInput);

        styleList(peersList);
        styleList(sendFilesList);
        styleIncomingFilesList(incomingList);
        styleChatList(chatList);

        logArea.setBackground(INPUT_BG);
        logArea.setForeground(INPUT_TEXT);
        logArea.setCaretColor(INPUT_TEXT);

        sendCurrentFileLabel.setForeground(TEXT);
        sendSpeedLabel.setForeground(TEXT);
        sendEncryptLabel.setForeground(TEXT);
        sendStatusLabel.setForeground(TEXT);
        receiveCurrentFileLabel.setForeground(TEXT);
        receiveSpeedLabel.setForeground(TEXT);
        receiveDecryptLabel.setForeground(TEXT);
        receiveStatusLabel.setForeground(TEXT);
        sendConnectionLabel.setForeground(SUBTLE);
        receiveConnectionLabel.setForeground(SUBTLE);
        receiveFolderLabel.setForeground(SUBTLE);

        styleProgressBar(sendProgressBar);
        styleProgressBar(sendEncryptProgressBar);
        styleProgressBar(receiveProgressBar);
        styleProgressBar(receiveDecryptProgressBar);
    }

    private void styleInputField(JTextField field) {
        field.setBackground(INPUT_BG);
        field.setForeground(INPUT_TEXT);
        field.setCaretColor(INPUT_TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
    }

    private void styleList(JList<?> list) {
        list.setBackground(INPUT_BG);
        list.setForeground(INPUT_TEXT);
        list.setSelectionBackground(ACCENT_DARK);
        list.setSelectionForeground(Color.WHITE);
    }

    private void styleIncomingFilesList(JList<?> list) {
        list.setBackground(INPUT_BG);
        list.setForeground(INPUT_TEXT);
        list.setSelectionBackground(ACCENT_DARK);
        list.setSelectionForeground(Color.WHITE);
    }

    private void styleChatList(JList<?> list) {
        list.setBackground(CHAT_BG);
        list.setForeground(INPUT_TEXT);
        list.setSelectionBackground(CHAT_BG);
        list.setSelectionForeground(INPUT_TEXT);
    }

    private void styleProgressBar(JProgressBar bar) {
        bar.setBackground(INPUT_BG);
        bar.setForeground(ACCENT);
        bar.setBorder(BorderFactory.createLineBorder(BORDER));
    }

    private JLabel label(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_BODY);
        lbl.setForeground(TEXT);
        return lbl;
    }

    private TitledBorder titledBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER), title);
        border.setTitleColor(TEXT);
        border.setTitleFont(new Font("Segoe UI", Font.BOLD, 13));
        return border;
    }

    private TitledBorder statusBoxBorder() {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.white, 2),
                "Status Box"
        );
        border.setTitleColor(Color.BLACK);
        border.setTitleFont(new Font("Segoe UI", Font.BOLD, 14));
        return border;
    }

    private TitledBorder incomingFilesBorder() {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER),
                "Incoming Files"
        );
        border.setTitleColor(Color.BLACK);
        border.setTitleFont(new Font("Segoe UI", Font.BOLD, 13));
        return border;
    }

    private JPanel centerInPanel(JPanel child) {
        JPanel host = new JPanel(new GridBagLayout());
        host.setBackground(APP_BG);
        host.setBorder(BorderFactory.createEmptyBorder(30, 130, 30, 130));
        host.add(child);
        return host;
    }

    private void stylePrimaryButton(JButton button) {
        button.setFont(FONT_BUTTON);
        button.setFocusPainted(false);
        button.setBackground(ACCENT);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        button.setPreferredSize(new Dimension(220, 56));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(ACCENT_DARK);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(ACCENT);
            }
        });
    }

    private void styleActionButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setBackground(new Color(28, 64, 114));
        button.setForeground(INPUT_TEXT);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
    }

    private void styleChatSendButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setBackground(CHAT_BUBBLE_MINE);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(26, 96, 65), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
    }

    private void styleDisconnectButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setBackground(new Color(176, 52, 52));
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(130, 34, 34), 1, true),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)
        ));
    }

    private static String speedText(double bytesPerSecond) {
        if (bytesPerSecond < 1024.0) {
            return String.format("%.0f B/s", bytesPerSecond);
        }
        double kb = bytesPerSecond / 1024.0;
        if (kb < 1024.0) {
            return String.format("%.2f KB/s", kb);
        }
        return String.format("%.2f MB/s", kb / 1024.0);
    }

    private record FileQueueItem(Path path, long sizeBytes, String status) {
        @Override
        public String toString() {
            return path.getFileName() + "   (" + PeerConnection.humanSize(sizeBytes) + ")   [" + status + "]";
        }
    }

    private record ChatMessage(String sender, String text, long timestampMillis) {
    }

    private static class FileQueueRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof FileQueueItem item) {
                label.setText(item.toString());
                label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            }
            return label;
        }
    }

    private static class PeerCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof DiscoveredPeer peer) {
                label.setText(peer.displayName() + "  [" + peer.ipAddress() + "]");
                label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            }
            return label;
        }
    }

    private static class ChatBubbleRenderer extends JPanel implements javax.swing.ListCellRenderer<ChatMessage> {
        private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
        private final JLabel senderLabel = new JLabel();
        private final JLabel messageLabel = new JLabel();
        private final JLabel metaLabel = new JLabel();
        private final JPanel bubble = new JPanel(new BorderLayout(0, 4));

        private ChatBubbleRenderer() {
            setLayout(new BorderLayout());
            setOpaque(true);
            bubble.add(senderLabel, BorderLayout.NORTH);
            bubble.add(messageLabel, BorderLayout.CENTER);
            bubble.add(metaLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ChatMessage> list,
                                                      ChatMessage value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            removeAll();
            setBackground(CHAT_BG);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

            if (value == null) {
                return this;
            }

            boolean mine = "You".equals(value.sender());
            boolean system = "System".equals(value.sender());

            String safeText = escapeHtml(value.text()).replace("\n", "<br>");
                messageLabel.setText("<html><body style='width:200px; font-family:Segoe UI Emoji, Segoe UI Symbol, Segoe UI;'>"
                    + safeText + "</body></html>");
                messageLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
            messageLabel.setForeground(Color.WHITE);

                senderLabel.setText(value.sender());
                senderLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
                senderLabel.setForeground(new Color(220, 235, 255));
                senderLabel.setVisible(!system);

            LocalTime localTime = Instant.ofEpochMilli(value.timestampMillis())
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime();
                metaLabel.setText(TIME_FORMAT.format(localTime));
            metaLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            metaLabel.setForeground(CHAT_META);

            Color bubbleColor = system ? CHAT_BUBBLE_SYSTEM : (mine ? CHAT_BUBBLE_MINE : CHAT_BUBBLE_OTHER);
            bubble.setBackground(bubbleColor);
            bubble.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(bubbleColor.darker(), 1, true),
                    BorderFactory.createEmptyBorder(7, 9, 7, 9)
            ));

            if (system) {
                add(bubble, BorderLayout.CENTER);
            } else if (mine) {
                add(bubble, BorderLayout.EAST);
            } else {
                add(bubble, BorderLayout.WEST);
            }
            return this;
        }

        private static String escapeHtml(String text) {
            return text
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }
    }

    private static class FileDropHandler extends TransferHandler {
        private final java.util.function.Consumer<List<Path>> onFilesDropped;

        private FileDropHandler(java.util.function.Consumer<List<Path>> onFilesDropped) {
            this.onFilesDropped = onFilesDropped;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                Transferable transferable = support.getTransferable();
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                List<Path> paths = new ArrayList<>();
                for (File file : files) {
                    paths.add(file.toPath());
                }
                onFilesDropped.accept(paths);
                return true;
            } catch (UnsupportedFlavorException | IOException ignored) {
                return false;
            }
        }
    }
}
