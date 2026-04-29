public final class Protocol {
    public static final int PORT = 44550;
    public static final int DISCOVERY_PORT = 44551;
    public static final String DISCOVERY_MAGIC = "P2PTRANSFER_V1";

    public static final byte PAIR_REQUEST = 1;
    public static final byte PAIR_ACCEPT = 2;
    public static final byte PAIR_REJECT = 3;
    public static final byte FILE_OFFER = 4;
    public static final byte FILE_RESPONSE = 5;
    public static final byte FILE_STREAM = 6;
    public static final byte CHAT_MESSAGE = 7;

    private Protocol() {
    }
}
