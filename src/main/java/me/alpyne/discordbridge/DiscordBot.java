package me.alpyne.discordbridge;

import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.WebhookCluster;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.WebhookAction;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Logger;

public class DiscordBot extends ListenerAdapter implements Listener {

    private DiscordBridge plugin;
    private Logger logger;

    // Config File Values
    private String commandPrefix;
    private String chatFormat;
    private boolean useWebhooks;
    private boolean reportJoin;
    private boolean reportLeave;
    private boolean reportDeath;

    private HashSet<Long> channelIds = new HashSet<>();
    private HashMap<Long, MessageChannel> channels = new HashMap<>();
    private HashMap<Long, Webhook> webhooks = new HashMap<>();

    private WebhookCluster webhookCluster;

    public DiscordBot(DiscordBridge plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        commandPrefix = plugin.getConfig().getString("command-prefix");
        chatFormat = plugin.getConfig().getString("chat-format");
        useWebhooks = plugin.getConfig().getBoolean("use-webhooks");
        reportJoin = plugin.getConfig().getBoolean("reported-events.join");
        reportLeave = plugin.getConfig().getBoolean("reported-events.leave");
        reportDeath = plugin.getConfig().getBoolean("reported-events.death");

        webhookCluster = new WebhookCluster();
    }

    /**
     * Loads all channel ID's from "discord-channels" in config.
     */
    private void loadChannelConfig()
    {
        List<String> channelWebhookPairs = plugin.getConfig().getStringList("discord-channels");

        for (String entry : channelWebhookPairs) {

            long id = -1, webhookId = -1;

            try {
                if (entry.contains(";")) {
                    // channel id; webhook id
                    String[] elements = entry.split(";");
                    id = Long.parseLong(elements[0].trim());
                    webhookId = Long.parseLong(elements[1].trim());
                } else {
                    // just channel id, no webhook
                    id = Long.parseLong(entry);
                }
            } catch (NumberFormatException e) {
                logger.warning("The Discord channel or webhook ID in '" + entry + "' is not a valid integer.");
                continue;
            }

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

                // Possible edge case: webhook for DM channel
                if (webhookId != -1) {
                    logger.warning(
                            "Channel #" + channel.getName() + " (" + channel.getId() + ") " +
                            "is a DM channel but has webhook with ID '" + webhookId + "'"
                    );
                }
            }

            channels.put(id, channel);

            // If we are supposed to have a webhook
            if (useWebhooks && channel instanceof TextChannel) {
                TextChannel textChannel = (TextChannel) channel;

                // If one is already registered
                if (webhookId != -1) {

                    // Lambda expression requires effectively final vars
                    long finalId = id;
                    long finalWebhookId = webhookId;
                    MessageChannel finalChannel = channel;

                    // Retrieve all webhooks and find ours
                    textChannel.retrieveWebhooks().submit().whenComplete((channelWebhooks, error) -> {
                        if (error != null) {
                            error.printStackTrace();
                        } else {
                            // Search for our webhook.
                            for (Webhook webhook : channelWebhooks) {
                                if (webhook.getIdLong() == finalWebhookId) {
                                    webhookCluster.buildWebhook(webhook.getIdLong(), webhook.getToken());
                                    webhooks.put(finalId, webhook);
                                    return;
                                }
                            }

                            // Couldn't find it
                            logger.warning(
                                    "Couldn't find webhook with ID '" + finalWebhookId + "' for channel #" +
                                            finalChannel.getName() + " (" + finalChannel.getId() + "). Creating a new one."
                            );

                            createWebhook(textChannel);
                        }
                    });
                } else {
                    // We need to create one
                    createWebhook(textChannel);
                }
            }
        }

        if (channelIds.size() > 0)
            logger.info("Loaded " + channels.size() + " of " + channelIds.size() + " Discord channels from config.");

