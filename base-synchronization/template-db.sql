
CREATE TABLE routs (_id_route INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, date_create TEXT, actuality INTEGER, path_picture_route TEXT);

CREATE TABLE detour (_id_detour INTEGER PRIMARY KEY AUTOINCREMENT, id_user INTEGER, id_route INTEGER, id_shedule INTEGER, time_start TEXT, time_stop TEXT, finished INTEGER, sent INTEGER);

CREATE TABLE visits (_id_visit INTEGER PRIMARY KEY AUTOINCREMENT, id_point INTEGER, id_detour INTEGER, time TEXT);
INSERT INTO "visits" VALUES(1,2,14,'2015-07-06 11:28:35');
INSERT INTO "visits" VALUES(2,1,16,'2015-07-06 13:28:03');
CREATE TABLE shedule (_id_shedule INTEGER PRIMARY KEY AUTOINCREMENT, day_of_week TEXT, description TEXT, date TEXT);
CREATE TABLE "positions" (
    "_id_position" INTEGER PRIMARY KEY AUTOINCREMENT,
    "position" TEXT
);
CREATE TABLE users (
    "_id_user" INTEGER,
    "fio" TEXT,
    "is_admin" INTEGER,
    "user_in_system" INTEGER
, "id_user" INTEGER);

CREATE TABLE "points_in_routs" (
    "_id" INTEGER PRIMARY KEY AUTOINCREMENT,
    "id_route" INTEGER,
    "id_point" INTEGER,
    "coor_x" REAL,
    "coor_y" REAL
);
CREATE TABLE points (
    "_id_point" INTEGER,
    "name" TEXT,
    "description" TEXT,
    "number_nfc" TEXT,
    "archive" INTEGER,
    "archive_reason" TEXT
);

COMMIT;
