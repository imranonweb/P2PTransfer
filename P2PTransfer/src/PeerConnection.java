import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class PeerConnection {
    public interface Listener {
        void onStatus(String message);

        void onIncomingFileOffered(String fileName, long sizeBytes);

        void onProgress(String fileName, long transferredBytes, long totalBytes, double bytesPerSec, boolean sending);

        void onCryptoProgress(String fileName, long processedBytes, long totalBytes, boolean sending);

        void onFileCompleted(String fileName, Path localPath, boolean sending);

        void onChatMessage(String message, boolean incoming);

        void onClosed(String reason);
    }

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int AES_KEY_BYTES = 16;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Socket socket;
    private final String peerName;
    private final byte[] aesKey;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Object writeLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile Listener listener;
    private volatile CountDownLatch offerLatch;
    private volatile Boolean offerAccepted;
    private volatile Path receiveDirectory = Paths.get(System.getProperty("user.home"), "Downloads", "P2PTransfer Received");
    private volatile boolean autoAcceptIncoming = true;

    public PeerConnection(Socket socket, String peerName, String sessionCode) throws IOException {
        this.socket = socket;
        this.peerName = peerName;
        this.aesKey = deriveAesKey(Objects.requireNonNull(sessionCode, "sessionCode"));
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    public String getPeerName() {
        return peerName;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setReceiveDirectory(Path receiveDirectory) {
        if (receiveDirectory != null) {
            this.receiveDirectory = receiveDirectory;
        }
    }

    public void setAutoAcceptIncoming(boolean autoAcceptIncoming) {
        this.autoAcceptIncoming = autoAcceptIncoming;
    }

    public void start() {
        Thread thread = new Thread(this::readLoop, "peer-reader");
        thread.setDaemon(true);
        thread.start();
    }

    public void sendChatMessage(String message) throws IOException {
        if (message == null || message.isBlank()) {
            return;
        }
        synchronized (writeLock) {
            out.writeByte(Protocol.CHAT_MESSAGE);
            out.writeUTF(message);
            out.flush();
        }
    }

    public void sendFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        java.io.File f = file.toFile();
        if (!f.exists() || !f.isFile()) {
            throw new IOException("Invalid file: " + f.getAbsolutePath());
        }
        long total = f.length();
        synchronized (writeLock) {
            out.writeByte(Protocol.FILE_OFFER);
            out.writeUTF(f.getName());
            out.writeLong(total);
            out.flush();
        }
        notifyStatus("Sent file offer: " + f.getName());
        CountDownLatch latch = new CountDownLatch(1);
        offerLatch = latch;
        offerAccepted = null;
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for receiver", e);
        } finally {
            offerLatch = null;
        }
        if (!Boolean.TRUE.equals(offerAccepted)) {
            throw new IOException("Receiver declined transfer");
        }

        byte[] iv = new byte[GCM_IV_BYTES];
        RANDOM.nextBytes(iv);
        Cipher encryptCipher = newCipher(Cipher.ENCRYPT_MODE, iv);
        long encryptedPayloadSize = total + GCM_TAG_BYTES;

        synchronized (writeLock) {
            out.writeByte(Protocol.FILE_STREAM);
            out.writeLong(total);
            out.writeInt(iv.length);
            out.write(iv);
            out.writeLong(encryptedPayloadSize);

            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long plainProcessed = 0L;
                long encryptedWritten = 0L;
                Instant start = Instant.now();
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    byte[] encrypted = encryptCipher.update(buffer, 0, read);
                    if (encrypted != null && encrypted.length > 0) {
                        out.write(encrypted);
                        encryptedWritten += encrypted.length;
                    }
                    plainProcessed += read;
                    notifyCryptoProgress(f.getName(), plainProcessed, total, true);
                    notifyProgress(f.getName(), plainProcessed, total, start, true);
                }

                byte[] finalChunk;
                try {
                    finalChunk = encryptCipher.doFinal();
                } catch (GeneralSecurityException e) {
                    throw new IOException("Encryption failed", e);
                }
                if (finalChunk.length > 0) {
                    out.write(finalChunk);
                    encryptedWritten += finalChunk.length;
                }
                out.flush();

                if (encryptedWritten != encryptedPayloadSize) {
                    throw new IOException("Encrypted payload size mismatch");
                }
            }
        }
        notifyCryptoProgress(f.getName(), total, total, true);
        notifyStatus("File sent: " + f.getName());
        notifyFileCompleted(f.getName(), f.toPath(), true);
    }

    private void readLoop() {
        try {
            while (!closed.get()) {
                byte type = in.readByte();
                switch (type) {
                    case Protocol.FILE_OFFER -> handleOffer();
                    case Protocol.FILE_RESPONSE -> handleOfferResponse();
                    case Protocol.FILE_STREAM -> handleIncomingStream();
                    case Protocol.CHAT_MESSAGE -> handleChatMessage();
                    default -> throw new IOException("Unknown message type: " + type);
                }
            }
        } catch (EOFException ignored) {
            close("Disconnected");
        } catch (IOException e) {
            close("Connection error: " + e.getMessage());
        }
    }

    private void handleOffer() throws IOException {
        String fileName = in.readUTF();
        long size = in.readLong();
        notifyIncomingOffer(fileName, size);
        TransferDecision decision = buildReceiveDecision(fileName);
        synchronized (writeLock) {
            out.writeByte(Protocol.FILE_RESPONSE);
            out.writeBoolean(decision.accepted());
            out.flush();
        }
        if (!decision.accepted()) {
            notifyStatus("Declined file: " + fileName);
            return;
        }
        pendingFileName = fileName;
        pendingFilePath = decision.targetPath();
        pendingFileSize = size;
        notifyStatus("Accepted file: " + fileName);
    }

    private volatile String pendingFileName;
    private volatile Path pendingFilePath;
    private volatile long pendingFileSize;

    private void handleOfferResponse() throws IOException {
        boolean accepted = in.readBoolean();
        offerAccepted = accepted;
        CountDownLatch latch = offerLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    private void handleIncomingStream() throws IOException {
        long announcedSize = in.readLong();
        int ivLength = in.readInt();
        if (ivLength <= 0 || ivLength > 32) {
            throw new IOException("Invalid encryption IV length");
        }
        byte[] iv = new byte[ivLength];
        in.readFully(iv);
        long encryptedPayloadSize = in.readLong();
        if (encryptedPayloadSize < GCM_TAG_BYTES) {
            throw new IOException("Invalid encrypted payload size");
        }

        String fileName = pendingFileName;
        Path target = pendingFilePath;
        long expectedSize = pendingFileSize;
        if (fileName == null || target == null) {
            throw new IOException("Incoming file stream without accepted offer");
        }
        if (announcedSize != expectedSize) {
            throw new IOException("File size mismatch");
        }

        Cipher decryptCipher = newCipher(Cipher.DECRYPT_MODE, iv);

        try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long encryptedRead = 0L;
            long plainReceived = 0L;
            Instant start = Instant.now();
            while (encryptedRead < encryptedPayloadSize) {
                int remaining = (int) Math.min(buffer.length, encryptedPayloadSize - encryptedRead);
                int read = in.read(buffer, 0, remaining);
                if (read == -1) {
                    throw new EOFException("Unexpected end of stream");
                }
                encryptedRead += read;

                byte[] decrypted = decryptCipher.update(buffer, 0, read);
                if (decrypted != null && decrypted.length > 0) {
                    fos.write(decrypted);
                    plainReceived += decrypted.length;
                }

                long clampedPlain = Math.min(plainReceived, announcedSize);
                notifyCryptoProgress(fileName, clampedPlain, announcedSize, false);
                notifyProgress(fileName, clampedPlain, announcedSize, start, false);
            }

            byte[] finalChunk;
            try {
                finalChunk = decryptCipher.doFinal();
            } catch (GeneralSecurityException e) {
                throw new IOException("Decryption failed", e);
            }
            if (finalChunk.length > 0) {
                fos.write(finalChunk);
                plainReceived += finalChunk.length;
            }

            if (plainReceived != announcedSize) {
                throw new IOException("Decrypted file size mismatch");
            }
        } finally {
            pendingFileName = null;
            pendingFilePath = null;
            pendingFileSize = 0L;
        }
        notifyCryptoProgress(fileName, announcedSize, announcedSize, false);
        notifyStatus("Received file: " + fileName);
        notifyFileCompleted(fileName, target, false);
    }

    private void handleChatMessage() throws IOException {
        String message = in.readUTF();
        Listener l = listener;
        if (l != null) {
            l.onChatMessage(message, true);
        }
    }

    private TransferDecision buildReceiveDecision(String fileName) {
        if (!autoAcceptIncoming) {
            return new TransferDecision(false, null);
        }
        try {
            Files.createDirectories(receiveDirectory);
            Path target = resolveUniquePath(receiveDirectory, fileName);
            return new TransferDecision(true, target);
        } catch (IOException e) {
            notifyStatus("Failed to prepare save path: " + e.getMessage());
            return new TransferDecision(false, null);
        }
    }

    public void close(String reason) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        Listener l = listener;
        if (l != null) {
            l.onClosed(reason);
        }
    }

    private void notifyStatus(String message) {
        Listener l = listener;
        if (l != null) {
            l.onStatus(message);
        }
    }

    private void notifyIncomingOffer(String fileName, long sizeBytes) {
        Listener l = listener;
        if (l != null) {
            l.onIncomingFileOffered(fileName, sizeBytes);
        }
    }

    private void notifyProgress(String fileName, long transferred, long total, Instant start, boolean sending) {
        Listener l = listener;
        if (l == null) {
            return;
        }
        double seconds = Math.max(0.001, Duration.between(start, Instant.now()).toMillis() / 1000.0);
        double bytesPerSecond = transferred / seconds;
        l.onProgress(fileName, transferred, total, bytesPerSecond, sending);
    }

    private void notifyCryptoProgress(String fileName, long processed, long total, boolean sending) {
        Listener l = listener;
        if (l == null) {
            return;
        }
        l.onCryptoProgress(fileName, processed, total, sending);
    }

    private void notifyFileCompleted(String fileName, Path localPath, boolean sending) {
        Listener l = listener;
        if (l == null) {
            return;
        }
        l.onFileCompleted(fileName, localPath, sending);
    }

    private static Path resolveUniquePath(Path directory, String fileName) {
        Path candidate = directory.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        int index = 1;
        while (true) {
            Path alternative = directory.resolve(base + "(" + index + ")" + ext);
            if (!Files.exists(alternative)) {
                return alternative;
            }
            index++;
        }
    }

    public static String humanSize(long size) {
        double mb = size / 1024.0 / 1024.0;
        if (mb < 1.0) {
            double kb = size / 1024.0;
            return String.format("%.2f KB", kb);
        }
        return String.format("%.2f MB", mb);
    }

    private static byte[] deriveAesKey(String sessionCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sessionCode.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(hash, AES_KEY_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private Cipher newCipher(int mode, byte[] iv) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BYTES * 8, iv);
            cipher.init(mode, new SecretKeySpec(aesKey, "AES"), spec);
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new IOException("Unable to initialize AES cipher", e);
        }
    }

    private record TransferDecision(boolean accepted, Path targetPath) {
    }
}
