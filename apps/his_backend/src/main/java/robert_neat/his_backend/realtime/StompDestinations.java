package robert_neat.his_backend.realtime;

import java.util.Set;
import java.util.UUID;

/** Tematy STOMP z API.md, par. 10 (sciezki bez prefiksu `/user`, tak jak w `convertAndSendToUser`). */
public final class StompDestinations {

    public static final String USER_PREFIX = "/user";
    public static final String QUEUE_MESSAGES = "/queue/messages";
    public static final String QUEUE_THREADS = "/queue/threads";
    public static final String QUEUE_ALERTS = "/queue/alerts";
    public static final String QUEUE_TASKS = "/queue/tasks";
    public static final String TOPIC_ALERTS_PREFIX = "/topic/alerts/";

    /** Pelne tematy, na ktore klient subskrybuje (`/user/queue/...`). */
    static final Set<String> USER_QUEUES = Set.of(USER_PREFIX + QUEUE_MESSAGES, USER_PREFIX + QUEUE_THREADS,
            USER_PREFIX + QUEUE_ALERTS, USER_PREFIX + QUEUE_TASKS);

    private StompDestinations() {
    }

    public static String wardAlerts(UUID wardId) {
        return TOPIC_ALERTS_PREFIX + wardId;
    }
}
