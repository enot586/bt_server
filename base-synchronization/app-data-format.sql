CREATE TABLE routs (_id_route INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,date_create TEXT,actuality INTEGER,id_path_picture_route TEXT,id_position_user_in_route INTEGER);
CREATE TABLE detour (_id_detour INTEGER PRIMARY KEY AUTOINCREMENT,id_user INTEGER,id_route INTEGER,id_shedule INTEGER,time_start TEXT,time_stop TEXT,finished INTEGER,send INTEGER);
CREATE TABLE visits (_id_visit INTEGER PRIMARY KEY AUTOINCREMENT,id_point INTEGER,id_detour INTEGER,time TEXT);
CREATE TABLE shedule (_id_shedule INTEGER PRIMARY KEY AUTOINCREMENT,id_position_shedule INTEGER,day_of_week INTEGER,week_of_year INTEGER,id_route_shedule INTEGER,id_shift_shedule INTEGER,number_routs_shedule INTEGER,actual_shedule INTEGER);
CREATE TABLE positions (_id_position INTEGER PRIMARY KEY AUTOINCREMENT,position TEXT,position_in_archive INTEGER);
CREATE TABLE users (_id_user INTEGER PRIMARY KEY AUTOINCREMENT,fio TEXT,id_position INTEGER,is_admin INTEGER,user_in_system INTEGER,user_in_archive INTEGER);
CREATE TABLE points_in_routs (_id_points_in_route INTEGER PRIMARY KEY AUTOINCREMENT,id_point_in_points_in_route INTEGER,id_route_in_points_in_route INTEGER,coor_x_in_points_in_route REAL,coor_y_in_points_in_route REAL,actuality_in_points_in_route INTEGER);
CREATE TABLE points (_id_point INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,description TEXT,number_nfc TEXT,archive INTEGER,archive_reason TEXT);
CREATE TABLE pictures (_id_picture INTEGER PRIMARY KEY AUTOINCREMENT,path_picture TEXT,raw_path_picture TEXT,send_picture INTEGER);
