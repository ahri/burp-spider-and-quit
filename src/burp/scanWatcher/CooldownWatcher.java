package burp.scanWatcher;

public class CooldownWatcher
{
    private static long NO_REQUEST_SENT = -1L;
    private final Platform platform;
    private final CooldownCalculator cooldownCalculator;
    private final Events events;
    private final int pollingIntervalMs;

    private final Object lock = new Object();
    private Long lastRequestTime = NO_REQUEST_SENT;
    private boolean started = false;

    public CooldownWatcher(final Platform platform, final CooldownCalculator cooldownCalculator, final Events events, final int pollingIntervalMs)
    {
        this.platform = platform;
        this.cooldownCalculator = cooldownCalculator;
        this.events = events;
        this.pollingIntervalMs = pollingIntervalMs;
    }

    public static class InvalidStateException extends RuntimeException
    {
        public InvalidStateException(String message)
        {
            super(message);
        }
    }

    public CooldownWatcher start()
    {
        if (started)
        {
            throw new InvalidStateException("Thread already started");
        }

        started = true;

        platform.startOnNewThread("cooldownWatcher", new Runnable()
        {
            @Override
            public void run()
            {
                while (true)
                {
                    synchronized (lock)
                    {
                        if (lastRequestTime != NO_REQUEST_SENT)
                        {
                            final long cooldownMs = cooldownCalculator.milliseconds();
                            if (lastRequestTime + cooldownMs < platform.currentTimeMs())
                            {
                                events.onEnd(cooldownMs);
                                lastRequestTime = NO_REQUEST_SENT;
                            }
                        }
                    }

                    platform.sleep(pollingIntervalMs);
                }
            }
        });

        return this;
    }

    public void onActivity()
    {
        synchronized (lock)
        {
            if (lastRequestTime == NO_REQUEST_SENT)
            {
                events.onStart();
            }

            lastRequestTime = platform.currentTimeMs();
        }
    }

    public interface Platform
    {
        long currentTimeMs();

        void startOnNewThread(String name, Runnable runnable);

        void sleep(int ms);
    }

    public interface CooldownCalculator
    {
        long milliseconds();
    }

    public interface Events
    {
        void onStart();

        void onEnd(long cooldownMs);
    }
}
