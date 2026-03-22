package conres;

// entry point — creates the shared backend and launches the admin dashboard on the edt.
// user windows are opened from within the admin dashboard via the '+ open user session' button.
public class Main {
    public static void main(String[] args) {
        ConResSystem system = new ConResSystem();
        javax.swing.SwingUtilities.invokeLater(() -> new AdminWindow(system));
    }
}
