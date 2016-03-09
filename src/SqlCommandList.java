package reportserver;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.sql.SQLSyntaxErrorException;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class SqlCommandList {
    private LinkedList<String> listSqlQueryes = new LinkedList<String>();

    SqlCommandList(File file) throws FileNotFoundException, SQLSyntaxErrorException {
        this.addFromFile(file);
    }

    public int getSqlQueriesNumber() {
        return listSqlQueryes.size();
    }

    public String popQuery() throws NoSuchElementException {
        String result = listSqlQueryes.element();
        listSqlQueryes.remove();
        return result;
    }

    private boolean isBadStartOrFinishSymbol(char symbol) {
        if (symbol == '\n' || symbol == '\r') return true;
        return false;
    }

    private String deletePrefixSuffix(String query) {
        StringBuilder newQuery = new StringBuilder( query.trim() );

        for (int i = 0;
             (i < newQuery.length()) &&
             (isBadStartOrFinishSymbol(newQuery.charAt(i))); newQuery.delete(i,i), ++i);

        for (int i = (newQuery.length()-1);
             (i > 0) &&
             (isBadStartOrFinishSymbol(newQuery.charAt(i))); newQuery.delete(i,i), --i);

        return newQuery.toString();
    }

    private boolean checkSqlSyntax(String query) {
        return true;
    }

    private void addFromFile(File script) throws FileNotFoundException, SQLSyntaxErrorException {
        InputStream inputstream = new FileInputStream(script);

        Scanner stringScanner = new Scanner(inputstream, "UTF-8").useDelimiter(";");

        while ( stringScanner.hasNext() ) {
            String rightFormat = deletePrefixSuffix( stringScanner.next() );
            if (checkSqlSyntax(rightFormat)) {
                listSqlQueryes.add(rightFormat);
            } else {
                listSqlQueryes.clear();
                throw (new SQLSyntaxErrorException());
            }
        }
    }


}
