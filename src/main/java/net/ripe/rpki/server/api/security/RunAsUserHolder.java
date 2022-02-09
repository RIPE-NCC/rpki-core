package net.ripe.rpki.server.api.security;


public final class RunAsUserHolder {

    private RunAsUserHolder() {
        //Utility classes should not have a public or default constructor.
    }

    /** Thread local holder of the RunAsUser object. */
    private static final ThreadLocal<RunAsUser> CURRENT = new ThreadLocal<>();

    public static RunAsUser get() {
        return CURRENT.get();
    }

    public static void set(RunAsUser user) {
        CURRENT.set(user);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public interface Do {
        void exec();
    }

    public interface Get<R> {
        R exec();
    }

    public interface GetE<R, E extends Exception> {
        R exec() throws E;
    }

    private static void runAs(RunAsUser user, Do d) {
        try {
            set(user);
            d.exec();
        } finally {
            clear();
        }
    }

    private static <T> T runAs(RunAsUser user, Get<T> t) {
        try {
            set(user);
            return t.exec();
        } finally {
            clear();
        }
    }

    private static <T, E extends Exception> T runAs(RunAsUser user, GetE<T, E> t) throws E {
        try {
            set(user);
            return t.exec();
        } finally {
            clear();
        }
    }

    public static void asAdmin(Do d) {
        runAs(RunAsUser.ADMIN, d);
    }

    public static <T> T asAdmin(Get<T> t) {
        return runAs(RunAsUser.ADMIN, t);
    }

    public static <T, E extends Exception> T asAdmin(GetE<T, E> t) throws E {
        return runAs(RunAsUser.ADMIN, t);
    }


}
