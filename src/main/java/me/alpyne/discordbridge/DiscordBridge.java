package me.alpyne.discordbridge;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;

public class DiscordBridge extends JavaPlugin
{
    private JDA client;

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

        getLogger().info("Connecting to Discord...");

        try {
            client = JDABuilder
                    .createDefault(token)
                    .addEventListeners(new DiscordBot(this))
                    .build();

        } catch (LoginException e) {
            getLogger().severe("Failed to connect to Discord. " + e.getMessage() + " Plugin disabled.");
            setEnabled(false);
            return;
        }


    }

    @Override
    public void onDisable()
    {
        getLogger().info("Disabling " + getName() + ". Closing any connections.");

        if (client != null)
            client.shutdownNow();
    }

}
