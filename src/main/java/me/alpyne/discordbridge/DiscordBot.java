package me.alpyne.discordbridge;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.logging.Logger;

public class DiscordBot extends ListenerAdapter {

    private JavaPlugin plugin;
    private Logger logger;
    private String commandPrefix;
    private HashSet<String> channels = new HashSet<>();

    public DiscordBot(JavaPlugin plugin) {
    public DiscordBot(JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        this.commandPrefix = plugin.getConfig().getString("command-prefix");
    }

    @Override
    public void onReady(@Nonnull ReadyEvent e)
    {
        logger.info("Connected to Discord and ready.");
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent e)
    {

        String content = e.getMessage().getContentRaw();

        if (content.startsWith(commandPrefix)) {

            String command = content.substring(commandPrefix.length()).trim().toLowerCase();
            String channelId = e.getChannel().getId();

            logger.info("Got command: " + command);

            switch(command) {
                case "help":
                    e.getChannel().sendMessage("**DiscordBridge Help:**\n"+
                            "`"+commandPrefix+"help`: Show this help message.\n"+
                            "`"+commandPrefix+"register`: Register a new channel to connect with Minecraft chat.\n"+
                            "`"+commandPrefix+"unregister`: Unregister this channel, stop listening to Minecraft chat."
                    ).queue();
                    break;

                case "register":

                    if (!channels.add(channelId)) {
                        e.getChannel().sendMessage("This channel is already registered as a listening channel.").queue();
                        break;
                    }

                    e.getChannel().sendMessage("Successfully registered <#" + channelId + "> as a listening channel.").queue();
                    logger.info("Registered new listening channel: #" + e.getChannel().getName() + " (" + channelId + ")");
                    break;

                case "unregister":

                    if (!channels.remove(channelId)) {
                        e.getChannel().sendMessage("This channel is not registered as a listening channel.").queue();
                        break;
                    }

                    e.getChannel().sendMessage("Successfully unregistered <#" + channelId + ">").queue();
                    logger.info("Unregistered listening channel: #" + e.getChannel().getName() + " (" + channelId + ")");
                    break;

                default:
                    break;
            }
        }

    }
}
