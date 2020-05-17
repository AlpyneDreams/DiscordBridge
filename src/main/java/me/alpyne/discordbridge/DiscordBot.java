package me.alpyne.discordbridge;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Logger;

public class DiscordBot extends ListenerAdapter {

    private DiscordBridge plugin;
    private Logger logger;
    private String commandPrefix;
    private String chatFormat;
    private HashSet<Long> channelIds = new HashSet<>();

    private HashMap<Long, MessageChannel> channels = new HashMap<>();

    public DiscordBot(DiscordBridge plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        this.commandPrefix = plugin.getConfig().getString("command-prefix");
        this.chatFormat = plugin.getConfig().getString("chat-format");
    }

    /**
     * Loads all channel ID's from "discord-channels" in config.
     */
    private void loadChannelConfig()
    {
        List<Long> listenerChannels = plugin.getConfig().getLongList("discord-channels");
        for (long id : listenerChannels) {

            if (!channelIds.add(id)) {
                logger.warning("Duplicate Discord channel ID: '" + id + "'");
                continue;
            }

            // Find the channel
            MessageChannel channel = plugin.client.getTextChannelById(id);
            if (channel == null) {
                // Try private channel instead
                channel = plugin.client.getPrivateChannelById(id);

                if (channel == null) {
                    logger.warning("Could not find Discord channel with ID: '" + id + "'");
                    continue;
                }
            }

            channels.put(id, channel);
        }

        if (channelIds.size() > 0)
            logger.info("Loaded " + channels.size() + " of " + channelIds.size() + " Discord channels from config.");
    }

    private void saveChannelConfig()
    {
        if (channelIds.size() > 0)
            plugin.getConfig().set("discord-channels", new ArrayList<Long>(channelIds));
        else
            plugin.getConfig().set("discord-channels", null);
        plugin.saveConfig();
    }

    private void addChannel(MessageChannel channel)
    {
        if (!channelIds.add(channel.getIdLong())) {
            channel.sendMessage("This channel is already registered as a listening channel.").queue();
            return;
        }

        channels.put(channel.getIdLong(), channel);

        channel.sendMessage("Successfully registered <#" + channel.getId() + "> as a listening channel.").queue();
        logger.info("Registered new listening channel: #" + channel.getName() + " (" + channel.getId()  + ")");

        saveChannelConfig();
    }

    private void removeChannel(MessageChannel channel)
    {
        if (!channelIds.remove(channel.getIdLong())) {
            channel.sendMessage("This channel is not registered as a listening channel.").queue();
            return;
        }

        channels.remove(channel.getIdLong());

        channel.sendMessage("Successfully unregistered <#" + channel.getId()  + ">").queue();
        logger.info("Unregistered listening channel: #" + channel.getName() + " (" + channel.getId()  + ")");

        saveChannelConfig();
    }

    /*==================================================
     *      Discord Event Handlers
     *==================================================*/

    @Override
    public void onReady(@Nonnull ReadyEvent e)
    {
        logger.info("Connected to Discord and ready.");

        loadChannelConfig();
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent e)
    {

        String content = e.getMessage().getContentRaw();


        if (channelIds.contains(e.getChannel().getIdLong())) {

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
