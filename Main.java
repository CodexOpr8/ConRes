package conres;

public class Main {
    public static void main(String[] args) {
        // initialise the core logic engine first.
        ConResSystem system = new ConResSystem();
        // fire up the dashboard on the event dispatch thread to keep the ui responsive.
        javax.swing.SwingUtilities.invokeLater(() -> new AdminWindow(system));
    }
}