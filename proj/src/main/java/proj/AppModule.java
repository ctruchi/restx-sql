package proj;

import restx.factory.Module;
import restx.factory.Provides;

import javax.inject.Named;

@Module
public class AppModule {

    @Provides
    @Named("sql.alternate")
    public String sqlAlternate() {
        return "true";
    }
}
