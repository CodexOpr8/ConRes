# ConRes — Concurrent Resource Access & Synchronisation Engine

**Module:** 6CM604 Design and Implementation of Concurrent Systems  
**Student ID:** 100717312  
**Language:** Java 17 · Swing GUI  

---

## Overview

ConRes is a Java desktop application that demonstrates concurrent resource access and synchronisation in a multi-user environment. It simulates multiple users competing to read and write a shared file (`ProductSpecification.txt`), enforcing thread-safety through semaphores, read/write locks, and a producer-consumer queue.

The system consists of two windows:

- **Admin Dashboard** — live monitoring view showing active sessions, the waiting queue, lock state, and a real-time event log
- **User Portal** — per-user session window with login, file read, and file write functionality

---

## Features

- **Semaphore-enforced session limit** — maximum 4 concurrent users; additional users are queued and unblocked in FIFO order when a slot frees
- **Multiple readers, single writer** — `ReentrantReadWriteLock` allows concurrent reads but gives exclusive access to writers
- **Timeout-based locking** — `tryLock(15s)` prevents any thread blocking forever; throws `LockTimeoutException` if the lock isn't granted in time
- **60-second hold countdown** — a Swing timer auto-releases the lock at zero via the normal release path
- **Queue removal on window close** — closing a queued window immediately removes the user from the waiting queue
- **Atomic duplicate rejection** — a `synchronized` check-then-enqueue block prevents the same user logging in twice concurrently
- **Race condition protection** — `windowClosed` and `loggedIn` volatile flags prevent semaphore over-release when a window is closed mid-login
- **Observer pattern** — `ChangeListener` and `LogListener` keep the admin dashboard in sync with backend state without any polling

---

## Architecture

```
Main
 └── ConResSystem          (backend controller)
      ├── Semaphore         (session slot enforcement, fair/FIFO)
      ├── ConcurrentHashMap (active session tracking)
      ├── LinkedBlockingQueue (waiting queue)
      └── SharedFile        (ReentrantReadWriteLock wrapping ProductSpecification.txt)

AdminWindow                 (observer — subscribes to ConResSystem)
UserWindow (×N)             (one per session, single worker thread each)
UserDatabase                (static credential store)
```

### Threading model

Each `UserWindow` has a dedicated single-thread `ExecutorService`. All blocking operations — `semaphore.acquire()`, `tryLock()` — run on this worker thread, never on the Swing EDT. UI updates are always dispatched back via `SwingUtilities.invokeLater()`.

### Deadlock avoidance

Two mechanisms are in place:

1. **Consistent lock acquisition order** — the semaphore is always acquired *before* any file lock, eliminating circular-wait (one of Coffman's four necessary conditions for deadlock)
2. **Timeout-based locking** — `tryLock(ACQUIRE_TIMEOUT_SECONDS)` ensures no thread can block indefinitely

---

## Project Structure

```
src/
└── conres/
    ├── Main.java           entry point
    ├── ConResSystem.java   session + file access controller
    ├── SharedFile.java     ReentrantReadWriteLock wrapper
    ├── UserDatabase.java   credential store
    ├── AdminWindow.java    live monitoring dashboard
    └── UserWindow.java     per-user session portal
```

---

## Getting Started

### Requirements

- Java 17 or later
- Any standard Java IDE (IntelliJ IDEA, Eclipse, NetBeans) or `javac` via terminal

### Running

**From an IDE:**  
Open the project, set `conres.Main` as the run configuration, and run.

**From terminal:**
```bash
javac -d out src/*.java
java -cp out conres.Main
```

### Default credentials

| Username | Password      |
|----------|---------------|
| karanjot | karanjot123   |
| billy    | billy123      |
| paul     | paul123       |
| phil     | phil123       |
| dan      | dan123        |
| mike     | mike123       |
| shaun    | shaun123      |
| dean     | dean123       |

---

## How to Use

1. Launch the application — the **Admin Dashboard** opens automatically
2. Click **＋ Open User Session** to open a user portal window (open multiple to test concurrency)
3. Log in with any credentials from the table above
4. Once logged in, use **Open File (Read)** or **Open File (Write)** to access the shared file
5. The admin dashboard updates in real time — watch the session slots, lock status, and event log as users interact
6. To test the semaphore queue, open 5+ windows and log in with different users — the 5th will be held in the waiting queue until one of the active users logs out
