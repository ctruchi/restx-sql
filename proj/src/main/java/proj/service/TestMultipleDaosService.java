package proj.service;

import proj.dao.TestAbstractDao;
import proj.domain.TestObj;
import restx.factory.SqlComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SqlComponent
public class TestMultipleDaosService {

    private Set<TestAbstractDao> daos;

    public TestMultipleDaosService(Set<TestAbstractDao> daos) {
        this.daos = daos;
    }

    public Iterable<TestObj> findAll() {
        List<TestObj> objs = new ArrayList<>();
        for (TestAbstractDao dao : daos) {
            objs.addAll(dao.findObj());
        }

        return objs;
    }
}
