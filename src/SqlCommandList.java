package reportserver;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.sql.SQLSyntaxErrorException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class SqlCommandList implements Iterable<String> {
    private LinkedList<String> listSqlQueries = new LinkedList<String>();

    SqlCommandList(File file) throws FileNotFoundException, SQLSyntaxErrorException {
        this.addFromFile(file);
    }

    public int getSqlQueriesNumber() {
        return listSqlQueries.size();
    }

    public String popQuery() throws NoSuchElementException {
        String result = listSqlQueries.element();
        listSqlQueries.remove();
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
                listSqlQueries.add(rightFormat);
            } else {
                listSqlQueries.clear();
                throw (new SQLSyntaxErrorException());
            }
        }
    }

    @Override
    public Iterator<String> iterator() {
        return listSqlQueries.listIterator();
    }

}
