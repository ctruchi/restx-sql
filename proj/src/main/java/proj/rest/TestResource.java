package proj.rest;

import proj.domain.TestObj;
import restx.annotations.GET;
import restx.annotations.POST;
import restx.annotations.RestxResource;
import restx.factory.Component;
import restx.security.PermitAll;
import proj.service.AlternateService;
import proj.service.TestService;

@RestxResource("/test")
@Component
public class TestResource {

    private TestService testService;
    private AlternateService alternateService;

    public TestResource(TestService testService, AlternateService alternateService) {
        this.testService = testService;
        this.alternateService = alternateService;
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
}
