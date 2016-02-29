package restx;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.sqlobject.PublicOnDemandHandleDing;
import restx.factory.Module;
import restx.factory.Provides;

import javax.inject.Named;

@Module
public class SqlModule {

    @Named("SqlHandleDing")
    @Provides
    public PublicOnDemandHandleDing onDemandHandleDing(DBI dbi) {
        return new PublicOnDemandHandleDing(dbi);
    }
}
