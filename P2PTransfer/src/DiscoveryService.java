import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class DiscoveryService {
    private static final int BROADCAST_INTERVAL_MS = 1500;
    private static final int STALE_MS = 6000;

    private final Map<String, DiscoveredPeer> peers = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread senderThread;
    private Thread listenerThread;
    private Thread cleanupThread;

    public synchronized void start(String displayName, int tcpPort, Consumer<List<DiscoveredPeer>> onPeersChanged) throws IOException {
        if (running.get()) {
            return;
        }
        running.set(true);
        senderThread = new Thread(() -> runSender(displayName, tcpPort), "discovery-sender");
        listenerThread = new Thread(() -> runListener(onPeersChanged), "discovery-listener");
        cleanupThread = new Thread(() -> runCleanup(onPeersChanged), "discovery-cleaner");
        senderThread.setDaemon(true);
        listenerThread.setDaemon(true);
        cleanupThread.setDaemon(true);
        senderThread.start();
        listenerThread.start();
        cleanupThread.start();
    }

    private void runSender(String displayName, int tcpPort) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            while (running.get()) {
                String payload = Protocol.DISCOVERY_MAGIC + "|" + sanitize(displayName) + "|" + tcpPort;
                byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(
                        bytes,
                        bytes.length,
                        InetAddress.getByName("255.255.255.255"),
                        Protocol.DISCOVERY_PORT
                );
                socket.send(packet);
                Thread.sleep(BROADCAST_INTERVAL_MS);
            }
        } catch (Exception ignored) {
        }
    }

    private void runListener(Consumer<List<DiscoveredPeer>> onPeersChanged) {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(Protocol.DISCOVERY_PORT));
            socket.setSoTimeout(2000);
            byte[] buf = new byte[512];
            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                } catch (SocketException e) {
                    if (running.get()) {
                        throw e;
                    }
                    return;
                } catch (IOException e) {
                    continue;
                }
                InetAddress source = packet.getAddress();
                if (LocalNet.isLocalAddress(source)) {
                    continue;
                }
                String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                String[] parts = message.split("\\|");
                if (parts.length != 3 || !Protocol.DISCOVERY_MAGIC.equals(parts[0])) {
                    continue;
                }
                int port;
                try {
                    port = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    continue;
                }
                String ip = source.getHostAddress();
                DiscoveredPeer peer = new DiscoveredPeer(parts[1], ip, port, System.currentTimeMillis());
                peers.put(ip, peer);
                onPeersChanged.accept(snapshot());
            }
        } catch (IOException ignored) {
        }
    }

    private void runCleanup(Consumer<List<DiscoveredPeer>> onPeersChanged) {
        while (running.get()) {
            long now = System.currentTimeMillis();
            boolean changed = false;
            for (Map.Entry<String, DiscoveredPeer> entry : peers.entrySet()) {
                if (now - entry.getValue().lastSeenMillis() > STALE_MS) {
                    peers.remove(entry.getKey());
                    changed = true;
                }
            }
            if (changed) {
                onPeersChanged.accept(snapshot());
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private List<DiscoveredPeer> snapshot() {
        List<DiscoveredPeer> list = new ArrayList<>(peers.values());
        list.sort(Comparator.comparing(DiscoveredPeer::displayName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public synchronized void stop() {
        running.set(false);
        if (senderThread != null) {
            senderThread.interrupt();
            senderThread = null;
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
        if (cleanupThread != null) {
            cleanupThread.interrupt();
            cleanupThread = null;
        }
        peers.clear();
    }

    private static String sanitize(String text) {
        return text.replace("|", "").trim();
    }
}
