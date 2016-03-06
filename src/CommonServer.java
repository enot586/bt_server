package reportserver;

abstract public class CommonServer {

    public enum  ServerState {
        SERVER_NOT_INITIALIZE,
        SERVER_INITIALIZING,
        SERVER_READY_NOT_ACTIVE,
        SERVER_ACTIVE,
        SERVER_STOP,
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
