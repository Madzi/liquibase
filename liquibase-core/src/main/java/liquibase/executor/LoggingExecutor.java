package liquibase.executor;

import liquibase.database.Database;
import liquibase.database.core.MSSQLDatabase;
import liquibase.database.core.SybaseASADatabase;
import liquibase.database.core.SybaseDatabase;
import liquibase.exception.DatabaseException;
import liquibase.servicelocator.LiquibaseService;
import liquibase.sql.visitor.SqlVisitor;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.*;
import liquibase.util.StreamUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@LiquibaseService(skip = true)
public class LoggingExecutor extends AbstractExecutor implements Executor {

    private Writer output;
    private Executor delegatedReadExecutor;

    public LoggingExecutor(Executor delegatedExecutor, Writer output, Database database) {
        this.output = output;
        this.delegatedReadExecutor = delegatedExecutor;
        setDatabase(database);
    }

    @Override
    public void execute(SqlStatement sql) throws DatabaseException {
        outputStatement(sql);
    }

    @Override
    public int update(SqlStatement sql) throws DatabaseException {
        if (sql instanceof LockDatabaseChangeLogStatement) {
            return 1;
        } else if (sql instanceof UnlockDatabaseChangeLogStatement) {
            return 1;
        }

        outputStatement(sql);

        return 0;
    }

    @Override
    public void execute(SqlStatement sql, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        outputStatement(sql, sqlVisitors);
    }

    @Override
    public int update(SqlStatement sql, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        outputStatement(sql, sqlVisitors);
        return 0;
    }

    @Override
    public void comment(String message) throws DatabaseException {
        try {
            output.write(database.getLineComment());
            output.write(" ");
            output.write(message);
            output.write(StreamUtil.getLineSeparator());
        } catch (IOException e) {
            throw new DatabaseException(e);
        }
    }

    private void outputStatement(SqlStatement sql) throws DatabaseException {
        outputStatement(sql, new ArrayList<SqlVisitor>());
    }

    private void outputStatement(SqlStatement sql, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        try {
            if (SqlGeneratorFactory.getInstance().generateStatementsVolatile(sql, database)) {
                throw new DatabaseException(sql.getClass().getSimpleName()+" requires access to up to date database metadata which is not available in SQL output mode");
            }
            for (String statement : applyVisitors(sql, sqlVisitors)) {
                if (statement == null) {
                    continue;
                }
                output.write(statement);


                if (database instanceof MSSQLDatabase || database instanceof SybaseDatabase || database instanceof SybaseASADatabase) {
                    output.write(StreamUtil.getLineSeparator());
                    output.write("GO");
    //            } else if (database instanceof OracleDatabase) {
    //                output.write(StreamUtil.getLineSeparator());
    //                output.write("/");
                } else {
                    String endDelimiter = ";";
                    if (sql instanceof RawSqlStatement) {
                        endDelimiter = ((RawSqlStatement) sql).getEndDelimiter();
                    }
                    if (!statement.endsWith(endDelimiter)) {
                        output.write(endDelimiter);
                    }
                }
                output.write(StreamUtil.getLineSeparator());
                output.write(StreamUtil.getLineSeparator());
            }
        } catch (IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    public Object queryForObject(SqlStatement sql, Class requiredType) throws DatabaseException {
        if (sql instanceof SelectFromDatabaseChangeLogLockStatement) {
            return false;
        }
        return delegatedReadExecutor.queryForObject(sql, requiredType);
    }

    @Override
    public Object queryForObject(SqlStatement sql, Class requiredType, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        return delegatedReadExecutor.queryForObject(sql, requiredType, sqlVisitors);
    }

    @Override
    public long queryForLong(SqlStatement sql) throws DatabaseException {
        return delegatedReadExecutor.queryForLong(sql);
    }

    @Override
    public long queryForLong(SqlStatement sql, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        return delegatedReadExecutor.queryForLong(sql, sqlVisitors);
    }

    @Override
    public int queryForInt(SqlStatement sql) throws DatabaseException {
        try {
            return delegatedReadExecutor.queryForInt(sql);
        } catch (DatabaseException e) {
            if (sql instanceof GetNextChangeSetSequenceValueStatement) { //table probably does not exist
                return 0;
            }
            throw e;
        }
    }

    @Override
    public int queryForInt(SqlStatement sql, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        return delegatedReadExecutor.queryForInt(sql, sqlVisitors);
    }

    @Override
    public List queryForList(SqlStatement sql, Class elementType) throws DatabaseException {
        return delegatedReadExecutor.queryForList(sql, elementType);
    }

    @Override
    public List queryForList(SqlStatement sql, Class elementType, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        return delegatedReadExecutor.queryForList(sql, elementType, sqlVisitors);
    }

    @Override
    public List<Map> queryForList(SqlStatement sql) throws DatabaseException {
        return delegatedReadExecutor.queryForList(sql);
    }

    @Override
    public List<Map> queryForList(SqlStatement sql, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        return delegatedReadExecutor.queryForList(sql, sqlVisitors);
    }

    @Override
    public boolean updatesDatabase() {
        return false;
    }
}
