package net.ripe.rpki.ripencc.ui.daemon.health;

import com.google.gson.Gson;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Health {

    private enum Code {
        OK, WARNING, ERROR
    }

    public static class Status {
        public final Code status;

        public final String message;

        Status(Code status, String message) {
            this.status = status;
            this.message = message;
        }

        public boolean isHealthy() {
            return status == Code.OK;
        }
    }

    public static abstract class Check {
        public final String name;

        protected Check(String name) {
            this.name = name;
        }

        public abstract Status check();
    }

    public static Status ok(final String message) {
        return new Status(Code.OK, message);
    }

    public static Status ok() {
        return ok(null);
    }

    public static Status warning(final String message) {
        return new Status(Code.WARNING, message);
    }

    public static Status error(final String message) {
        return new Status(Code.ERROR, message);
    }

    static int httpCode(Map<String, Status> statuses) {
        final Collection<Status> values = statuses.values();
        if (values.stream().anyMatch(c -> c.status == Code.ERROR)) {
            return 500;
        }
        if (values.stream().anyMatch(c -> c.status == Code.WARNING)) {
            return 299;
        }
        return 200;
    }

    // Parallel execution here is mostly for the cases when some of the checks are
    // seriously slower than other or time out.
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    static Map<String, Status> statuses(Collection<Check> checks) {
        final Map<String, Future<Status>> futures = new HashMap<>();
        for (final Check c : checks) {
            futures.put(c.name, executorService.submit(c::check));
        }
        final Map<String, Status> statuses = new HashMap<>();
        for (Map.Entry<String, Future<Status>> e : futures.entrySet()) {
            Status value;
            try {
                value = e.getValue().get();
            } catch (Exception exc) {
                value = error(exc.getMessage());
            }
            statuses.put(e.getKey(), value);
        }
        return statuses;
    }

    static String toJson(Map<String, Status> statuses) {
        return new Gson().toJson(statuses);
    }

}
