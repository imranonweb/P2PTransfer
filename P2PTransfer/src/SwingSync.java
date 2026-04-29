import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public final class SwingSync {
    private SwingSync() {
    }

    public static boolean askAccept(String message) {
        Integer choice = callOnEdt(() -> JOptionPane.showConfirmDialog(
                null,
                message,
                "Pair Request",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        ));
        return choice != null && choice == JOptionPane.YES_OPTION;
    }

    public static <T> T callOnEdt(Callable<T> callable) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    value.set(callable.call());
                } catch (Throwable t) {
                    error.set(t);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        return value.get();
    }
}
