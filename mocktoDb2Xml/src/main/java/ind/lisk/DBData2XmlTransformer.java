package ind.lisk;

import cn.hutool.core.util.XmlUtil;
import org.apache.commons.cli.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

public class DBData2XmlTransformer {
    private TransformerConfig config;

    public DBData2XmlTransformer(){}
    public DBData2XmlTransformer(TransformerConfig config) {
        this.config = config;
    }

    public String transform(){
        // parse metadata
        TableSql[] tableSqls = parseTableSqls();
        //write to file
        return  writeToFile(tableSqls);
    }

    private TableSql[] parseTableSqls(){
        try{
            Class.forName(config.getDbDriver().getDriverClassName());
        }catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException("cannot find database driver class", e.getCause());
        }
        TableSql[] tableSqls = null;
        try(Connection connection = DriverManager.getConnection(config.getUrl(), config.getUserName(), config.getPassword())) {
            tableSqls = RowExtractor.extract(config.getTables());
            for (TableSql tableSql : tableSqls) {
                tableSql.rows = RowExtractor.extractRows(connection, tableSql);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        }
        return tableSqls;
    }

    private String writeToFile(TableSql[] tableSqls){
        String result = null;
        try{
            Document doc = XmlUtil.createXml();
            Element dbEle = doc.createElement("DATABASE");
            doc.appendChild(dbEle);
            for(TableSql tableSql : tableSqls){
                for(List<NamedColumn> row : tableSql.rows){
                    Element element = doc.createElement(tableSql.tableName);
                    for(NamedColumn column:row){
                        if(column.value != null) {
                            element.setAttribute(column.columnName, column.value.toString());
                        }
                    }
                    dbEle.appendChild(element);
                }
            }
            if(config.getTargetFile() != null){
                XmlUtil.toFile(doc, config.getTargetFile(), "UTF-8");
                result = config.getTargetFile();
            }else{
                result = XmlUtil.toStr(doc,"UTF-8",true);
            }
        }catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException("exception happens while writing data to xml", e);
        }
        return result;
    }

    public static DBData2XmlTransformer configFromArgs(String[] args){
        TransformerConfig config = parseArgLine(args);
        return new DBData2XmlTransformer(config);
    }

   public static TransformerConfig parseArgLine(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption("u","username",true,"database user's name");
        options.addOption("p","password",true,"database user's password");
        options.addOption("l","url",true,"database url");
        options.addOption("d","database",true,"database type: (1) Mysql (2) Oracle");
        options.addOption("f","targetFile",true,"the file to write result in");
        options.addOption("t","tables",true,"specified tables to extract,for example\r\n \t " +
                "busi_product:2,3,45;busi_order:100,101; meaning records in table busi_product with id 2,3,45 and records in table busi_order with id 100,101 will be extracted");
        //Parse the program arguments
        TransformerConfig.TransformerConfigBuilder configBuilder = new TransformerConfig.TransformerConfigBuilder();
        try {
            CommandLine commandLine = parser.parse(options, args);
            if(commandLine.hasOption("username")){
                configBuilder.userName(commandLine.getOptionValue("username"));
            }
            if(commandLine.hasOption("password")){
                configBuilder.password(commandLine.getOptionValue("password"));
            }
            if(commandLine.hasOption("url")){
                configBuilder.url(commandLine.getOptionValue("url"));
            }
            if(commandLine.hasOption("database")){
                configBuilder.dbType(commandLine.getOptionValue("database"));
            }
            if(commandLine.hasOption("tables")){
                configBuilder.tables(commandLine.getOptionValue("tables"));
            }
            if(commandLine.hasOption("targetFile")){
                configBuilder.targetFile(commandLine.getOptionValue("targetFile"));
            }
        } catch (ParseException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e.getCause());
        }
        return configBuilder.build();
    }

    public static DBData2XmlTransformer config(String userName, String password, String url, String dbType, String tables,String targetFile){
        TransformerConfig.TransformerConfigBuilder configBuilder = new TransformerConfig.TransformerConfigBuilder();
        TransformerConfig config =
                configBuilder.userName(userName).password(password).url(url).dbType(dbType).tables(tables).targetFile(targetFile).build();
        return new DBData2XmlTransformer(config);
    }
}
