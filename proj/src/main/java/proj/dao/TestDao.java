package proj.dao;

import proj.domain.TestObj;
import proj.mapper.TestObjMapper;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import restx.factory.SqlComponent;

@RegisterMapper(TestObjMapper.class)
@SqlComponent
public abstract class TestDao {

    @SqlQuery("SELECT * from test_obj")
    public abstract TestObj find();

    @SqlUpdate("INSERT INTO test_obj VALUES (:label)")
    public abstract void insert(@Bind("label") String label);
}
