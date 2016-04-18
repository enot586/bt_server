package reportserver;


import java.util.LinkedList;
import java.util.NoSuchElementException;

public class GroupTransaction {
    private LinkedList<BluetoothSimpleTransaction> groupTransaction = new LinkedList<BluetoothSimpleTransaction>();
    private TransactionTimer groupTransactionTimeout = new TransactionTimer(5000);
    private BluetoothServer bt;
    private GroupTransactionCallback callbackTransaction;

    private enum State {
        EMPTY,
        ACTIVE,
        DONE,
        ERROR
    };

    private State groupTransactionState = State.EMPTY;;

    GroupTransaction(BluetoothServer bt_) {
        bt = bt_;
    }

    public void responseHandler() {
        if (groupTransactionState == State.ACTIVE) {
            if (!groupTransaction.isEmpty()) {
                if (bt.sendData(groupTransaction.getFirst())) {
                    groupTransactionTimeout.refreshTransactionTimeout();
                    if (BluetoothPacketType.SESSION_CLOSE == groupTransaction.getFirst().getHeader().get("type")) {
                        groupTransactionTimeout.stop();
                        groupTransactionState = State.DONE;

                        if (callbackTransaction != null) {
                            callbackTransaction.success();
                        }
                    }
                    groupTransaction.remove();
                }
            } else {
                groupTransactionTimeout.stop();
                groupTransactionState = State.DONE;

                if (callbackTransaction != null) {
                    callbackTransaction.success();
                }
            }
        }
    }

    public boolean send() {
        try {
            if (bt.sendData(groupTransaction.getFirst())) {
                groupTransaction.remove();
                groupTransactionTimeout.refreshTransactionTimeout();
                groupTransactionTimeout.start();
                groupTransactionState = State.ACTIVE;
                return true;
            }
        } catch (NoSuchElementException e) {

        }

        return false;
    }

    public void handler() {
        if ((groupTransactionState == State.ACTIVE) && groupTransactionTimeout.isTransactionTimeout()) {
            groupTransactionState = State.ERROR;
            groupTransactionTimeout.stop();

            if (callbackTransaction != null) {
                callbackTransaction.fail();
            }
        }
    }

    public boolean add(BluetoothSimpleTransaction t) {
        return groupTransaction.add(t);
    }

    public boolean addAll(LinkedList<BluetoothSimpleTransaction> list) {
        return groupTransaction.addAll(list);
    }

    public void setCallbacks(GroupTransactionCallback callback) {
        callbackTransaction = callback;
    }

    public boolean isComplete() {
        return (groupTransactionState != State.ACTIVE) || (groupTransactionState != State.EMPTY);
    }
}
