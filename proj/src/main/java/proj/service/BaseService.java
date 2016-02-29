package proj.service;

import proj.domain.TestObj;

public class BaseService {

    public TestObj find() {
        TestObj testObj = new TestObj();
        testObj.setLabel("label");
        return testObj;
    }
}
