package me.pesca.fishing;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class QuestMenu extends Menu {
    private static final int[] SLOTS = {11, 13, 15, 21, 23};

    public QuestMenu(PescaPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String title() {
        return "&8» &b&lPESCA &8| &dMisiones diarias";
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void build() {
        border(Material.PURPLE_STAINED_GLASS_PANE);
        for (int s : new int[]{0, 8, 36, 44}) set(s, Ui.pane(Material.MAGENTA_STAINED_GLASS_PANE));
        PlayerData d = plugin.data().get(player.getUniqueId());
        QuestManager qm = plugin.quests();
        qm.ensure(d);

        List<QuestManager.Def> list = qm.today();
        for (int i = 0; i < list.size() && i < SLOTS.length; i++) {
            QuestManager.Def q = list.get(i);
            final int idx = i;
            boolean claimed = qm.isClaimed(d, i);
            boolean done = qm.isDone(d, i);
            int prog = Math.min(q.amount(), d.questProg[i]);

            List<String> lore = new ArrayList<>();
            lore.add("&7" + q.desc());
            lore.add("&7");
            lore.add(Ui.bar((double) prog / q.amount(), 14) + " &f" + prog + "&8/&f" + q.amount());
            lore.add("&7");
            lore.add("&7Premio: &a" + plugin.economy().format(q.money()) + " &8+ &e" + q.xp() + " XP");
            lore.add("&7");
            lore.add(claimed ? "&a✔ Reclamada" : done ? "&e¡Clic para reclamar!" : "&8En progreso...");

            Material icon = claimed ? Material.LIME_DYE : done ? Material.GOLD_INGOT : Material.PAPER;
            ItemStack it = Ui.item(icon, (claimed ? "&a" : done ? "&6&l" : "&f") + "Misión " + (i + 1), lore);
            if (done && !claimed) {
                set(SLOTS[i], Ui.glow(it), e -> {
                    if (qm.claim(player, d, idx)) redraw();
                });
            } else {
                set(SLOTS[i], it);
            }
        }

        long mins = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay()).toMinutes();
        set(31, Ui.item(Material.CLOCK, "&e&lNuevas misiones en",
                "&f" + (mins / 60) + "h " + (mins % 60) + "m"));
        back();
        fill(Material.BLACK_STAINED_GLASS_PANE);
    }
}
