package reportserver;

enum BluetoothPacketType {
    /**
     * Транзакции могут быть следующих типов:
     *      1.Состоит только из JSON заголовка, если наличие данных для передачи/приема не подразумевается:
     *          {"type":int, "userId":int, "param1":value, ... "paramN":value}
     *      2.Состоит из JSON заголовка и тела данных, которые следуют сразу за заголовком размером size байт
     *          {"type":int, "userId":int, "size":int, "param1":value, ... "paramN":value}, byte0, byte1, ... byteN
     */
    SQL_QUERIES(0),        //SQL-скрипт
                                        //JSON-Заголовок: {"type":int, "userId":int, "size":int}
                                        //Тело пакета содержит последовательность строковых запросов UPDATE-INSERT-DELETE разделенных ";"
                                        //общим размером size(без учета размера заголовка).
                                        //В ответ посылается RESPONSE.

    SYNCH_REQUEST(1),        //Запрос на синхронизацию
                                        //JSON-Заголовок: {"type":int, "userId":int}
                                        //В ответ посылаются данные SQL_QUERIES содержащий историю изменений
                                        //либо BINARY_FILE, если изменений очень много

    RESPONSE(2),        //Ответ на транзакцию
                                        //JSON-Заголовок: {"type":int, "userId":int, "size":int, "status":int}
                                        //size = количество приянтых байт;
                                        //status = BluetoothTransactionStatus.getId()

    BINARY_FILE(3);        //Передача файла
                                        //JSON-Заголовок: {"type":int, "userId":int, "size":int}
                                        //Тело пакета содержит инарный кусок файла размером size
                                        //В ответ посылается RESPONSE.
    private int id;

    BluetoothPacketType(int packetTypeNumber_) {
        this.id = packetTypeNumber_;
    }

    int getId() {
        return id;
    }
}

