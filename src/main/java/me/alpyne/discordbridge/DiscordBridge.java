package me.alpyne.discordbridge;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;

public class DiscordBridge extends JavaPlugin
{
    public JDA client;
    private DiscordBot bot;

    @Override
    public void onEnable()
    {
        saveDefaultConfig();

        String token = getConfig().getString("token");
        if (token.equals("YOUR_TOKEN_HERE")) {
            getLogger().warning("You must specify a Discord token in config.yml. Plugin disabled.");
            setEnabled(false);
            return;
        }

        bot = new DiscordBot(this);

        getLogger().info("Connecting to Discord...");

        try {
            client = JDABuilder
                    .createDefault(token)
                    .addEventListeners(bot)
                    .build();

        } catch (LoginException e) {
            getLogger().severe("Failed to connect to Discord. " + e.getMessage() + " Plugin disabled.");
            setEnabled(false);
            return;
        }

        getServer().getPluginManager().registerEvents(bot, this);

    }

    @Override
    public void onDisable()
    {
        getLogger().info("Disabling " + getName() + ". Closing any connections.");

        if (client != null)
            client.shutdownNow();
    }

}
