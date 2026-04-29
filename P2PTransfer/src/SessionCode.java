import java.security.SecureRandom;

public final class SessionCode {
    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionCode() {
    }

    public static String generate() {
        int value = RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }
}
