package reportserver;

public enum BluetoothTransactionStatus {
    DONE      (0),  //Если выполнилось успешно
    NOT_DONE  (1),  //Если принялось но не выполнелось
    ERROR     (2),  //Если плохо принялось
    BUSY      (3);  //Если не может обработать т.к. занят предыдущим запросом <Пока зарезервировано на будущее>

    private int id;

    BluetoothTransactionStatus(int transactionStatus_) {
        this.id = transactionStatus_;
    }

    int getId() {
        return id;
    }
}
