package proj.service;

import proj.dao.TestDao;
import org.skife.jdbi.v2.sqlobject.Transaction;
import restx.factory.SqlAlternative;
import restx.factory.When;

@When(name = "sql.alternate", value = "true")
@SqlAlternative(to = AlternateService.class)
public class AlternateServiceImpl implements AlternateService {

    private TestDao testDao;

    public AlternateServiceImpl(TestDao testDao) {
        this.testDao = testDao;
    }

    @Override
    @Transaction
    public void insertTwoLines() {
        testDao.insert("toto");
        testDao.insert("toolong");
    }
}
