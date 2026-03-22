package conres;

import java.util.*;

// simulated credential store with static, pre-assigned username/id/password pairs.
// authenticate() returns the matching userrecord if credentials are correct, or null if not.
// password hashing and encryption are intentionally omitted as per the project brief.
public class UserDatabase {

    // holds the user's id and password — username is the map key
    public static class UserRecord {
        public final int    id;
        public final String password;
        public UserRecord(int id, String password) {
            this.id = id;
            this.password = password;
        }
    }

    // static list of all valid users — pattern is username123 for each password
    private static final Map<String, UserRecord> USERS = new LinkedHashMap<>();

    static {
        USERS.put("karanjot", new UserRecord(1001, "karanjot123"));
        USERS.put("billy",    new UserRecord(1002, "billy123"));
        USERS.put("paul",     new UserRecord(1003, "paul123"));
        USERS.put("phil",     new UserRecord(1004, "phil123"));
        USERS.put("dan",      new UserRecord(1005, "dan123"));
        USERS.put("mike",     new UserRecord(1006, "mike123"));
        USERS.put("shaun",    new UserRecord(1007, "shaun123"));
        USERS.put("dean",     new UserRecord(1008, "dean123"));
    }

    // returns the userrecord if username and password match, otherwise null
    public static UserRecord authenticate(String username, String password) {
        UserRecord r = USERS.get(username.toLowerCase().trim());
        if (r != null && r.password.equals(password)) return r;
        return null;
    }

    public static Map<String, UserRecord> getAllUsers() {
        return Collections.unmodifiableMap(USERS);
    }
}