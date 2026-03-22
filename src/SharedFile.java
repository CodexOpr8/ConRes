package conres;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

// wraps productspecification.txt with a fair reentrantreadwritelock.
// enforces the "multiple readers, single writer" rule:
//   - multiple users can hold the read lock at the same time (shared)
//   - only one user can hold the write lock (exclusive — blocks all readers and writers)
// the lock is held open between start/stop calls so the admin dashboard
// can see who is actively reading or writing at any given moment.
//
// deadlock avoidance — two mechanisms:
//   1. consistent lock acquisition order: the semaphore is always acquired before
//      any file lock (enforced in conressystem). this eliminates circular-wait —
//      one of coffman's four necessary conditions for deadlock.
//   2. timeout-based locking: startread() and startwrite() use trylock() instead of
//      lock(). if a lock cannot be granted within the timeout, a locktimeoutexception
//      is thrown rather than blocking forever.
public class SharedFile {

    // maximum seconds to wait for a lock to be granted before giving up
    public static final int ACQUIRE_TIMEOUT_SECONDS = 15;

    // maximum seconds a user may hold a lock before the userwindow countdown auto-closes
    public static final int HOLD_TIMEOUT_SECONDS = 60;

    // thrown when trylock does not succeed within acquire_timeout_seconds
    public static class LockTimeoutException extends Exception {
        public LockTimeoutException(String msg) { super(msg); }
    }

    private final String fileName;
    private final Path   filePath;

    // fair=true means threads acquire locks in arrival order (fifo), preventing starvation
    private final ReentrantReadWriteLock rwLock    = new ReentrantReadWriteLock(true);
    private final Lock                   readLock  = rwLock.readLock();
    private final Lock                   writeLock = rwLock.writeLock();

    // thread-safe tracking of which users currently hold locks, used by the dashboard
    private final Set<Integer> currentReaders = ConcurrentHashMap.newKeySet();
    private volatile int       currentWriter  = -1;

    // creates the file with default content if it doesn't already exist on disk
    public SharedFile(String fileName) {
        this.fileName = fileName;
        this.filePath = Paths.get(fileName);
        if (!Files.exists(filePath)) {
            try {
                Files.write(filePath, (
                    "ProductSpecification v1.0\n" +
                    "==========================\n" +
                    "Component : Trent XWB Engine\n" +
                    "Thrust    : 84,000 lbf\n" +
                    "BPR       : 9.3\n" +
                    "OPR       : 50\n" +
                    "Material  : Nickel superalloy\n" +
                    "Status    : In-Service\n"
                ).getBytes());
            } catch (IOException e) {
                System.err.println("Could not create file: " + e.getMessage());
            }
        }
    }

    // ── read ─────────────────────────────────────────────────────────────────

    // tries to acquire the shared read lock within the timeout period.
    // throws locktimeoutexception if a writer is still holding the lock after 15 seconds.
    public void startRead(int userId) throws LockTimeoutException, InterruptedException {
        boolean acquired = readLock.tryLock(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!acquired) {
            throw new LockTimeoutException(
                "Could not acquire read lock within " + ACQUIRE_TIMEOUT_SECONDS + " seconds. " +
                "A user is currently writing. Please try again shortly.");
        }
        currentReaders.add(userId);
    }

    // reads and returns the full file contents from disk
    public String getContent() {
        try { return new String(Files.readAllBytes(filePath)); }
        catch (IOException e) { return "[Error reading file: " + e.getMessage() + "]"; }
    }

    // releases the read lock for this user
    public void stopRead(int userId) {
        if (currentReaders.remove(userId)) readLock.unlock();
    }

    // ── write ────────────────────────────────────────────────────────────────

    // tries to acquire the exclusive write lock within the timeout period.
    // throws locktimeoutexception if readers or another writer don't release in time.
    public void startWrite(int userId) throws LockTimeoutException, InterruptedException {
        boolean acquired = writeLock.tryLock(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!acquired) {
            throw new LockTimeoutException(
                "Could not acquire write lock within " + ACQUIRE_TIMEOUT_SECONDS + " seconds. " +
                "Other users are currently reading or writing. Please try again shortly.");
        }
        currentWriter = userId;
    }

    // writes the new content to disk, then releases the write lock regardless of success
    public void commitWrite(int userId, String content) {
        try { Files.write(filePath, content.getBytes()); }
        catch (IOException e) { System.err.println("Write error: " + e.getMessage()); }
        finally { currentWriter = -1; writeLock.unlock(); }
    }

    // releases the write lock without saving any changes
    public void cancelWrite(int userId) {
        currentWriter = -1;
        writeLock.unlock();
    }

    public String       getFileName()       { return fileName; }
    public Set<Integer> getCurrentReaders() { return Collections.unmodifiableSet(currentReaders); }
    public int          getCurrentWriter()  { return currentWriter; }
    public int          getAcquireTimeout() { return ACQUIRE_TIMEOUT_SECONDS; }
    public int          getHoldTimeout()    { return HOLD_TIMEOUT_SECONDS; }
}