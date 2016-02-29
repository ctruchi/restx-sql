package proj.persistence;

import org.postgresql.ds.PGSimpleDataSource;
import org.skife.jdbi.v2.DBI;
import restx.factory.Module;
import restx.factory.Provides;

import javax.sql.DataSource;

@Module
public class DbiModule {

    @Provides
    public DBI dbi(DataSource dataSource) {
        return  new DBI(dataSource);
    }

    @Provides
    public DataSource dataSource() throws ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl("jdbc:postgresql://localhost/foo");
        dataSource.setUser("foo");
        dataSource.setPassword("foo");
        return dataSource;
    }
}
