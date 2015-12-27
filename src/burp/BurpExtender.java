package burp;

import burp.scanWatcher.CooldownWatcher;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import static burp.IBurpExtenderCallbacks.TOOL_SPIDER;

public class BurpExtender implements IBurpExtender, IHttpListener
{
    private static final String BURP_SAQ_URL_NAME = "BURP_SAQ_URL";
    private CooldownWatcher cooldownWatcher;

    @Override
    public void registerExtenderCallbacks(final IBurpExtenderCallbacks callbacks) throws MalformedURLException {
        final HashMap<String, String> burpConfig = new HashMap<String, String>();
        burpConfig.put("spider.apploginmode", "3"); // avoid user interaction
        callbacks.loadConfig(burpConfig);

        callbacks.printOutput("TRACE: cooldown time after last behaviour " + guessCooldownTime(new DefaultedConfig(callbacks.saveConfig(), new DefaultedConfig.Output()
        {
            @Override
            public void println(String str)
            {
                callbacks.printOutput(str);
            }
        })) + "ms");

        cooldownWatcher = new CooldownWatcher(
                new CooldownWatcher.Platform()
                {
                    @Override
                    public long currentTimeMs()
                    {
                        return System.currentTimeMillis();
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
                },
                 new CooldownWatcher.CooldownCalculator()
                {
                    @Override
                    public long milliseconds()
                    {
                        final DefaultedConfig burpConfig = new DefaultedConfig(callbacks.saveConfig(), new DefaultedConfig.Output()
                        {
                            @Override
                            public void println(String str)
                            {
                                callbacks.printOutput(str);
                            }
                        });

                        return guessCooldownTime(burpConfig);
                    }
                },
                new CooldownWatcher.Events()
                {
                    @Override
                    public void onStart()
                    {
                    }

                    @Override
                    public void onEnd(long cooldownMs)
                    {
                        System.exit(0);
                    }
                },
                5000
        ).start();

        String url = System.getenv(BURP_SAQ_URL_NAME);

        if (url == null)
        {
            callbacks.printError("ERROR : no environment var " + BURP_SAQ_URL_NAME + " set");
            System.exit(1);
        }

        callbacks.registerHttpListener(this);

        try
        {
            callbacks.sendToSpider(new URL(url));
        }
        catch (MalformedURLException e)
        {
            callbacks.printError("ERROR: " + e.getMessage());
            System.exit(2);
        }
    }

    private static long guessCooldownTime(DefaultedConfig burpConfig)
    {
        return
                burpConfig.getLong("spider.throttleinterval", 500) +
                burpConfig.getLong("spider.pausebeforeretry", 2000) +
                burpConfig.getLong("suite.normaltimeoutmilli", 120000)
        ;
    }

    private static class DefaultedConfig
    {
        final Map<String, String> burpConfig;
        private final Output output;

        DefaultedConfig(Map<String, String> burpConfig, Output output)
        {
            this.burpConfig = burpConfig;
            this.output = output;
        }

        public long getLong(String key, long defaultValue)
        {
            String val = burpConfig.get(key);

            if (val == null)
            {
                output.println("TRACE: missing Burp config value for " + key + ". Defaulting to " + defaultValue);
                return defaultValue;
            }

            try
            {
                return Long.parseLong(val);
            }
            catch (NumberFormatException ex)
            {
                output.println("TRACE: invalid Burp config value for " + key + "; " + val + ". Defaulting to " + defaultValue);
                return defaultValue;
            }
        }

        public interface Output
        {
            void println(String str);
        }
    }

    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo)
    {
        if ((toolFlag & TOOL_SPIDER) != TOOL_SPIDER)
        {
            return; // not interested in non-spider messages
        }

        if (!messageIsRequest)
        {
            return; // not interested in responses
        }

        cooldownWatcher.onActivity();
    }
}