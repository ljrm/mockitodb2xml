package ind.lisk;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RowExtractor {
    static TableSql[] extract(String tables){
        TableSql[] tableSqls = {};
        if(tables == null || (tables = tables.trim()).length() == 0){
            return tableSqls;
        }
        String[] limitedTables = tables.split(";");
        tableSqls = new TableSql[limitedTables.length];
        for(int i=0; i<limitedTables.length; i++){
            tableSqls[i] = parseTableSql(limitedTables[i]);
        }
        return tableSqls;
    }

    static  TableSql parseTableSql(String str){
        String ids = null;
        String tableName = str;
        if(str.indexOf(":")>0){
            tableName = str.substring(0, str.indexOf(":"));
            ids = str.substring(str.indexOf(":")+1).trim();
        }
        tableName = tableName.toUpperCase();
        return new TableSql(formSql(ids, tableName), tableName);
    }

    static String formSql(String ids, String tableName){
        StringBuilder sql = new StringBuilder("select * from ").append(tableName);
        if(ids != null && ids.length()>0){
            sql.append(" where id in(");
            for(String id:ids.split(",")){
                sql.append(id).append(",");
            }
            sql.delete(sql.length()-1,sql.length());
            sql.append(")");
        }
        return sql.toString();
    }

    static List<List<NamedColumn>> extractRows(Connection connection, TableSql tableSql) {
        List<List<NamedColumn>> rows = new ArrayList<>();
        try(PreparedStatement preparedStatement = connection.prepareStatement(tableSql.sql);
            ResultSet rs = preparedStatement.executeQuery())
        {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            SqlColumn[] columns = new SqlColumn[columnCount];
            for (int i = 0; i < columnCount; i++) {
                int index = i+1;
                columns[i] = new SqlColumn(index, metaData.getColumnName(index), metaData.getColumnType(index));
            }
            while (rs.next()) {
                List<NamedColumn> row = RowExtractor.getRow(rs, columns);
                rows.add(row);
            }
        }catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e.getCause());
        }
        return rows;
    }

    static List<NamedColumn> getRow(ResultSet rs, SqlColumn[] sqlColumns){
        List<NamedColumn> row = new ArrayList<>(sqlColumns.length);
        for(int i=0;i<sqlColumns.length;i++){
            row.add(new NamedColumn(getColumn(rs, sqlColumns[i]), sqlColumns[i].columnName));
        }
        return row;
    }

    static Object getColumn(ResultSet rs, SqlColumn sqlColumn){
        Object value = null;
        try {
            switch (sqlColumn.dataType) {
                case Types.BIGINT:
                case Types.SMALLINT:
                case Types.TINYINT:
                case Types.INTEGER:
                    value = rs.getInt(sqlColumn.index); break;
                case Types.DATE:
                case Types.TIMESTAMP:
                    value = rs.getTimestamp(sqlColumn.index); break;
                case Types.TIME:
                    value = rs.getTime(sqlColumn.index); break;
                case Types.DOUBLE:
                    value = rs.getDouble(sqlColumn.index); break;
                case Types.FLOAT:
                    value = rs.getFloat(sqlColumn.index); break;
                case Types.CHAR:
                case Types.VARCHAR:
                    value = rs.getString(sqlColumn.index);break;
                case Types.BLOB:
                    value = rs.getBlob(sqlColumn.index); break;
                case Types.CLOB:
                    value = rs.getClob(sqlColumn.index); break;
                case Types.DECIMAL:
                case Types.NUMERIC:
                     value = rs.getBigDecimal(sqlColumn.index); break;
                case Types.OTHER:
                    value = rs.getObject(sqlColumn.index); break;
                case Types.NULL:
                default:
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return value;
    }



}
