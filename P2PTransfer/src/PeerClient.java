import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public final class PeerClient {
    private PeerClient() {
    }

    public static PeerConnection connect(String hostIp, String code, String displayName) throws IOException {
        return connect(hostIp, Protocol.PORT, code, displayName);
    }

    public static PeerConnection connect(String hostIp, int hostPort, String code, String displayName) throws IOException {
        Socket socket = new Socket(hostIp, hostPort);
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        out.writeByte(Protocol.PAIR_REQUEST);
        out.writeUTF(code);
        out.writeUTF(displayName);
        out.flush();

        byte type = in.readByte();
        if (type == Protocol.PAIR_REJECT) {
            String reason = in.readUTF();
            socket.close();
            throw new IOException("Pair rejected: " + reason);
        }
        if (type != Protocol.PAIR_ACCEPT) {
            socket.close();
            throw new IOException("Unexpected response from host");
        }
        String peerName = in.readUTF();
        return new PeerConnection(socket, peerName, code);
    }
}
