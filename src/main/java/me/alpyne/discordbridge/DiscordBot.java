package me.alpyne.discordbridge;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

public class DiscordBot extends ListenerAdapter {

    private JavaPlugin plugin;
    private Logger logger;
    private String commandPrefix;
    private String chatFormat;

    private HashSet<Long> channels = new HashSet<>();

    public DiscordBot(JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        this.commandPrefix = plugin.getConfig().getString("command-prefix");
        this.chatFormat = plugin.getConfig().getString("chat-format");

        loadChannelConfig();
    }

    /**
     * Loads all channel ID's from "discord-channels" in config.
     */
    private void loadChannelConfig()
    {
        List<Long> listenerChannels = plugin.getConfig().getLongList("discord-channels");
        for (long id : listenerChannels) {
            if (!channels.add(id)) {
                logger.warning("Duplicate Discord channel ID: '" + id + "'");
            }
        }

        if (channels.size() > 0)
            logger.info("Loaded " + channels.size() + " Discord channels from config.");
    }

    private void saveChannelConfig()
    {
        if (channels.size() > 0)
            plugin.getConfig().set("discord-channels", Arrays.asList(channels));
        else
            plugin.getConfig().set("discord.channels", null);
        plugin.saveConfig();
    }

    private void addChannel(MessageChannel channel)
    {
        if (!channels.add(channel.getIdLong())) {
            channel.sendMessage("This channel is already registered as a listening channel.").queue();
            return;
        }

        channel.sendMessage("Successfully registered <#" + channel.getId() + "> as a listening channel.").queue();
        logger.info("Registered new listening channel: #" + channel.getName() + " (" + channel.getId()  + ")");

        saveChannelConfig();
    }

    private void removeChannel(MessageChannel channel)
    {
        if (!channels.remove(channel.getIdLong())) {
            channel.sendMessage("This channel is not registered as a listening channel.").queue();
            return;
        }

        channel.sendMessage("Successfully unregistered <#" + channel.getId()  + ">").queue();
        logger.info("Unregistered listening channel: #" + channel.getName() + " (" + channel.getId()  + ")");

        saveChannelConfig();
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


        if (channels.contains(e.getChannel().getIdLong())) {

            String chatMsg = chatFormat
                    .replaceAll("%username%", e.getMember().getEffectiveName())
                    .replaceAll("%message%", e.getMessage().getContentDisplay());

            chatMsg = ChatColor.translateAlternateColorCodes('&', chatMsg);
            plugin.getServer().broadcastMessage(chatMsg);
        }

        /*
         * Command handling.
         */
        if (content.startsWith(commandPrefix)) {

            String command = content.substring(commandPrefix.length()).trim().toLowerCase();
            MessageChannel channel = e.getChannel();

            //logger.info("Got command: " + command);

            switch(command) {
                case "help":
                    e.getChannel().sendMessage("**DiscordBridge Help:**\n"+
                            "`"+commandPrefix+"help`: Show this help message.\n"+
                            "`"+commandPrefix+"register`: Register a new channel to connect with Minecraft chat.\n"+
                            "`"+commandPrefix+"unregister`: Unregister this channel, stop listening to Minecraft chat."
                    ).queue();
                    break;

                case "register":
                    addChannel(channel);
                    break;

                case "unregister":
                    removeChannel(channel);
                    break;

                default:
                    break;
            }
        }

    }
}
