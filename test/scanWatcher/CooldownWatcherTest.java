package scanWatcher;

import burp.scanWatcher.CooldownWatcher;

import common.TestPlatform;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class CooldownWatcherTest
{
    private class TrackableEvents implements CooldownWatcher.Events
    {
        public boolean started = false;
        public boolean ended = false;

        @Override
        public void onStart()
        {
            started = true;
        }

        @Override
        public void onEnd(long cooldownMs)
        {
            ended = true;
        }
    }

    @Test
    public void givenAScanWatcher_whenNoRequestSent_thenNoEvents() throws Exception
    {
        final TrackableEvents events = new TrackableEvents();

        TestPlatform platform = new TestPlatform(0);
        new CooldownWatcher(
                platform,
                new CooldownWatcher.CooldownCalculator()
                {
                    @Override
                    public long milliseconds()
                    {
                        return 0;
                    }
                },
                events,
                1
        ).start();

        Thread.sleep(100);

        assertFalse(events.started);
        assertFalse(events.ended);
    }

    @Test
    public void givenAScanWatcherWithUnhittableCooldown_whenRequestSent_thenStartEventOccurs_andEndEventDoesNot() throws Exception
    {
        final TrackableEvents events = new TrackableEvents();

        TestPlatform platform = new TestPlatform(0);
        new CooldownWatcher(
                platform,
                new CooldownWatcher.CooldownCalculator()
                {
                    @Override
                    public long milliseconds()
                    {
                        return 1000;
                    }
                },
                events,
                1
        ).start().onActivity();

        while (!events.started);

        Thread.sleep(100);

        assertFalse(events.ended);
    }

    @Test
    public void givenAScanWatcherWithHittableCooldown_whenRequestSent_thenStartEventOccurs_andEndEventDoesNot() throws Exception
    {
        final int firstTime = 1000;
        final int cooldown = 500;
        final int secondTime = firstTime + cooldown + 1;

        final TrackableEvents events = new TrackableEvents();

        TestPlatform platform = new TestPlatform(firstTime, secondTime);
        new CooldownWatcher(
                platform,
                new CooldownWatcher.CooldownCalculator()
                {
                    @Override
                    public long milliseconds()
                    {
                        return cooldown;
                    }
                },
                events,
                1
        ).start().onActivity();

        while (!events.ended);
    }
}
