package org.ziadeh;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.lucko.helper.gson.GsonSerializable;
import me.lucko.helper.gson.JsonBuilder;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class MutedPlayer implements GsonSerializable {

    private final UUID uuid;
    private final String reason;
    private final String mutedBy;
    private final Instant muteDate;
    private final Duration muteDuration;

    @Nonnull
    @Override
    public JsonElement serialize() {
        return JsonBuilder.object()
                .add("uuid", uuid.toString())
                .add("reason", reason)
                .add("mutedBy", mutedBy)
                .add("muteDate", muteDate.toEpochMilli())
                .add("muteDuration", muteDuration.toSeconds())
                .build();
    }

    public static MutedPlayer deserialize(JsonElement element) {
        JsonObject object = element.getAsJsonObject();
        UUID uuid = UUID.fromString(object.get("uuid").getAsString());
        String reason = object.get("reason").getAsString();
        String mutedBy = object.get("mutedBy").getAsString();
        Instant muteDate = Instant.ofEpochMilli(object.get("muteDate").getAsLong());
        Duration duration = Duration.ofSeconds(object.get("muteDuration").getAsInt());
        return new MutedPlayer(uuid, reason, mutedBy, muteDate, duration);
    }
}
