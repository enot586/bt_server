package reportserver;


import java.util.LinkedList;
import java.util.NoSuchElementException;

public class GroupTransaction {
    private LinkedList<BluetoothSimpleTransaction> groupTransaction = new LinkedList<BluetoothSimpleTransaction>();
    private TransactionTimer groupTransactionTimeout = new TransactionTimer(5000);
    private GroupTransactionCallback callbackTransaction;

    private enum State {
        EMPTY,
        ACTIVE,
        DONE,
        ERROR
    };

    private State groupTransactionState = State.EMPTY;;

    GroupTransaction() {

    }

    public void initSendingProcess() {
        groupTransactionTimeout.refreshTransactionTimeout();
        groupTransactionTimeout.start();
        groupTransactionState = State.ACTIVE;
    }

    public BluetoothSimpleTransaction getFirst() throws NoSuchElementException {
        return groupTransaction.getFirst();
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

    public void remove() {
        groupTransaction.remove();
    }

    public void setCallbacks(GroupTransactionCallback callback) {
        callbackTransaction = callback;
    }

    public boolean isComplete() {
        return (groupTransactionState != State.ACTIVE) || (groupTransactionState != State.EMPTY);
    }

    public void responseHandler(BluetoothServer bt) {
        if (groupTransactionState == State.ACTIVE) {
            if (!groupTransaction.isEmpty()) {
                if (bt.sendData(groupTransaction.getFirst())) {
                    groupTransactionTimeout.refreshTransactionTimeout();
                    int type = ((Long)groupTransaction.getFirst().getHeader().get("type")).intValue();

                    if ((BluetoothPacketType.SESSION_CLOSE.getId() == type) ||
                            (BluetoothPacketType.END_TRANSACTION.getId() == type)) {
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
}
