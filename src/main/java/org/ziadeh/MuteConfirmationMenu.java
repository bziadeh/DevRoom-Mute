package org.ziadeh;

import me.lucko.helper.item.ItemStackBuilder;
import me.lucko.helper.menu.Gui;
import me.lucko.helper.menu.Item;
import me.lucko.helper.menu.scheme.MenuPopulator;
import me.lucko.helper.menu.scheme.MenuScheme;
import me.lucko.helper.time.DurationFormatter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.function.Consumer;

public class MuteConfirmationMenu extends Gui {

    private final MenuScheme border = new MenuScheme()
            .mask("111111111")
            .mask("110101011")
            .mask("111111111");

    private final Player executor;
    private final Player target;
    private final Duration duration;
    private final String reason;

    private Consumer<Gui> onAccept;
    private Consumer<Gui> onDecline;

    public MuteConfirmationMenu(Player commandExecutor, Player target, Duration duration, String reason) {
        super(commandExecutor, 3, "Mute  " + target.getName());
        this.executor = commandExecutor;
        this.target = target;
        this.duration = duration;
        this.reason = reason;
    }

    public void onAccept(Consumer<Gui> onAccept) {
        this.onAccept = onAccept;
    }

    @Override
    public void redraw() {
        if(isFirstDraw()) {
            Item borderItem = ItemStackBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").buildItem().build();

            // populate menu with border
            MenuPopulator populator = new MenuPopulator(this, border);
            while(populator.hasSpace()) {
                populator.accept(borderItem);
            }

            Item decline = ItemStackBuilder.of(Material.RED_STAINED_GLASS_PANE)
                    .name("&cCancel")
                    .lore("&7Click to cancel this mute.")
                    .build(() -> {
                        close();
                        getPlayer().playSound(getPlayer(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1f);
                    });

            Item accept  = ItemStackBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                    .name("&aConfirm")
                    .lore("&7Click to confirm this mute.")
                    .build(() -> {
                        close();
                        onAccept.accept(this);
                        getPlayer().playSound(getPlayer(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1f);
                    });

            Item info = ItemStackBuilder.of(Material.PAPER)
                    .name("Mute " + target.getName())
                    .lore("&fDuration: &c" + (duration.toDays() >= 100000 ? "Permanent" : DurationFormatter.format(duration, true)))
                    .lore("&fReason: &c" + reason)
                    .buildItem().build();

            setItem(11, decline);
            setItem(13, info);
            setItem(15, accept);
        }
    }
}
