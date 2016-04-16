package reportserver;


import java.util.Timer;

public class TransactionTimer {
    private long timeInterval = 0;
    private long timeoutTime = 0;
    private boolean isActive = false;

    TransactionTimer(long timeInterval_) {
        timeInterval = timeInterval_;
        refreshTransactionTimeout();
    }

    public void refreshTransactionTimeout() {
        timeoutTime = System.currentTimeMillis()+timeInterval;
    }

    public boolean isTransactionTimeout() {
        return (isActive && (System.currentTimeMillis() >= timeoutTime));
    }

    public void start() {
        isActive = true;
    }

    public void stop() {
        isActive = false;
    }


}
