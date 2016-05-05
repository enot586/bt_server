CREATE TABLE clients_version (_id_client INTEGER PRIMARY KEY AUTOINCREMENT,mac TEXT,id_version INTEGER);
CREATE TABLE history (_id_query INTEGER PRIMARY KEY AUTOINCREMENT,id_version INTEGER,query TEXT);
CREATE TABLE user_messages (_id_message INTEGER PRIMARY KEY AUTOINCREMENT,message TEXT,message_date TEXT);
CREATE TABLE pictures_history (_id_pict_history INTEGER PRIMARY KEY AUTOINCREMENT,id_version INTEGER,filename TEXT);
