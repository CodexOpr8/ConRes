package conres;

// Commit 2 — First GUI: AdminWindow only.
// UserWindow not built yet — '+ Open User Session' launches the user portal
// but UserWindow.java is just a stub with a placeholder message for now.
public class Main {
    public static void main(String[] args) {
        ConResSystem system = new ConResSystem();
        javax.swing.SwingUtilities.invokeLater(() -> new AdminWindow(system));
    }
}
