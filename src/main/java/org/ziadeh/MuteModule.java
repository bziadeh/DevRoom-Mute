package org.ziadeh;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import me.lucko.helper.Commands;
import me.lucko.helper.Events;
import me.lucko.helper.Schedulers;
import me.lucko.helper.gson.GsonProvider;
import me.lucko.helper.gson.JsonBuilder;
import me.lucko.helper.terminable.TerminableConsumer;
import me.lucko.helper.terminable.module.TerminableModule;
import me.lucko.helper.text3.Text;
import me.lucko.helper.time.DurationFormatter;
import me.lucko.helper.utils.Players;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.ziadeh.DevRoomPlugin;
import org.ziadeh.MutedPlayer;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MuteModule implements TerminableModule {

    private DevRoomPlugin plugin;

    // hashmap for quick access
    private Map<UUID, MutedPlayer> mutedPlayerData;

    // json file storage
    private Path dataFile;

    public MuteModule(DevRoomPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = plugin.getDataFolder().toPath().resolve("muted_players.json");
    }

    @Override
    public void setup(@Nonnull TerminableConsumer consumer) {
        consumer.bind(this::save);

        // Load Muted Players
        if(Files.exists(dataFile)) {
            try {
                String jsonString = new String(Files.readAllBytes(dataFile));
                JsonArray array = GsonProvider.prettyPrinting().fromJson(jsonString, JsonElement.class).getAsJsonArray();
                for (JsonElement element : array) {
                    MutedPlayer result = MutedPlayer.deserialize(element);
                    mutedPlayerData.put(result.getUuid(), result);
                }
            } catch (IOException | JsonSyntaxException ex) {
                ex.printStackTrace();
            }
        }

        // Mute Command
        Commands.create()
                .assertUsage("<player> [duration] [reason]")
                .handler(command -> {
                    Player target = command.arg(0).parseOrFail(Player.class);
                    // Mute with no duration or reason specified
                    String durationString = command.arg(1).parse(String.class).orElse(null);
                    if(durationString == null) {
                        mutePlayer(target, command.sender(), "None Specified", Duration.ofDays(100000), true);
                        return;
                    }
                    // Check for invalid duration format
                    Duration duration = getDuration(durationString);
                    if(duration == null) {
                        command.reply("&cInvalid duration specified, please try again.");
                        return;
                    }
                    // Mute the player!
                    String reason = command.arg(2).parse(String.class).orElse("None Specified");
                    mutePlayer(target, command.sender(), reason, duration, true);
                }).registerAndBind(consumer, "mute");

        // Unmute Command
        Commands.create()
                .assertUsage("<player>")
                .handler(command -> {
                    // For the sake of saving time, let's keep things simple
                    Player target = command.arg(0).parseOrFail(Player.class);
                    // Check if the player is muted first
                    if(!isMuted(target)) {
                        command.reply("&cThe player you specified is not muted.");
                        return;
                    }
                    // Unmute and notify!
                    mutedPlayerData.remove(target.getUniqueId());
                    command.reply("&aYou unmuted " + target.getName());
                    Players.msg(target, "&aYou have been unmuted by " + command.sender().getName());
                }).registerAndBind(consumer, "unmute");

        // Prevent Chat
        Events.subscribe(AsyncPlayerChatEvent.class)
                .handler(event -> {
                    final Player player = event.getPlayer();
                    getMuteData(player).ifPresent(data -> {
                        Duration duration = data.getMuteDuration();
                        Instant date = data.getMuteDate();

                        // Check if enough time has elapsed
                        Duration elapsed = Duration.between(date, Instant.now());
                        if(elapsed.compareTo(duration) > 0) {
                            mutedPlayerData.remove(player.getUniqueId());
                            return;
                        }
                        String timeRemaining = DurationFormatter.format(duration.minus(elapsed), true);
                        event.setCancelled(true);

                        // Notify the player
                        Players.msg(player, "&cYou cannot speak, you are muted for another: &f" + timeRemaining);
                    });
                }).bindWith(consumer);

        // Save our data every five minutes
        Schedulers.sync().runRepeating(this::save, 5, TimeUnit.MINUTES, 5, TimeUnit.MINUTES);
    }

    private void save() {
        // Convert the data map into a JsonElement list
        List<JsonElement> elements = mutedPlayerData.values().stream().map(MutedPlayer::serialize).toList();

        // Create an Array and add our elements!
        JsonBuilder.JsonArrayBuilder builder = JsonBuilder.array();
        for (JsonElement element : elements) {
            builder.add(element);
        }

        JsonArray array = builder.build();
        String json = GsonProvider.prettyPrinting().toJson(array);

        try {
            Files.createDirectories(dataFile.getParent());
            Files.write(dataFile, json.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void mutePlayer(Player player, CommandSender sender, String reason, Duration duration, boolean confirm) {
        UUID uuid = player.getUniqueId();
        if(confirm && sender instanceof Player) {
            MuteConfirmationMenu confirmMenu = new MuteConfirmationMenu((Player) sender, player, duration, reason);
            confirmMenu.onAccept(gui -> mutePlayer(player, sender, reason, duration, false));
            confirmMenu.open();
            return;
        }

        // Add the player to our map
        MutedPlayer data = new MutedPlayer(uuid, reason, sender.getName(), Instant.now(), duration);
        mutedPlayerData.put(uuid, data);

        // Notify sender and receiver
        Players.msg(sender, "&aMuted &7" + player.getName() + "&a for &7" + duration + " - &a" + reason);
        Players.msg(player, "&cYou have been muted by &7" + sender.getName() + "&c for &7" + duration + " - &c" + reason);
    }

    public Map<UUID, MutedPlayer> getMutedPlayerData() {
        // read-only
        return Collections.unmodifiableMap(mutedPlayerData);
    }

    public Optional<MutedPlayer> getMuteData(Player player) {
        return Optional.ofNullable(plugin.getMuteModule().getMutedPlayerData().get(player.getUniqueId()));
    }

    public boolean isMuted(Player player) {
        return plugin.getMuteModule().getMutedPlayerData().containsKey(player.getUniqueId());
    }

    public Duration getDuration(String duration) {
        return null;
    }
}
