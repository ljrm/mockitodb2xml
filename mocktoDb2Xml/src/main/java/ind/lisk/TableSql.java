package ind.lisk;

import java.util.List;

public class TableSql {
    String sql;
    String tableName;
    List<List<NamedColumn>> rows;

    public TableSql(String sql, String tableName) {
        this.sql = sql;
        this.tableName = tableName;
    }

    @Override
    public String toString() {
        return "{" +
                "sql='" + sql + '\'' +
                ", tableName='" + tableName + '\'' +
                ", rows=" + rows +
                '}';
    }
}
