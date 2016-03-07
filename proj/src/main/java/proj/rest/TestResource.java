package proj.rest;

import proj.domain.TestObj;
import proj.service.AlternateService;
import proj.service.TestMultipleDaosService;
import proj.service.TestService;
import restx.annotations.GET;
import restx.annotations.POST;
import restx.annotations.RestxResource;
import restx.factory.Component;
import restx.security.PermitAll;

@RestxResource("/test")
@Component
public class TestResource {

    private TestService testService;
    private AlternateService alternateService;
    private TestMultipleDaosService testMultipleDaosService;

    public TestResource(TestService testService, AlternateService alternateService,
                        TestMultipleDaosService testMultipleDaosService) {
        this.testService = testService;
        this.alternateService = alternateService;
        this.testMultipleDaosService = testMultipleDaosService;
    }

    @PermitAll
    @GET("")
    public TestObj find() {
        return testService.find();
    }

    @PermitAll
    @POST("")
    public void insertTwoLines() {
        testService.insertTwoLines();
    }

    @PermitAll
    @POST("/alternate")
    public void alternateInsertTwoLines() {
        alternateService.insertTwoLines();
    }

    @PermitAll
    @GET("/multiple")
    public Iterable<TestObj> findMultiple() {
        return testMultipleDaosService.findAll();
    }
}
