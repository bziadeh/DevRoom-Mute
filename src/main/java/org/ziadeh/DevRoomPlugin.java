package org.ziadeh;

import lombok.Getter;
import me.lucko.helper.plugin.ExtendedJavaPlugin;

@Getter
public class DevRoomPlugin extends ExtendedJavaPlugin {

    private MuteModule muteModule;

    @Override
    protected void enable() {

        muteModule = bindModule(new MuteModule(this));
    }
}
