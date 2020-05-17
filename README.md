# DiscordBridge

Simple Minecraft ⇔ Discord bridge. This plugin uses [JDA](https://github.com/DV8FromTheWorld/JDA) and [discord-webhooks](https://github.com/MinnDevelopment/discord-webhooks).

## Running

Just drop the jar in your `plugins` folder.

You will have to edit the `config.yml` that is generated after first run and add your Discord token there.

Use `>register` in a Discord channel to connect it to Minecraft, and `>unregister` to disconnect it.

If the bot does not have the `Manage Webhooks` permission, but `use-webhooks` is enabled then messages will show up without webhook avatar and username.

## Default Config

```yaml
token: 'YOUR_TOKEN_HERE'
command-prefix: '>'
chat-format: '&f[&3Discord&f] {username}: {message}'
webhooks:
  enabled: true
  avatar-url: 'https://crafatar.com/avatars/{uuid}?overlay'
reported-events:
  join: true
  leave: true
  death: true
```

## Building

Run the gradle task `shadowJar`. For testing, I use a PaperMC server in `test/Paper`, so running the gradle task `copyJarToPlugins` is a fast way to build the plugin and install it on a test server.
