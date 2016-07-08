package reportserver;


import org.apache.log4j.Logger;

import java.io.*;
import java.sql.SQLSyntaxErrorException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Scanner;

class SqlCommandList implements Iterable<String> {
    private LinkedList<String> listSqlQueries = new LinkedList<String>();
    private static Logger log = Logger.getLogger(SqlCommandList.class);

    SqlCommandList() {

    }

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
        return (symbol == '\n') || (symbol == '\r');
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

    public void addQueryToList(String query) {
        listSqlQueries.add(query);
    }

    private boolean checkSqlSyntax(String query) {
        return true;
    }

    private void addFromFile(File script) throws FileNotFoundException, SQLSyntaxErrorException {
        InputStream inputstream = new FileInputStream(script);
        Scanner stringScanner = new Scanner(new BufferedInputStream(inputstream), "UTF-8").useDelimiter(";");

        while ( stringScanner.hasNext() ) {
            String rightFormat = deletePrefixSuffix( stringScanner.next() );
            if (checkSqlSyntax(rightFormat)) {
                listSqlQueries.add(rightFormat);
            } else {
                listSqlQueries.clear();
                SQLSyntaxErrorException e = new SQLSyntaxErrorException();
                log.error(e);
                throw e;
            }
        }
    }

    @Override
    public Iterator<String> iterator() {
        return listSqlQueries.listIterator();
    }

}
