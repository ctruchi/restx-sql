package proj.dao;

import org.skife.jdbi.v2.sqlobject.SqlQuery;
import proj.domain.TestObj;
import restx.factory.SqlComponent;

import java.util.List;

@SqlComponent
public abstract class TestConcreteDao1 implements TestAbstractDao {

    @SqlQuery("SELECT * from test_obj where label like '1%'")
    @Override
    public abstract List<TestObj> findObj();
}