        saveChannelConfig();
    }

    private void saveChannelConfig()
    {
        ArrayList<String> channelWebhookPairs = new ArrayList<>();

        for (long id : channelIds) {
            String entry = Long.toString(id);
            if (webhooks.containsKey(id)) {
                entry += ";" + webhooks.get(id).getId();
            }
            channelWebhookPairs.add(entry);
        }

        if (channelWebhookPairs.size() > 0)
            plugin.getConfig().set("discord-channels", channelWebhookPairs);
        else
            // Delete this list from config if it's empty
            plugin.getConfig().set("discord-channels", null);

        plugin.saveConfig();
    }

    private void createWebhook(TextChannel channel)
    {
        if (!useWebhooks) return;

        try {
            // Create webhook
            channel.createWebhook("DiscordBridge").submit().whenComplete((webhook, error) -> {
                if (error != null) {
                    error.printStackTrace();
                } else {
                    logger.info("Created webhook in channel #" + channel.getName() + " (" + channel.getId() + ")");
                    webhookCluster.buildWebhook(webhook.getIdLong(), webhook.getToken());
                    webhooks.put(channel.getIdLong(), webhook);

                    saveChannelConfig();
                }
            });
        } catch (InsufficientPermissionException e) {
            // Bot doesn't have Manage Webhooks permission.
            logger.warning(
                    "Failed to create webhook in #" + channel.getName() +
                            " (" + channel.getId() + "). Bot does not have permission."
            );
            channel.sendMessage(
                    "Failed to create webhook, the bot doesn't have the **Manage Webhooks** permission."
            ).queue();
        }
    }

    private void removeWebhook(Webhook webhook)
    {
        try {
            // Delete webhook
            webhook.delete();
        } catch (InsufficientPermissionException e) {
            // Bot doesn't have Manage Webhooks permission.
            TextChannel channel = webhook.getChannel();
            logger.warning(
                    "Failed to delete webhook with ID '" + webhook.getId() + "' in #" + channel.getName() +
                            " (" + channel.getId() + "). Bot does not have permission."
            );
            channel.sendMessage(
                    "Failed to delete webhook, the bot doesn't have the **Manage Webhooks** permission."
            ).queue();
        }
    }

    private void addChannel(MessageChannel channel)
    {
        if (!channelIds.add(channel.getIdLong())) {
            channel.sendMessage("This channel is already registered as a listening channel.").queue();
            return;
        }

        if (channel instanceof TextChannel && useWebhooks) {
            createWebhook((TextChannel)channel);
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

        if (webhooks.containsKey(channel.getIdLong())) {
            removeWebhook(webhooks.get(channel.getIdLong()));
        }

        channels.remove(channel.getIdLong());

        channel.sendMessage("Successfully unregistered <#" + channel.getId()  + ">").queue();
        logger.info("Unregistered listening channel: #" + channel.getName() + " (" + channel.getId()  + ")");

        saveChannelConfig();
    }

    /**
     * Send a message to all registered channels.
     * @param msg Raw string message
     */
    private void sendMessageAll(String msg)
    {
        for (MessageChannel channel : channels.values())
        {
            channel.sendMessage(msg).queue();
        }
    }

    private void sendMessageWebhooks(UUID playerId, String username, String msg)
    {
        WebhookMessageBuilder builder = new WebhookMessageBuilder();
        builder.setAvatarUrl("https://crafatar.com/avatars/" + playerId + "?overlay");
        builder.setUsername(username);
        builder.setContent(msg);
        webhookCluster.broadcast(builder.build());

        String nonWebhookMsg = "**" + username + "**: " + msg;
        for (MessageChannel channel : channels.values())
        {
            if (!webhooks.containsKey(channel.getIdLong())) {
                channel.sendMessage(nonWebhookMsg).queue();
            }
        }
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
        // Ignore webhooks
        if (e.isWebhookMessage()) return;

        // Ignore our own messages
        if (e.getAuthor().getIdLong() == plugin.client.getSelfUser().getIdLong()) {
            return;
        }

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

    /*==================================================
     *      Bukkit Event Handlers
     *==================================================*/

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e)
    {
        if (useWebhooks)
            sendMessageWebhooks(e.getPlayer().getUniqueId(), e.getPlayer().getDisplayName(), e.getMessage());
        else
            sendMessageAll("**" + e.getPlayer().getDisplayName() + "**: " + e.getMessage());
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent e)
    {
        if (!reportJoin) return;
        if (e.getResult() != PlayerLoginEvent.Result.ALLOWED) return;

        sendMessageAll(e.getPlayer().getDisplayName() + " joined the game");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e)
    {
        if (!reportLeave) return;

        if (e.getQuitMessage() != null)
            sendMessageAll(ChatColor.stripColor(e.getQuitMessage()));
        else
            sendMessageAll(e.getPlayer().getDisplayName() + " left the game");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e)
    {
        if (!reportDeath) return;

        if (e.getDeathMessage() != null)
            sendMessageAll(ChatColor.stripColor(e.getDeathMessage()));
        else
            sendMessageAll(e.getEntity().getDisplayName() + " died");
    }

}
