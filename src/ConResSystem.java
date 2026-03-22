package conres;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

// central controller for the concurrent resource access engine.
// enforces:
//   - maximum n=4 concurrent users via a fair counting semaphore
//   - extra users are automatically queued in a linkedblockingqueue until a slot frees
//   - thread-safe session tracking via concurrenthashmap
//   - observer pattern: change listeners and log listeners let the ui react to backend
//     state changes without any coupling to swing
public class ConResSystem {

    public static final int MAX_USERS = 4;

    // fair=true means the semaphore grants permits in fifo order, preventing starvation
    private final Semaphore sessionSemaphore = new Semaphore(MAX_USERS, true);

    // thread-safe map of userid -> username for all currently active sessions
    private final ConcurrentHashMap<Integer, String> activeSessions = new ConcurrentHashMap<>();

    // thread-safe fifo queue of usernames waiting for a semaphore permit
    private final LinkedBlockingQueue<String> waitingQueue = new LinkedBlockingQueue<>();

    private final SharedFile sharedFile = new SharedFile("ProductSpecification.txt");

    // observer lists — notified whenever state changes so the ui can refresh
    private final List<Runnable>         changeListeners = Collections.synchronizedList(new ArrayList<>());
    private final List<Consumer<String>> logListeners    = Collections.synchronizedList(new ArrayList<>());

    public void addChangeListener(Runnable r)      { changeListeners.add(r); }
    public void addLogListener(Consumer<String> c) { logListeners.add(c); }

    private void notifyChange() { changeListeners.forEach(Runnable::run); }
    private void log(String msg) { logListeners.forEach(c -> c.accept(msg)); }

    // ── session control ──────────────────────────────────────────────────────

    // blocks here if 4 users are already active — the calling thread waits on the semaphore
    public void login(String username, int userId) throws InterruptedException {
        waitingQueue.offer(username);
        log("LOGIN REQUEST  — " + username + " (ID " + userId + ")  |  waiting for session slot...");
        notifyChange();

        sessionSemaphore.acquire();

        waitingQueue.remove(username);
        activeSessions.put(userId, username);
        log("LOGIN SUCCESS  — " + username + " (ID " + userId + ")  |  active: " + activeSessions.size() + " / " + MAX_USERS);
        notifyChange();
    }

    // removes the user from active sessions and releases one permit for the next waiter
    public void logout(String username, int userId) {
        activeSessions.remove(userId);
        sessionSemaphore.release();
        log("LOGOUT         — " + username + " (ID " + userId + ")  |  active: " + activeSessions.size() + " / " + MAX_USERS);
        notifyChange();
    }

    // ── file access ──────────────────────────────────────────────────────────

    // acquires a shared read lock then returns the file contents
    public String startRead(int userId) {
        String uname = activeSessions.getOrDefault(userId, "ID:" + userId);
        log("READ REQUEST   — " + uname + " (ID " + userId + ")  |  acquiring shared read lock...");
        sharedFile.startRead(userId);
        log("READ ACTIVE    — " + uname + " (ID " + userId + ")  |  read lock held");
        notifyChange();
        return sharedFile.getContent();
    }

    // releases the shared read lock held by this user
    public void stopRead(int userId) {
        String uname = activeSessions.getOrDefault(userId, "ID:" + userId);
        sharedFile.stopRead(userId);
        log("READ CLOSED    — " + uname + " (ID " + userId + ")  |  read lock released");
        notifyChange();
    }

    // acquires the exclusive write lock — blocks until all readers and other writers finish
    public void startWrite(int userId) {
        String uname = activeSessions.getOrDefault(userId, "ID:" + userId);
        log("WRITE REQUEST  — " + uname + " (ID " + userId + ")  |  acquiring exclusive write lock...");
        sharedFile.startWrite(userId);
        log("WRITE ACTIVE   — " + uname + " (ID " + userId + ")  |  exclusive write lock held — all others blocked");
        notifyChange();
    }

    // saves the new content to disk and releases the write lock
    public void commitWrite(int userId, String content) {
        String uname = activeSessions.getOrDefault(userId, "ID:" + userId);
        sharedFile.commitWrite(userId, content);
        log("WRITE SAVED    — " + uname + " (ID " + userId + ")  |  write lock released");
        notifyChange();
    }

    // discards any changes and releases the write lock without saving
    public void cancelWrite(int userId) {
        String uname = activeSessions.getOrDefault(userId, "ID:" + userId);
        sharedFile.cancelWrite(userId);
        log("WRITE CANCEL   — " + uname + " (ID " + userId + ")  |  write lock released (no changes saved)");
        notifyChange();
    }

    // ── accessors ────────────────────────────────────────────────────────────

    public Map<Integer, String> getActiveSessions()   { return Collections.unmodifiableMap(activeSessions); }
    public List<String>         getWaitingQueue()     { return new ArrayList<>(waitingQueue); }
    public SharedFile           getSharedFile()       { return sharedFile; }
    public int                  getAvailablePermits() { return sessionSemaphore.availablePermits(); }
}