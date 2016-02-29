package proj.mapper;

import proj.domain.TestObj;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TestObjMapper implements ResultSetMapper<TestObj> {

    @Override
    public TestObj map(int index, ResultSet rs, StatementContext ctx) throws SQLException {
        TestObj testObj = new TestObj();
        testObj.setLabel(rs.getString("label"));
        return testObj;
    }
}
