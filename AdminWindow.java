package conres;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;

// main dashboard

public class AdminWindow extends JFrame {

    // dark-mode colour
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

    // components for real-time stats
    private final JLabel   lblUserCount = lbl("0 / 4", 44, GREEN);
    private final JLabel   lblUserSub   = lbl("users logged in", 11, GRAY);
    private final JPanel[] slots        = new JPanel[ConResSystem.MAX_USERS];

    // data models for the tables and lists
    private final DefaultTableModel activeModel = new DefaultTableModel(
        new Object[]{"User ID", "Username", "Action", "Lock Held"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultListModel<String> waitModel = new DefaultListModel<>();

    private final JLabel lblResStatus = lbl("IDLE", 22, CYAN);
    private final JLabel lblResDetail = lbl("No active readers or writers.", 12, GRAY);

    private final JTextArea logArea = new JTextArea();
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

    public AdminWindow(ConResSystem system) {
        super("ConRes  |  v1 — Dashboard GUI only");
        this.system = system;

        // window Setup
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1060, 780);
        setLocation(30, 30);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        // assembly
        add(buildTopBar(), BorderLayout.NORTH);
        add(buildMain(),   BorderLayout.CENTER);

        // listem for back-end changes to trigger UI repaints
        system.addChangeListener(() -> SwingUtilities.invokeLater(this::refresh));
        system.addLogListener(msg -> SwingUtilities.invokeLater(() -> appendLog(msg)));

        setVisible(true);
        appendLog("Dashboard started — GUI only. Login functionality added in Commit 2.");
        refresh();
    }

    // itle and the "Open Session" button
    private JPanel buildTopBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(HDR);
        p.setBorder(new EmptyBorder(12, 20, 12, 20));

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 3));
        left.setOpaque(false);
        JLabel title = lbl("ConRes  —  Admin Dashboard", 18, WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        left.add(title);
        left.add(lbl("100717312", 11, new java.awt.Color(0x7C3AED)));

        // shows a "Coming Soon" popup
        JButton btn = new JButton("＋  Open User Session");
        btn.setBackground(new Color(37, 99, 235));
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setPreferredSize(new Dimension(210, 36));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e ->
            JOptionPane.showMessageDialog(this,
                "User login portal not implemented yet.\n\nAdded in Commit 2.",
                "Coming in Commit 2",
                JOptionPane.INFORMATION_MESSAGE)
        );

        p.add(left, BorderLayout.WEST);
        p.add(btn,  BorderLayout.EAST);
        return p;
    }

    // layout engine for the dashboard tiles
    private JPanel buildMain() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(5, 5, 5, 5);

        // top row: semaphore and ersource Status
        g.gridy = 0; g.weighty = 0.22;
        g.gridx = 0; g.weightx = 0.26; p.add(buildSemPanel(),      g);
        g.gridx = 1; g.weightx = 0.74; p.add(buildResourcePanel(), g);

        // middle: active Users and Waiting Queue
        g.gridy = 1; g.weighty = 0.36;
        g.gridx = 0; g.weightx = 0.65; p.add(buildActivePanel(),  g);
        g.gridx = 1; g.weightx = 0.35; p.add(buildWaitingPanel(), g);

        // bottom: The Log console
        g.gridy = 2; g.weighty = 0.42;
        g.gridx = 0; g.weightx = 1.0; g.gridwidth = 2;
        p.add(buildLogPanel(), g);

        return p;
    }

    // displays current user count vs max limit
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

        // Visual "dots" representing available seats
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

    // displays what file is being accessed and how
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

    // the main table showing who is reading/writing
    private JPanel buildActivePanel() {
        JPanel p = card("ACTIVE SESSIONS");
        p.setLayout(new BorderLayout());
        JTable table = new JTable(activeModel);
        table.setBackground(CARD); table.setForeground(WHITE);
        table.setFont(new Font("Monospaced", Font.PLAIN, 13));
        table.setRowHeight(28); table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        
        // Custom styling for the table headers
        table.getTableHeader().setBackground(new Color(20, 35, 75));
        table.getTableHeader().setForeground(WHITE);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.getTableHeader().setReorderingAllowed(false);
        
        // Dynamic colour coding: Red for Writing, Green for Reading
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
        p.add(sp); return p;
    }

    // shows users blocked by the semaphore
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
        p.add(sp); return p;
    }

    // scrolalble terminal-style log
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
        p.add(sp); return p;
    }

    // updates the visuals based on the ConResSystem state
    private void refresh() {
        int active = system.getActiveSessions().size();
        lblUserCount.setText(active + " / " + ConResSystem.MAX_USERS);
        lblUserSub.setText(active == 1 ? "user logged in" : "users logged in");
        
        // colour the "seats" green if filled
        for (int i = 0; i < slots.length; i++) {
            slots[i].setBackground(i < active ? GREEN : CARD);
            slots[i].setBorder(new LineBorder(i < active ? GREEN : GRAY, 1, true));
        }
        
        activeModel.setRowCount(0);
        waitModel.clear();
        
        // hardcoded IDLE for now
        lblResStatus.setText("IDLE"); lblResStatus.setForeground(CYAN);
        lblResDetail.setText("No active readers or writers."); lblResDetail.setForeground(GRAY);
    }

    // adds a timestamped message to the log area
    private void appendLog(String msg) {
        logArea.append("[" + sdf.format(new Date()) + "]  " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // uI Helper: creates a styled container "tile"
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

    // UI Helper: Creates a styled label quickly
    private static JLabel lbl(String t, int sz, Color fg) {
        JLabel l = new JLabel(t);
        l.setForeground(fg); l.setFont(new Font("SansSerif", Font.PLAIN, sz));
        return l;
    }
}