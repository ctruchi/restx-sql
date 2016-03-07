package proj.dao;

import proj.domain.TestObj;

import java.util.List;

public interface TestAbstractDao {

    List<TestObj> findObj();
}
