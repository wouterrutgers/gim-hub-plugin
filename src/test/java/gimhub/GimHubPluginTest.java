package gimhub;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GimHubPluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(GimHubPlugin.class);
        RuneLite.main(args);
    }
}
