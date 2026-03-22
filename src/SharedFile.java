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
public class SharedFile {

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

    // acquires the shared read lock — blocks only if a writer currently holds it
    public void startRead(int userId) {
        readLock.lock();
        currentReaders.add(userId);
    }

    // reads and returns the full file contents from disk
    public String getContent() {
        try { return new String(Files.readAllBytes(filePath)); }
        catch (IOException e) { return "[Error reading file: " + e.getMessage() + "]"; }
    }

    // releases the read lock for this user
    public void stopRead(int userId) {
        if (currentReaders.remove(userId))
            readLock.unlock();
    }

    // ── write ────────────────────────────────────────────────────────────────

    // acquires the exclusive write lock — blocks until all readers and writers have released
    public void startWrite(int userId) {
        writeLock.lock();
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
}