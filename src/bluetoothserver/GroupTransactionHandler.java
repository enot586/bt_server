package reportserver;

public class GroupTransactionHandler {

    private CommonServer sv;

    GroupTransactionHandler(CommonServer sv_) {
        sv = sv_;
    }

    public void responsePacketHandler(GroupTransaction t) {
        if (t.getState() == GroupTransaction.State.ACTIVE) {
            if (!t.isEmpty()) {
                if (sv.sendData(t.getFirst())) {
                    t.getTimer().refreshTransactionTimeout();

                    if (!t.getFirst().getHeader().containsKey("type")) {
                        return;
                    }

                    int type = ((Long)t.getFirst().getHeader().get("type")).intValue();

                    if ((BluetoothPacketType.SESSION_CLOSE.getId() == type) ||
                            (BluetoothPacketType.END_TRANSACTION.getId() == type)) {
                        t.getTimer().stop();
                        t.setState(GroupTransaction.State.DONE);

                        if (t.getCallbacks() != null) {
                            t.getCallbacks().success();
                        }
                    }
                    t.remove();
                }
            } else {
                t.getTimer().stop();
                t.setState(GroupTransaction.State.DONE);

                if (t.getCallbacks() != null) {
                    t.getCallbacks().success();
                }
            }
        }
    }

    public void timerHandler(GroupTransaction t) {
        if ((t.getState() == GroupTransaction.State.ACTIVE) && t.getTimer().isTransactionTimeout()) {
            t.setState(GroupTransaction.State.ERROR);
            t.getTimer().stop();

            if (t.getCallbacks() != null) {
                t.getCallbacks().fail();
            }
        }
    }
}
