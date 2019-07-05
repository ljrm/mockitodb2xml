package ind.lisk;

import java.util.HashMap;
import java.util.Map;

public class TransformerConfig {

    public final static String defaultDbType = "oracle";

    private String userName;

    private String password;

    private String url;

    private DBDriver dbDriver = DBDriver.ORACLE;

    private String tables;

    private String targetFile;

    public TransformerConfig(String userName, String password, String url, String tables) {
        this(userName, password, url,  DBDriver.ORACLE, tables, null);
    }

    public TransformerConfig(String userName, String password, String url, DBDriver dbDriver, String tables, String targetFile) {
        this.userName = userName;
        this.password = password;
        this.url = url;
        this.dbDriver = dbDriver;
        this.tables = tables;
        this.targetFile = targetFile;
    }

    public TransformerConfig() {
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }

    public DBDriver getDbDriver() {
        return dbDriver;
    }

    public String getTables() {
        return tables;
    }

    public String getTargetFile() {
        return targetFile;
    }

    private static volatile Map<String, DBDriver> helper;

    public static DBDriver of(String code){
        if(helper == null){
            synchronized (TransformerConfig.class){
                helper = new HashMap<>(4);
                for(DBDriver dbDriver:DBDriver.values()){
                    helper.put(dbDriver.getIdentifier(), dbDriver);
                }
            }
        }
        return helper.get(code);
    }

    enum DBDriver {
        MYSQL("MySQL", "com.mysql.jdbc.Driver"),
        ORACLE("oracle", "oracle.jdbc.driver.OracleDriver");

        private String identifier;
        private String driverClassName;

        DBDriver(String identifier, String driverClassName) {
            this.identifier = identifier;
            this.driverClassName = driverClassName;
        }

        public String getIdentifier() {
            return identifier;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

    }

    public static class TransformerConfigBuilder{
        private String userName;

        private String password;

        private String url;

        private String dbType = TransformerConfig.defaultDbType;

        private String tables;

        private String targetFile;

        TransformerConfigBuilder(){}

        public TransformerConfigBuilder userName(String userName){
            this.userName = userName;
            return this;
        }

        public TransformerConfigBuilder password(String pqssword){
            this.password = pqssword;
            return this;
        }

        public TransformerConfigBuilder url(String url){
            this.url = url;
            return this;
        }

        public TransformerConfigBuilder tables(String tables){
            this.tables = tables;
            return this;
        }

        public TransformerConfigBuilder dbType(String dbType){
            this.dbType = dbType;
            return this;
        }

        public TransformerConfigBuilder targetFile(String targetFile){
            this.targetFile = targetFile;
            return this;
        }

        void validate(){
            if(this.dbType == null){
                this.dbType = TransformerConfig.defaultDbType;
            }
            if(targetFile!= null && !targetFile.endsWith(".xml")){

            }
        }

        public TransformerConfig build(){
            validate();
            DBDriver acDbDriver = TransformerConfig.of(this.dbType);
            return new TransformerConfig(this.userName, this.password, this.url, acDbDriver, this.tables, this.targetFile);
        }
    }
}
