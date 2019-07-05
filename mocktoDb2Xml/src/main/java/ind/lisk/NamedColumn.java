package ind.lisk;

public class NamedColumn {
    Object value;
    String columnName;

    public NamedColumn(Object value, String columnName) {
        this.value = value;
        this.columnName = columnName;
    }

    @Override
    public String toString() {
        return "{" +
                "value=" + value +
                ", columnName='" + columnName + '\'' +
                '}';
    }
}
