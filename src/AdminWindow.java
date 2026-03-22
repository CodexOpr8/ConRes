package conres;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;

// admin dashboard window — shows live state of the entire conres system.
// subscribes to the backend via two listener types:
//   - change listener (runnable): fires on any state change, triggers refresh()
//   - log listener (consumer<string>): fires for every event, appends to the event log
// this means the ui updates automatically with no polling loop needed.
public class AdminWindow extends JFrame {

    private static final Color BG       = new Color( 10,  15,  30);
    private static final Color CARD     = new Color( 18,  25,  48);
    private static final Color HDR      = new Color( 15,  35,  90);
    private static final Color DIVIDER  = new Color( 40,  55, 100);
    private static final Color CELL_ALT = new Color( 24,  32,  58);
    private static final Color WHITE    = new Color(220, 230, 255);
    private static final Color GREEN    = new Color( 74, 222, 128);
    private static final Color YELLOW   = new Color(250, 204,  21);
    private static final Color RED      = new Color(248,  93,  93);
    private static final Color CYAN     = new Color( 34, 211, 238);
    private static final Color GRAY     = new Color(148, 163, 184);

    private final ConResSystem system;

    private final JLabel   lblUserCount = lbl("0 / 4", 44, GREEN);
    private final JLabel   lblUserSub   = lbl("users logged in", 11, GRAY);
    private final JPanel[] slots        = new JPanel[ConResSystem.MAX_USERS];

    // table model for the active sessions panel — not editable by the admin
    private final DefaultTableModel activeModel = new DefaultTableModel(
        new Object[]{"User ID", "Username", "Action", "Lock Held"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };

    private final DefaultListModel<String> waitModel = new DefaultListModel<>();

    private final JLabel lblResStatus = lbl("IDLE", 22, CYAN);
    private final JLabel lblResDetail = lbl("No active readers or writers.", 12, GRAY);

    private final JTextArea logArea = new JTextArea();
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

    // tracks how many user windows have been opened, used to offset their positions
    private int windowCount = 0;

    public AdminWindow(ConResSystem system) {
        super("ConRes  |  v2 — Full login + session UI added");
        this.system = system;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1060, 780);
        setLocation(30, 30);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildMain(),   BorderLayout.CENTER);

        // subscribe to backend state changes — re-render the dashboard on the edt whenever anything changes
        system.addChangeListener(() -> SwingUtilities.invokeLater(this::refresh));

        // every log event gets appended to the event log panel
        system.addLogListener(msg -> SwingUtilities.invokeLater(() -> appendLog(msg)));

