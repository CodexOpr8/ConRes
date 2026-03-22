package conres;

import java.util.*;
import java.util.function.Consumer;

public class ConResSystem {

    public static final int MAX_USERS = 4; // cap to prevent system overload.

    private final List<Runnable>         changeListeners = new ArrayList<>();
    private final List<Consumer<String>> logListeners    = new ArrayList<>();

    public void addChangeListener(Runnable r)      { changeListeners.add(r); }
    public void addLogListener(Consumer<String> c) { logListeners.add(c); }

    public Map<Integer, String> getActiveSessions() { return Collections.emptyMap(); }
    public List<String>         getWaitingQueue()   { return Collections.emptyList(); }
    public int                  getAvailablePermits() { return MAX_USERS; }
}
