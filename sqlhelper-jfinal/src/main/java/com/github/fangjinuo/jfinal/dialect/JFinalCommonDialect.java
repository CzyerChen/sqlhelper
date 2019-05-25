package com.github.fangjinuo.jfinal.dialect;

import com.github.fangjinuo.sqlhelper.dialect.RowSelection;
import com.github.fangjinuo.sqlhelper.dialect.SQLStatementInstrumentor;
import com.github.fangjinuo.sqlhelper.dialect.internal.OracleDialect;
import com.github.fangjinuo.sqlhelper.dialect.parameter.ArrayBasedParameterSetter;
import com.github.fangjinuo.sqlhelper.dialect.parameter.ArrayBasedQueryParameters;
import com.jfinal.plugin.activerecord.Record;
import com.jfinal.plugin.activerecord.Table;
import com.jfinal.plugin.activerecord.dialect.Dialect;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JFinalCommonDialect extends Dialect {
    private com.github.fangjinuo.sqlhelper.dialect.Dialect delegate;
    private String databaseId;
    private SQLStatementInstrumentor instrumentor;

    public JFinalCommonDialect(String databaseId) {
        this.databaseId = databaseId.toLowerCase();
        this.instrumentor = new SQLStatementInstrumentor();
        if (instrumentor.beginIfSupportsLimit(databaseId)) {
            delegate = instrumentor.getCurrentDialect();
        }
    }

    private String getQuotedIdentifier(String identifier) {
        return delegate == null ? identifier : delegate.getQuotedIdentifier(identifier);
    }

    private ThreadLocal<RowSelection> pagingRequestHolder = new ThreadLocal<RowSelection>();

    @Override
    public String forTableBuilderDoBuild(String tableName) {
        return "select * from " + getQuotedIdentifier(tableName) + " where 1 = 2";
    }

    @Override
    public String forPaginate(int pageNumber, int pageSize, StringBuilder findSql) {
        RowSelection rowSelection = new RowSelection();
        rowSelection.setOffset((pageNumber - 1) * pageSize);
        rowSelection.setLimit(pageSize);
        if (instrumentor.beginIfSupportsLimit(databaseId)) {
            pagingRequestHolder.set(rowSelection);
            return instrumentor.instrumentSql(findSql.toString(), rowSelection);
        } else {
            return findSql.toString();
        }
    }

    @Override
    public void fillStatement(PreparedStatement pst, List<Object> paras) throws SQLException {
        fillStatement(pst, paras.toArray());
    }

    @Override
    public void fillStatement(PreparedStatement pst, Object... paras) throws SQLException {
        RowSelection rowSelection = pagingRequestHolder.get();
        if (rowSelection != null) {
            pagingRequestHolder.remove();
            ArrayBasedQueryParameters queryParameters = new ArrayBasedQueryParameters();
            queryParameters.setRowSelection(rowSelection);
            instrumentor.bindParameters(pst, new ArrayBasedParameterSetter(), queryParameters, true);
        } else {
            super.fillStatement(pst, paras);
        }
    }


    @Override
    public boolean isOracle() {
        return delegate == null ? databaseId.equals("oracle") : delegate instanceof OracleDialect;
    }

    @Override
    public String forFindAll(String tableName) {
        return super.forFindAll(getQuotedIdentifier(tableName));
    }

    @Override
    public String forModelFindById(Table table, String columns) {
        StringBuilder sql = new StringBuilder("select ").append(columns).append(" from ");
        sql.append(getQuotedIdentifier(table.getName()));
        sql.append(" where ");
        String[] pKeys = table.getPrimaryKey();
        for (int i = 0; i < pKeys.length; i++) {
            if (i > 0) {
                sql.append(" and ");
            }
            sql.append(getQuotedIdentifier(pKeys[i])).append(" = ?");
        }
        return sql.toString();
    }

    @Override
    public String forModelDeleteById(Table table) {
        String[] pKeys = table.getPrimaryKey();
        StringBuilder sql = new StringBuilder(45);
        sql.append("delete from ");
        sql.append(getQuotedIdentifier(table.getName()));
        sql.append(" where ");
        for (int i = 0; i < pKeys.length; i++) {
            if (i > 0) {
                sql.append(" and ");
            }
            sql.append(getQuotedIdentifier(pKeys[i])).append(" = ?");
        }
        return sql.toString();
    }

    @Override
    public void forModelSave(Table table, Map<String, Object> attrs, StringBuilder sql, List<Object> paras) {
        sql.append("insert into ").append(getQuotedIdentifier(table.getName())).append('(');
        StringBuilder temp = new StringBuilder(") values(");
        for (Map.Entry<String, Object> e : attrs.entrySet()) {
            String colName = e.getKey();
            if (table.hasColumnLabel(colName)) {
                if (paras.size() > 0) {
                    sql.append(", ");
                    temp.append(", ");
                }
                sql.append(getQuotedIdentifier(colName));
                temp.append('?');
                paras.add(e.getValue());
            }
        }
        sql.append(temp.toString()).append(')');
    }

    @Override
    public void forModelUpdate(Table table, Map<String, Object> attrs, Set<String> modifyFlag, StringBuilder sql, List<Object> paras) {
        sql.append("update ").append(getQuotedIdentifier(table.getName())).append(" set ");
        String[] pKeys = table.getPrimaryKey();
        for (Map.Entry<String, Object> e : attrs.entrySet()) {
            String colName = e.getKey();
            if (modifyFlag.contains(colName) && !isPrimaryKey(colName, pKeys) && table.hasColumnLabel(colName)) {
                if (paras.size() > 0) {
                    sql.append(", ");
                }
                sql.append(getQuotedIdentifier(colName)).append(" = ? ");
                paras.add(e.getValue());
            }
        }
        sql.append(" where ");
        for (int i = 0; i < pKeys.length; i++) {
            if (i > 0) {
                sql.append(" and ");
            }
            sql.append(getQuotedIdentifier(pKeys[i])).append(" = ?");
            paras.add(attrs.get(pKeys[i]));
        }
    }

    @Override
    public String forDbFindById(String tableName, String[] pKeys) {
        tableName = tableName.trim();
        trimPrimaryKeys(pKeys);

        StringBuilder sql = new StringBuilder("select * from ").append(getQuotedIdentifier(tableName)).append(" where ");
        for (int i = 0; i < pKeys.length; i++) {
            if (i > 0) {
                sql.append(" and ");
            }
            sql.append(getQuotedIdentifier(pKeys[i])).append(" = ?");
        }
        return sql.toString();
    }

    @Override
    public String forDbDeleteById(String tableName, String[] pKeys) {
        tableName = tableName.trim();
        trimPrimaryKeys(pKeys);

        StringBuilder sql = new StringBuilder("delete from ").append(getQuotedIdentifier(tableName)).append(" where ");
        for (int i = 0; i < pKeys.length; i++) {
            if (i > 0) {
                sql.append(" and ");
            }
            sql.append(getQuotedIdentifier(pKeys[i])).append(" = ?");
        }
        return sql.toString();
    }

    @Override
    public void forDbSave(String tableName, String[] pKeys, Record record, StringBuilder sql, List<Object> paras) {
        tableName = tableName.trim();
        trimPrimaryKeys(pKeys);

        sql.append("insert into ");
        sql.append(getQuotedIdentifier(tableName)).append('(');
        StringBuilder temp = new StringBuilder();
        temp.append(") values(");

        int count = 0;
        for (Map.Entry<String, Object> e : record.getColumns().entrySet()) {
            String colName = e.getKey();
            if (count++ > 0) {
                sql.append(", ");
                temp.append(", ");
            }
            sql.append(getQuotedIdentifier(colName));

            Object value = e.getValue();
            if (value instanceof String && isPrimaryKey(colName, pKeys) && ((String) value).endsWith(".nextval")) {
                temp.append(value);
            } else {
                temp.append('?');
                paras.add(value);
            }
        }
        sql.append(temp.toString()).append(')');
    }

    @Override
    public void forDbUpdate(String tableName, String[] pKeys, Object[] ids, Record record, StringBuilder sql, List<Object> paras) {
        tableName = tableName.trim();
        trimPrimaryKeys(pKeys);

        sql.append("update ").append(getQuotedIdentifier(tableName)).append(" set ");
        for (Map.Entry<String, Object> e : record.getColumns().entrySet()) {
            String colName = e.getKey();
            if (!isPrimaryKey(colName, pKeys)) {
                if (paras.size() > 0) {
                    sql.append(", ");
                }
                sql.append(getQuotedIdentifier(colName)).append(" = ? ");
                paras.add(e.getValue());
            }
        }
        sql.append(" where ");
        for (int i = 0; i < pKeys.length; i++) {
            if (i > 0) {
                sql.append(" and ");
            }
            sql.append(getQuotedIdentifier(pKeys[i])).append(" = ?");
            paras.add(ids[i]);
        }
    }
}