package legalvideo;

import java.time.Clock;

public final class LegalSessionApplication {
    private LegalSessionApplication() {}

    public static void main(String[] args) throws Exception {
        ServiceConfig config = ServiceConfig.fromEnvironment();
        InfraiClient infrai = new InfraiClient(config);
        infrai.createBucket();
        MatterSessionService service = new MatterSessionService(infrai, Clock.systemUTC());
        new LegalSessionController(service).start(config.port());
        System.out.println("Legal session service listening on http://localhost:" + config.port());
    }
}