        setVisible(true);
        appendLog("Admin dashboard started.  MAX_CONCURRENT_USERS = " + ConResSystem.MAX_USERS);
        refresh();
    }

    private JPanel buildTopBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(HDR);
        p.setBorder(new EmptyBorder(12, 20, 12, 20));

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 3));
        left.setOpaque(false);
        JLabel title = lbl("ConRes  —  Admin Dashboard", 18, WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        left.add(title);
        left.add(lbl("100717312", 11, new java.awt.Color(0x16A34A)));

        // button to open a new user session window
        JButton btn = new JButton("＋  Open User Session");
        btn.setBackground(new Color(37, 99, 235));
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setPreferredSize(new Dimension(210, 36));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            windowCount++;
            new UserWindow(system, windowCount);
            appendLog("User window #" + windowCount + " opened.");
        });

        p.add(left, BorderLayout.WEST);
        p.add(btn,  BorderLayout.EAST);
        return p;
    }

    private JPanel buildMain() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(5, 5, 5, 5);

        // row 0: semaphore slot panel on the left, resource status on the right
        g.gridy = 0; g.weighty = 0.22;
        g.gridx = 0; g.weightx = 0.26; p.add(buildSemPanel(),      g);
        g.gridx = 1; g.weightx = 0.74; p.add(buildResourcePanel(), g);

        // row 1: active sessions table on the left, waiting queue on the right
        g.gridy = 1; g.weighty = 0.36;
        g.gridx = 0; g.weightx = 0.65; p.add(buildActivePanel(),  g);
        g.gridx = 1; g.weightx = 0.35; p.add(buildWaitingPanel(), g);

        // row 2: event log spanning full width
        g.gridy = 2; g.weighty = 0.42;
        g.gridx = 0; g.weightx = 1.0; g.gridwidth = 2;
        p.add(buildLogPanel(), g);

        return p;
    }

    private JPanel buildSemPanel() {
        JPanel p = card("CONCURRENT SESSIONS");
        p.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(2, 6, 2, 6);

        g.gridy = 0; p.add(lbl("MAX ALLOWED: " + ConResSystem.MAX_USERS, 10, GRAY), g);
        g.gridy = 1;
        lblUserCount.setFont(new Font("SansSerif", Font.BOLD, 44));
        p.add(lblUserCount, g);
        g.gridy = 2; p.add(lblUserSub, g);

        // coloured squares — one per slot, filled green when occupied
        g.gridy = 3; g.insets = new Insets(10, 4, 4, 4);
        JPanel slotRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        slotRow.setOpaque(false);
        for (int i = 0; i < ConResSystem.MAX_USERS; i++) {
            slots[i] = new JPanel();
            slots[i].setPreferredSize(new Dimension(26, 26));
            slots[i].setBackground(CARD);
            slots[i].setBorder(new LineBorder(GRAY, 1, true));
            slotRow.add(slots[i]);
        }
        p.add(slotRow, g);
        g.gridy = 4; g.weighty = 1; p.add(Box.createVerticalGlue(), g);
        return p;
    }

    private JPanel buildResourcePanel() {
        JPanel p = card("SHARED RESOURCE STATUS");
        p.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(4, 12, 4, 12);

        g.gridy = 0; p.add(lbl("File:", 11, GRAY), g);
        g.gridy = 1;
        JLabel fname = lbl("ProductSpecification.txt", 13, CYAN);
        fname.setFont(new Font("Monospaced", Font.BOLD, 13));
        p.add(fname, g);
        g.gridy = 2; g.insets = new Insets(12, 12, 2, 12);
        p.add(lbl("Status:", 11, GRAY), g);
        g.gridy = 3; g.insets = new Insets(2, 12, 4, 12);
        lblResStatus.setFont(new Font("SansSerif", Font.BOLD, 22));
        p.add(lblResStatus, g);
        g.gridy = 4; g.insets = new Insets(4, 12, 4, 12);
        p.add(lblResDetail, g);
        g.gridy = 5; g.weighty = 1; p.add(Box.createVerticalGlue(), g);
        return p;
    }

    private JPanel buildActivePanel() {
        JPanel p = card("ACTIVE SESSIONS");
        p.setLayout(new BorderLayout());
        JTable table = new JTable(activeModel);
        table.setBackground(CARD); table.setForeground(WHITE);
        table.setFont(new Font("Monospaced", Font.PLAIN, 13));
        table.setRowHeight(28); table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        table.getTableHeader().setBackground(new Color(20, 35, 75));
        table.getTableHeader().setForeground(WHITE);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(260);

        // custom cell renderer: colours the action and lock columns based on their value
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                String s = val == null ? "" : val.toString();
                setBackground(row % 2 == 0 ? CARD : CELL_ALT);
                Color fg = WHITE;
                if (col == 2) fg = s.equals("Writing") ? RED : s.equals("Reading") ? GREEN : GRAY;
                if (col == 3) fg = s.startsWith("WRITE") ? RED : s.startsWith("READ") ? GREEN : GRAY;
                setForeground(fg); setBorder(new EmptyBorder(0, 10, 0, 4));
                return this;
            }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(CARD);
        p.add(sp);
        return p;
    }

    private JPanel buildWaitingPanel() {
        JPanel p = card("WAITING QUEUE  (blocked on semaphore)");
        p.setLayout(new BorderLayout());
        JList<String> list = new JList<>(waitModel);
        list.setBackground(CARD); list.setForeground(YELLOW);
        list.setFont(new Font("Monospaced", Font.PLAIN, 13));
        list.setBorder(new EmptyBorder(4, 8, 4, 8));
        list.setFixedCellHeight(28);
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(CARD);
        p.add(sp);
        return p;
    }

    private JPanel buildLogPanel() {
        JPanel p = card("EVENT LOG");
        p.setLayout(new BorderLayout());
        logArea.setEditable(false);
        logArea.setBackground(new Color(8, 12, 22));
        logArea.setForeground(new Color(134, 239, 172));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBorder(new EmptyBorder(4, 8, 4, 8));
        logArea.setLineWrap(true);
        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(new Color(8, 12, 22));
        p.add(sp);
        return p;
    }

    // re-reads backend state and updates every panel — called on the edt via the change listener
    private void refresh() {
        SharedFile sf    = system.getSharedFile();
        int active       = system.getActiveSessions().size();
        Set<Integer> rdr = sf.getCurrentReaders();
        int writer       = sf.getCurrentWriter();

        // update the session count label and colour based on how full the semaphore is
        lblUserCount.setText(active + " / " + ConResSystem.MAX_USERS);
        if      (active == 0)                       lblUserCount.setForeground(GREEN);
        else if (active < ConResSystem.MAX_USERS)   lblUserCount.setForeground(YELLOW);
        else                                         lblUserCount.setForeground(RED);
        lblUserSub.setText(active == 1 ? "user logged in" : "users logged in");

        // fill slot indicators green for each occupied session, grey for free ones
        for (int i = 0; i < slots.length; i++) {
            if (i < active) { slots[i].setBackground(GREEN); slots[i].setBorder(new LineBorder(GREEN, 1, true)); }
            else            { slots[i].setBackground(CARD);  slots[i].setBorder(new LineBorder(GRAY, 1, true));  }
        }

        // rebuild the active sessions table from scratch each refresh
        activeModel.setRowCount(0);
        system.getActiveSessions().forEach((uid, uname) -> {
            String action, lock;
            if (uid == writer)          { action = "Writing"; lock = "WRITE — exclusive (all others blocked)"; }
            else if (rdr.contains(uid)) { action = "Reading"; lock = "READ  — shared (concurrent reads OK)";  }
            else                        { action = "Idle";    lock = "None"; }
            activeModel.addRow(new Object[]{uid, uname, action, lock});
        });

        // rebuild the waiting queue list
        waitModel.clear();
        int pos = 1;
        for (String w : system.getWaitingQueue())
            waitModel.addElement("#" + pos++ + "   " + w + "  —  waiting for a free slot");

        // update the shared resource status panel
        if (writer != -1) {
            String wn = system.getActiveSessions().getOrDefault(writer, "ID:" + writer);
            lblResStatus.setText("WRITING"); lblResStatus.setForeground(RED);
            lblResDetail.setText("User " + writer + " (" + wn + ") holds exclusive write lock  |  all readers & writers are blocked");
            lblResDetail.setForeground(RED);
        } else if (!rdr.isEmpty()) {
            String ids = rdr.stream().sorted().map(Object::toString).reduce("", (a, b) -> a + " " + b).trim();
            lblResStatus.setText("READING"); lblResStatus.setForeground(GREEN);
            lblResDetail.setText("Concurrent readers active  |  User IDs: " + ids);
            lblResDetail.setForeground(GREEN);
        } else {
            lblResStatus.setText("IDLE"); lblResStatus.setForeground(CYAN);
            lblResDetail.setText("No active readers or writers."); lblResDetail.setForeground(GRAY);
        }
    }

    // appends a timestamped entry to the event log and scrolls to the bottom
    private void appendLog(String msg) {
        logArea.append("[" + sdf.format(new Date()) + "]  " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // helper: creates a styled card panel with a titled border
    private JPanel card(String title) {
        JPanel p = new JPanel();
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(new LineBorder(DIVIDER, 1), title,
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 10), CYAN),
            new EmptyBorder(4, 4, 4, 4)));
        return p;
    }

    // helper: creates a plain label with the given font size and foreground colour
    private static JLabel lbl(String t, int sz, Color fg) {
        JLabel l = new JLabel(t);
        l.setForeground(fg);
        l.setFont(new Font("SansSerif", Font.PLAIN, sz));
        return l;
    }
}