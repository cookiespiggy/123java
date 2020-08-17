package l.ys;

import org.junit.Test;
// https://github.com/tianmaotalk/gbaseDemo
//STEP 1. Import required packages
import java.sql.*;

public class GbaseTest {

    static final String GBASE_URL = "jdbc:gbasedbt-sqli://127.0.01:9088/zmtest:GBASEDBTSERVER=gbaseserver";
    static final String GBASE_DRIVER = "com.gbasedbt.jdbc.Driver";


    // JDBC driver name and database URL
    static final String JDBC_DRIVER = "com.gbasedbt.jdbc.IfxDriver";
    static final String DB_URL = "jdbc:gbasedbt-sqli://47.99.97.125:9088/gbasedb:gbasedbtserver=gbaseserver;db_locale=zh_cn.utf8;client_locale=zh_cn.utf8;NEWCODESET=utf-8,utf8,57372;";

    //  Database credentials
    static final String USER = "test";
    static final String PASS = "123456";


    private Connection conn;

    @Test
    public void setUp() {

    }

    @Test
    public void test() {
        Connection conn = null;
        Statement stmt = null;
        try {
            //STEP 2: Register JDBC driver
            Class.forName(JDBC_DRIVER);

            //STEP 3: Open a connection
            System.out.println("Connecting to database...");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);

            //STEP 4: Execute a query
            System.out.println("Creating statement...");
            stmt = conn.createStatement();
            String sql;
            sql = "SELECT id, first, last, age FROM Employees";
            ResultSet rs = stmt.executeQuery(sql);

            //STEP 5: Extract data from result set
            while (rs.next()) {
                //Retrieve by column name
                int id = rs.getInt("id");
                int age = rs.getInt("age");
                String first = rs.getString("first");
                String last = rs.getString("last");

                //Display values
                System.out.print("ID: " + id);
                System.out.print(", Age: " + age);
                System.out.print(", First: " + first);
                System.out.println(", Last: " + last);
            }
            //STEP 6: Clean-up environment
            rs.close();
            stmt.close();
            conn.close();
        } catch (SQLException se) {
            //Handle errors for JDBC
            se.printStackTrace();
        } catch (Exception e) {
            //Handle errors for Class.forName
            e.printStackTrace();
        } finally {
            //finally block used to close resources
            try {
                if (stmt != null)
                    stmt.close();
            } catch (SQLException se2) {
            }// nothing we can do
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException se) {
                se.printStackTrace();
            }//end finally try
        }//end try
        System.out.println("There are so thing wrong!");
    }
}
