package common;

import burp.scanWatcher.CooldownWatcher;

import java.util.concurrent.Executors;

import static org.junit.Assert.assertTrue;

public class TestPlatform implements CooldownWatcher.Platform
{
    private int calls = 0;
    private final long[] currentTimeMs;

    public TestPlatform(long... currentTimeMs)
    {
        assertTrue(currentTimeMs.length > 0);

        this.currentTimeMs = currentTimeMs;
    }

    @Override
    public long currentTimeMs()
    {
        assertTrue(currentTimeMs.length > 0);

        final long ms = currentTimeMs[calls];

        if (calls < (currentTimeMs.length - 1))
        {
            calls++;
        }

        return ms;
    }

    @Override
    public void startOnNewThread(String name, Runnable runnable)
    {
        final Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setName(name);
        thread.start();
    }

    @Override
    public void sleep(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
}
