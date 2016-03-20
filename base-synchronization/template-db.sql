
CREATE TABLE "routs" (
                        "_id_route" INTEGER PRIMARY KEY AUTOINCREMENT,
                        "name" TEXT,
                        "date_create" TEXT,
                        "actuality" INTEGER,
                        "path_picture_route" TEXT,
                        "id_position_user_in_route" INTEGER
                     );

CREATE TABLE "detour" (
                        "_id_detour" INTEGER PRIMARY KEY AUTOINCREMENT,
                        "id_user" INTEGER,
                        "id_route" INTEGER,
                        "id_shedule" INTEGER,
                        "time_start" TEXT,
                        "time_stop" TEXT,
                        "finished" INTEGER,
                        "send" INTEGER
                     );

CREATE TABLE "visits" (
                        "_id_visit" INTEGER PRIMARY KEY AUTOINCREMENT,
                        "id_point" INTEGER,
                        "id_detour" INTEGER,
                        "time" TEXT
                      );

CREATE TABLE "shedule" (
                        "_id_shedule" INTEGER PRIMARY KEY AUTOINCREMENT,
                        "day_of_week" TEXT,
                        "description" TEXT,
                        "date" TEXT
                       );

CREATE TABLE "positions" (
                            "_id_position" INTEGER PRIMARY KEY AUTOINCREMENT,
                            "position" TEXT,
                            "position_in_archive" INTEGER,
                         );

CREATE TABLE "users" (
                        "_id_user" PRIMARY KEY AUTOINCREMENT,
                        "fio" TEXT,
                        "id_position" INTEGER,
                        "is_admin" INTEGER,
                        "user_in_system" INTEGER,
                        "user_in_archive" INTEGER
                     );

CREATE TABLE "points_in_routs" (
                                "_id_points_in_route" INTEGER PRIMARY KEY AUTOINCREMENT,
                                "id_point_in_points_in_route" INTEGER,
                                "id_route_in_points_in_route" INTEGER,
                                "coor_x_in_points_in_route" REAL,
                                "coor_y_in_points_in_route" REAL
                               );

CREATE TABLE "points" (
                        "_id_point" PRIMARY KEY AUTOINCREMENT,
                        "name" TEXT,
                        "description" TEXT,
                        "number_nfc" TEXT,
                        "archive" INTEGER,
                        "archive_reason" TEXT
                       );

CREATE TABLE "clients_version" (
                                "_id_client" INTEGER PRIMARY KEY AUTOINCREMENT,
                                "mac" TEXT,
                                "id_version" INTEGER,
                               );

CREATE TABLE "history" (
                       "_id_query" INTEGER PRIMARY KEY AUTOINCREMENT,
                       "id_version" INTEGER,
                       "query" TEXT
                       );

COMMIT;
