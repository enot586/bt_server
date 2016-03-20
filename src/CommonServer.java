package reportserver;

abstract class CommonServer {

    public enum  ServerState {
        SERVER_INITIALIZING,
        SERVER_ACTIVE,
        SERVER_STOPPED,
    }

    private ServerState state;

    abstract public void start() throws Exception;
    abstract public void stop() throws Exception;

    synchronized public void setState(ServerState state_ ) {
        state = state_;
    }

    synchronized public ServerState getServerState() {
        return state;
    }
}
