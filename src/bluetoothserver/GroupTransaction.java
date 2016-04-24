package reportserver;


import java.util.LinkedList;
import java.util.NoSuchElementException;

public class GroupTransaction {
    private LinkedList<SimpleTransaction> groupTransaction = new LinkedList<SimpleTransaction>();
    private TransactionTimer groupTransactionTimeout = new TransactionTimer(5000);
    private GroupTransactionCallback callbackTransaction;

    public enum State {
        EMPTY,
        ACTIVE,
        DONE,
        ERROR
    };

    private State groupTransactionState = State.EMPTY;;

    GroupTransaction() {

    }

    public State getState() {
        return groupTransactionState;
    }

    public void setState(State state) {
        groupTransactionState = state;
    }

    public TransactionTimer getTimer() {
        return groupTransactionTimeout;
    }

    public boolean isEmpty() {
        return groupTransaction.isEmpty();
    }

    public void initSendingProcess() {
        groupTransactionTimeout.refreshTransactionTimeout();
        groupTransactionTimeout.start();
        groupTransactionState = State.ACTIVE;
    }

    public SimpleTransaction getFirst() throws NoSuchElementException {
        return groupTransaction.getFirst();
    }

    public boolean add(SimpleTransaction t) {
        return groupTransaction.add(t);
    }

    public boolean addAll(LinkedList<SimpleTransaction> list) {
        return groupTransaction.addAll(list);
    }

    public void remove() {
        groupTransaction.remove();
    }

    public GroupTransactionCallback getCallbacks() {
        return callbackTransaction;
    }

    public void setCallbacks(GroupTransactionCallback callback) {
        callbackTransaction = callback;
    }

    public boolean isComplete() {
        return (groupTransactionState != State.ACTIVE) || (groupTransactionState != State.EMPTY);
    }
}
