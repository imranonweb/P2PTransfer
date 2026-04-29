public record DiscoveredPeer(String displayName, String ipAddress, int port, long lastSeenMillis) {
    @Override
    public String toString() {
        return displayName + "  [" + ipAddress + "]";
    }
}
