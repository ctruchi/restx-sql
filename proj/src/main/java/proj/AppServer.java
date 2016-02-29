package proj;

import restx.server.JettyWebServer;

import java.util.Optional;

public final class AppServer {

    public static final String WEB_INF_LOCATION = "src/main/webapp/WEB-INF/web.xml";

    public static final String WEB_APP_LOCATION = "/dev/null";
    public static final String BIND_INTERFACE = "0.0.0.0"; //NOSONAR

    private AppServer() {
        //Empty constructor.
    }

    public static void main(String[] args) throws Exception {
        String mode = System.getProperty("restx.mode", "prod");
        System.setProperty("restx.mode", mode);
        System.setProperty("restx.app.package", "proj");

        int port = Integer.parseInt(Optional.ofNullable(System.getenv("PORT")).orElse("8080"));
        JettyWebServer server = new JettyWebServer(WEB_INF_LOCATION, WEB_APP_LOCATION, port, BIND_INTERFACE);

        System.setProperty("restx.server.id", server.getServerId());
        System.setProperty("restx.server.baseUrl", System.getProperty("restx.server.baseUrl", server.baseUrl()));

        server.startAndAwait();
    }

}
