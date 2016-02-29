package proj.service;

import proj.dao.TestDao;
import org.skife.jdbi.v2.sqlobject.Transaction;
import restx.factory.SqlComponent;

@SqlComponent
public class TestService extends BaseService {

    private TestDao testDao;

    public TestService(TestDao testDao) {
        this.testDao = testDao;
    }

    @Transaction
    public void insertTwoLines() {
        testDao.insert("toto");
        testDao.insert("toolong");
    }
}
