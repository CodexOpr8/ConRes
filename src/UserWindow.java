package conres;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.*;

// per-user session portal window.
// operates as a state machine with six states:
//   logged_out  — login form is shown
//   idle        — logged in, no file open
//   wait_read   — acquiring the read lock (buttons hidden to prevent double-clicks)
//   reading     — shared read lock held; close file button shown
//   wait_write  — acquiring the write lock (buttons hidden)
//   writing     — exclusive write lock held; save & close / cancel buttons shown
//
// a dedicated single-thread executor runs all blocking calls (login, read, write)
// off the swing event dispatch thread so the ui never freezes while waiting for
// a semaphore permit or a lock.
// cardlayout switches between the "login" card and the "session" card.
public class UserWindow extends JFrame {

    private static final Color BG     = new Color(245, 248, 255);
    private static final Color CARD   = Color.WHITE;
    private static final Color ACCENT = new Color(37,  99, 235);
    private static final Color GREEN  = new Color(22, 163,  74);
    private static final Color RED    = new Color(204,  30,  30);
    private static final Color AMBER  = new Color(180,  90,   0);
    private static final Color PURPLE = new Color(109,  40, 217);
    private static final Color ORANGE = new Color(180,  60,   0);
    private static final Color TEAL   = new Color(8,  145, 178);
    private static final Color TXT    = new Color(15,  23,  42);
    private static final Color SUBTXT = new Color(100, 116, 139);
    private static final Color BORD   = new Color(203, 213, 225);

    private enum State { LOGGED_OUT, IDLE, WAIT_READ, READING, WAIT_WRITE, WRITING }

    private final ConResSystem system;
    private volatile State state = State.LOGGED_OUT;
    private String loggedInUser = null;
    private int    loggedInId   = -1;

