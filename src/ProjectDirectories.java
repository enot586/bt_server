package reportserver;

public class ProjectDirectories {

    public static final String directoryDownloads           = "downloads";
    public static final String directoryDatabase            = "base-synchronization";
    public static final String commonDatabaseFileName       = "app-data.db3";
    public static final String localDatabaseFileName        = "local-data.db3";
    public static final String commonDatabaseRelativePath   = directoryDatabase+"/"+commonDatabaseFileName;
    public static final String localDatabaseRelativePath    = directoryDatabase+"/"+localDatabaseFileName;

    public static final String test_commonDatabaseRelativePath   = "src/tests/common-test.db3";
    public static final String test_localDatabaseRelativePath    = "src/tests/local-test.db3";

}
