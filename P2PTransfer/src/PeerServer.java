import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public class PeerServer {
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public synchronized void start(String sessionCode,
                                   String hostName,
                                   Consumer<PeerConnection> onAccepted,
                                   Consumer<String> onStatus) throws IOException {
        stop();
        serverSocket = new ServerSocket(Protocol.PORT);
        acceptThread = new Thread(() -> acceptLoop(sessionCode, hostName, onAccepted, onStatus), "accept-thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop(String sessionCode,
                            String hostName,
                            Consumer<PeerConnection> onAccepted,
                            Consumer<String> onStatus) {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                byte type = in.readByte();
                if (type != Protocol.PAIR_REQUEST) {
                    socket.close();
                    continue;
                }
                String submittedCode = in.readUTF();
                String peerName = in.readUTF();
                if (!sessionCode.equals(submittedCode)) {
                    out.writeByte(Protocol.PAIR_REJECT);
                    out.writeUTF("Invalid session code");
                    out.flush();
                    socket.close();
                    onStatus.accept("Rejected connection from " + peerName + " (wrong code)");
                    continue;
                }

                out.writeByte(Protocol.PAIR_ACCEPT);
                out.writeUTF(hostName);
                out.flush();
                PeerConnection connection = new PeerConnection(socket, peerName, submittedCode);
                onAccepted.accept(connection);
                onStatus.accept("Connected to " + peerName);
            } catch (IOException e) {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    onStatus.accept("Server error: " + e.getMessage());
                }
                break;
            }
        }
    }

    public synchronized void stop() {
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
    }
}