    // one background thread per window — all blocking operations run here, not on the edt
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UserWorker");
        t.setDaemon(true);
        return t;
    });

    // login form fields and controls
    private final JTextField     fldUser  = new JTextField(16);
    private final JPasswordField fldPass  = new JPasswordField(16);
    private final JButton        btnLogin = btn("Login", ACCENT);
    private final JLabel         lblMsg   = new JLabel(" ");

    // session toolbar — buttons shown/hidden depending on current state
    private final JLabel  lblStatus = new JLabel(" ");
    private final JButton btnRead   = btn("Open File (Read)",  TEAL);
    private final JButton btnWrite  = btn("Open File (Write)", PURPLE);
    private final JButton btnClose  = btn("Close File",        ORANGE);
    private final JButton btnSave   = btn("Save & Close",      GREEN);
    private final JButton btnCancel = btn("Cancel Write",      RED);
    private final JButton btnLogout = btn("Logout",            RED);

    private final JLabel    lblBar   = new JLabel("  No file open.");
    private final JLabel    lblTimer = new JLabel(" "); // countdown label
    private final JTextArea txtFile  = new JTextArea();

    // cardlayout switches between the login card and the session card
    private final CardLayout cards = new CardLayout();
    private final JPanel     deck  = new JPanel(cards);

    // swing timer that counts down the remaining hold time and auto-releases at zero
    private javax.swing.Timer holdCountdown = null;
    private int secondsRemaining = 0;

    public UserWindow(ConResSystem system, int num) {
        super("ConRes — User Portal");
        this.system = system;
        setSize(560, 690);
        setMinimumSize(new Dimension(500, 620));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocation(270 + num * 40, 100 + num * 30);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        deck.setOpaque(false);
        deck.add(buildLoginPanel(),   "login");
        deck.add(buildSessionPanel(), "session");

        add(buildHeader(), BorderLayout.NORTH);
        add(deck,          BorderLayout.CENTER);

        wire();
        applyState();
        setVisible(true);
        fldUser.requestFocusInWindow();
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(ACCENT);
        p.setBorder(new EmptyBorder(12, 18, 12, 18));
        JLabel t = new JLabel("ConRes  —  User Portal");
        t.setFont(new Font("SansSerif", Font.BOLD, 16));
        t.setForeground(Color.WHITE);
        JLabel s = new JLabel("Concurrent Resource Access Engine");
        s.setFont(new Font("SansSerif", Font.PLAIN, 11));
        s.setForeground(new Color(186, 210, 255));
        JPanel col = new JPanel(new GridLayout(2, 1, 0, 2));
        col.setOpaque(false); col.add(t); col.add(s);
        p.add(col);
        return p;
    }

    private JPanel buildLoginPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(BG);
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORD, 1, true), new EmptyBorder(28, 34, 26, 34)));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(6, 4, 6, 4);

        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        JLabel h = new JLabel("Sign In");
        h.setFont(new Font("SansSerif", Font.BOLD, 22)); h.setForeground(TXT);
        card.add(h, g);

        g.gridy = 1; g.gridwidth = 1;
        card.add(fLabel("Username"), g);
        g.gridx = 1; styleField(fldUser); card.add(fldUser, g);

        g.gridy = 2; g.gridx = 0;
        card.add(fLabel("Password"), g);
        g.gridx = 1; styleField(fldPass); card.add(fldPass, g);

        g.gridy = 3; g.gridx = 0; g.gridwidth = 2; g.insets = new Insets(18, 4, 4, 4);
        btnLogin.setPreferredSize(new Dimension(260, 40));
        card.add(btnLogin, g);

        g.gridy = 4; g.insets = new Insets(4, 4, 4, 4);
        lblMsg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblMsg.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(lblMsg, g);

        // hint label showing available usernames and the password pattern
        g.gridy = 5;
        JLabel hint = new JLabel("<html><center><font color='#94a3b8'>"
            + "Users: karanjot · billy · paul · phil · dan · mike · shaun · dean<br>"
            + "Password: [username]123 &nbsp; e.g. karanjot / karanjot123"
            + "</font></center></html>");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(hint, g);

        outer.add(card);
        return outer;
    }

    private JPanel buildSessionPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);

        JPanel toolbar = new JPanel(new GridBagLayout());
        toolbar.setBackground(new Color(232, 238, 255));
        toolbar.setBorder(new EmptyBorder(10, 14, 10, 14));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; g.gridx = 0;

        g.gridy = 0; g.insets = new Insets(0, 0, 6, 0);
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblStatus.setForeground(TXT);
        toolbar.add(lblStatus, g);

        g.gridy = 1; g.insets = new Insets(0, 0, 4, 0);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnRow.setOpaque(false);
        for (JButton b : new JButton[]{btnRead, btnWrite, btnClose, btnSave, btnCancel, btnLogout})
            btnRow.add(b);
        toolbar.add(btnRow, g);

        // countdown row — shows remaining hold time while a lock is open
        g.gridy = 2; g.insets = new Insets(2, 0, 0, 0);
        lblTimer.setFont(new Font("Monospaced", Font.BOLD, 11));
        lblTimer.setForeground(AMBER);
        toolbar.add(lblTimer, g);

        p.add(toolbar, BorderLayout.NORTH);

        // status bar at the bottom showing the current lock state
        lblBar.setFont(new Font("Monospaced", Font.BOLD, 11));
        lblBar.setForeground(SUBTXT);
        lblBar.setBackground(new Color(22, 30, 55));
        lblBar.setOpaque(true);
        lblBar.setPreferredSize(new Dimension(0, 26));
        lblBar.setBorder(new EmptyBorder(0, 10, 0, 10));
        p.add(lblBar, BorderLayout.SOUTH);

        txtFile.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtFile.setBackground(new Color(15, 23, 42));
        txtFile.setForeground(new Color(190, 240, 200));
        txtFile.setCaretColor(Color.WHITE);
        txtFile.setBorder(new EmptyBorder(10, 12, 10, 12));
        txtFile.setLineWrap(true); txtFile.setWrapStyleWord(true);
        txtFile.setEditable(false);

        JScrollPane sp = new JScrollPane(txtFile);
        sp.setBorder(BorderFactory.createTitledBorder(
            new LineBorder(new Color(51, 65, 85), 1), "ProductSpecification.txt",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 11), new Color(100, 180, 220)));
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    // wires up all button and window event listeners
    private void wire() {

        // pressing enter in the password field triggers login
        fldPass.addActionListener(e -> btnLogin.doClick());

        // login — validates credentials then calls system.login() on the worker thread.
        // also catches illegalstateexception thrown when a duplicate user is rejected atomically.
        btnLogin.addActionListener(e -> {
            String user = fldUser.getText().trim();
            String pass = new String(fldPass.getPassword());
            if (user.isEmpty() || pass.isEmpty()) { msg("Enter username and password.", RED); return; }

            UserDatabase.UserRecord rec = UserDatabase.authenticate(user, pass);
            if (rec == null) { msg("Incorrect username or password.", RED); fldPass.setText(""); return; }

            loggedInUser = user;
            loggedInId   = rec.id;
            btnLogin.setEnabled(false);
            msg("Waiting for a session slot...", SUBTXT);

            worker.submit(() -> {
                try {
                    system.login(loggedInUser, loggedInId);
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("Logged in as:  " + loggedInUser + "  (ID " + loggedInId + ")");
                        setState(State.IDLE);
                        setBar("  No file open.", SUBTXT);
                        cards.show(deck, "session");
                    });
                } catch (IllegalStateException ex) {
                    // login() rejected this user atomically — they are already active or queued
                    loggedInUser = null; loggedInId = -1;
                    SwingUtilities.invokeLater(() -> {
                        msg(ex.getMessage(), RED);
                        fldPass.setText("");
                        btnLogin.setEnabled(true);
                    });
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    SwingUtilities.invokeLater(() -> { msg("Login interrupted.", RED); btnLogin.setEnabled(true); });
                }
            });
        });

        // open for reading — uses trylock; shows a popup if the lock times out
        btnRead.addActionListener(e -> {
            setState(State.WAIT_READ);
            setBar("  Opening file for reading...", SUBTXT);
            worker.submit(() -> {
                try {
                    String content = system.startRead(loggedInId);
                    SwingUtilities.invokeLater(() -> {
                        txtFile.setText(content);
                        txtFile.setCaretPosition(0);
                        setState(State.READING);
                        setBar("  READING  —  shared read lock held.  Click Close File when done.",
                               new Color(74, 222, 128));
                        startHoldCountdown(SharedFile.HOLD_TIMEOUT_SECONDS, State.READING);
                    });
                } catch (SharedFile.LockTimeoutException ex) {
                    SwingUtilities.invokeLater(() -> {
                        setState(State.IDLE);
                        setBar("  Timeout: " + ex.getMessage(), RED);
                        JOptionPane.showMessageDialog(this, ex.getMessage(), "Read Lock Timeout", JOptionPane.WARNING_MESSAGE);
                    });
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });
        });

        // close file — stops the countdown and releases the shared read lock
        btnClose.addActionListener(e -> {
            stopHoldCountdown();
            setState(State.IDLE);
            setBar("  Releasing read lock...", SUBTXT);
            worker.submit(() -> {
                system.stopRead(loggedInId);
                SwingUtilities.invokeLater(() -> { txtFile.setText(""); setBar("  File closed.  No file open.", SUBTXT); });
            });
        });

        // open for writing — uses trylock; shows a popup if the lock times out
        btnWrite.addActionListener(e -> {
            setState(State.WAIT_WRITE);
            setBar("  Opening file for writing  (waiting if others are currently reading)...", SUBTXT);
            worker.submit(() -> {
                try {
                    system.startWrite(loggedInId);
                    String current = system.getSharedFile().getContent();
                    SwingUtilities.invokeLater(() -> {
                        txtFile.setText(current);
                        txtFile.setCaretPosition(0);
                        txtFile.setEditable(true);
                        txtFile.requestFocusInWindow();
                        setState(State.WRITING);
                        setBar("  WRITING  —  exclusive lock held.  Edit below, then Save & Close or Cancel.",
                               new Color(248, 93, 93));
                        startHoldCountdown(SharedFile.HOLD_TIMEOUT_SECONDS, State.WRITING);
                    });
                } catch (SharedFile.LockTimeoutException ex) {
                    SwingUtilities.invokeLater(() -> {
                        setState(State.IDLE);
                        setBar("  Timeout: " + ex.getMessage(), RED);
                        JOptionPane.showMessageDialog(this, ex.getMessage(), "Write Lock Timeout", JOptionPane.WARNING_MESSAGE);
                    });
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });
        });

        // save & close — commits content to disk and releases the write lock
        btnSave.addActionListener(e -> {
            final String newContent = txtFile.getText();
            stopHoldCountdown();
            setState(State.IDLE); txtFile.setEditable(false);
            setBar("  Saving and releasing write lock...", SUBTXT);
            worker.submit(() -> {
                system.commitWrite(loggedInId, newContent);
                SwingUtilities.invokeLater(() -> { txtFile.setText(""); setBar("  File saved.  No file open.", new Color(74, 222, 128)); });
            });
        });

        // cancel write — discards changes and releases the write lock without saving
        btnCancel.addActionListener(e -> {
            stopHoldCountdown();
            setState(State.IDLE); txtFile.setEditable(false);
            setBar("  Cancelling — releasing write lock without saving...", SUBTXT);
            worker.submit(() -> {
                system.cancelWrite(loggedInId);
                SwingUtilities.invokeLater(() -> { txtFile.setText(""); setBar("  Write cancelled.  No file open.", SUBTXT); });
            });
        });

        // logout — releases the session slot so another waiting user can proceed
        btnLogout.addActionListener(e -> {
            stopHoldCountdown();
            setState(State.LOGGED_OUT);
            worker.submit(() -> {
                system.logout(loggedInUser, loggedInId);
                loggedInUser = null; loggedInId = -1;
                SwingUtilities.invokeLater(() -> {
                    fldUser.setText(""); fldPass.setText("");
                    msg(" ", TXT); btnLogin.setEnabled(true);
                    txtFile.setText(""); txtFile.setEditable(false);
                    setBar("  No file open.", SUBTXT); lblTimer.setText(" ");
                    cards.show(deck, "login"); fldUser.requestFocusInWindow();
                });
            });
        });

        // window close — shutdownnow() interrupts blocked semaphore.acquire() in login()
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                stopHoldCountdown();
                worker.shutdownNow();
                State s = state;
                if (s == State.READING  || s == State.WAIT_READ)  system.stopRead(loggedInId);
                if (s == State.WRITING  || s == State.WAIT_WRITE) system.cancelWrite(loggedInId);
                if (loggedInUser != null) system.logout(loggedInUser, loggedInId);
            }
        });
    }

    // starts a 60-second countdown while a lock is held.
    // at zero it triggers the correct release button so the lock is freed via the normal path.
    // the message is state-aware: tells the user whether to close the file or save/cancel.
    private void startHoldCountdown(int seconds, State lockState) {
        stopHoldCountdown();
        secondsRemaining = seconds;
        holdCountdown = new javax.swing.Timer(1000, null);
        holdCountdown.addActionListener(ev -> {
            secondsRemaining--;
            if (secondsRemaining > 0) {
                Color col = secondsRemaining <= 10 ? RED : AMBER;
                lblTimer.setForeground(col);
                String action = (lockState == State.READING) ? "close the file" : "save or cancel";
                lblTimer.setText("  Lock auto-expires in " + secondsRemaining + "s"
                    + (secondsRemaining <= 10 ? "  — " + action + " soon!" : ""));
            } else {
                stopHoldCountdown();
                if (lockState == State.READING) btnClose.doClick();
                else                            btnCancel.doClick();
            }
        });
        holdCountdown.start();
    }

    private void stopHoldCountdown() {
        if (holdCountdown != null) { holdCountdown.stop(); holdCountdown = null; }
        lblTimer.setText(" ");
    }

    private void setState(State s) { this.state = s; applyState(); }

    // shows or hides buttons based on the current state
    private void applyState() {
        boolean idle    = state == State.IDLE;
        boolean reading = state == State.READING;
        boolean writing = state == State.WRITING;
        btnRead.setVisible(idle); btnWrite.setVisible(idle); btnLogout.setVisible(idle);
        btnClose.setVisible(reading);
        btnSave.setVisible(writing); btnCancel.setVisible(writing);
    }

    private void setBar(String text, Color fg) {
        SwingUtilities.invokeLater(() -> { lblBar.setText(text); lblBar.setForeground(fg); });
    }
    private void msg(String text, Color color) { lblMsg.setText(text); lblMsg.setForeground(color); }

    private static JLabel fLabel(String t) {
        JLabel l = new JLabel(t); l.setFont(new Font("SansSerif", Font.BOLD, 12));
        l.setForeground(TXT); l.setPreferredSize(new Dimension(78, 28)); return l;
    }
    private static void styleField(JTextField f) {
        f.setFont(new Font("SansSerif", Font.PLAIN, 13));
        f.setPreferredSize(new Dimension(175, 32));
        f.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORD, 1, true), new EmptyBorder(4, 8, 4, 8)));
    }
    private static JButton btn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setFocusPainted(false); b.setBorderPainted(false); b.setOpaque(true);
        b.setPreferredSize(new Dimension(150, 36));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}