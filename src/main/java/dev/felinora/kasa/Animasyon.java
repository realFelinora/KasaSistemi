package dev.felinora.kasa;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kasa açma animasyonu: ortadaki satırda eşyalar sağdan sola kayar,
 * gittikçe yavaşlar ve ortadaki (13. slot) eşyada durur.
 */
public final class Animasyon extends BukkitRunnable implements InventoryHolder {

    private static final Material[] CAMLAR = {
            Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE
    };

    private final KasaSistemi eklenti;
    private final Player oyuncu;
    private final Kasa kasa;
    private final Odul kazanan;
    private final List<Odul> serit = new ArrayList<>();
    private final Inventory menu;
    private final int adimSayisi;

    private int adim = 0;
    private int bekleme = 0;
    private boolean bitti = false;

    Animasyon(KasaSistemi eklenti, Player oyuncu, Kasa kasa, int adimSayisi) {
        this.eklenti = eklenti;
        this.oyuncu = oyuncu;
        this.kasa = kasa;
        this.adimSayisi = adimSayisi;
        this.kazanan = kasa.rastgeleOdul();

        // Şeridi doldur: son adımda ortaya (index adimSayisi + 4) kazanan gelecek.
        for (int i = 0; i < adimSayisi + 9; i++) serit.add(kasa.rastgeleOdul());
        serit.set(adimSayisi + 4, kazanan);

        menu = Bukkit.createInventory(this, 27, KasaSistemi.renk(kasa.isim));
        camlariBoya();
        ItemStack ok = isimliEsya(Material.HOPPER, "&e▼ Kazanan ▼");
        menu.setItem(4, ok);
        menu.setItem(22, isimliEsya(Material.HOPPER, "&e▲ Kazanan ▲"));
        seridiCiz();
    }

    void baslat() {
        oyuncu.openInventory(menu);
        runTaskTimer(eklenti, 1L, 1L);
    }

    @Override
    public void run() {
        if (bitti) return;
        if (!oyuncu.isOnline()) { bitir(false); return; }

        if (bekleme > 0) { bekleme--; return; }

        adim++;
        seridiCiz();
        camlariBoya();
        float ton = 0.8f + (adim / (float) adimSayisi) * 1.0f;
        oyuncu.playSound(oyuncu.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, ton);

        if (adim >= adimSayisi) { bitir(true); return; }

        // Başta hızlı, sona doğru yavaşla.
        int kalan = adimSayisi - adim;
        bekleme = kalan > 20 ? 0 : kalan > 12 ? 1 : kalan > 6 ? 2 : kalan > 3 ? 4 : 7;
    }

    /** Oyuncu menüyü erken kapatırsa da ödül yine verilir. */
    void bitir(boolean menuyuSonraKapat) {
        if (bitti) return;
        bitti = true;
        cancel();
        eklenti.animasyonBitti(oyuncu);

        if (!oyuncu.isOnline()) {
            // Çevrim dışı oyuncunun ödülü kaybolmasın: girişte verilir.
            eklenti.bekleyenOdulEkle(oyuncu.getUniqueId(), kasa, kazanan);
            return;
        }

        kazanan.ver(oyuncu);
        oyuncu.playSound(oyuncu.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        oyuncu.playSound(oyuncu.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        eklenti.mesaj(oyuncu, "kazandin", "%kasa%", kasa.isim, "%odul%", kazanan.ad());

        if (kasa.yuzde(kazanan) < eklenti.getConfig().getDouble("duyuru-sans-esigi", 5)) {
            String duyuru = eklenti.metin("duyuru", "%oyuncu%", oyuncu.getName(),
                    "%kasa%", kasa.isim, "%odul%", kazanan.ad());
            Bukkit.broadcastMessage(KasaSistemi.renk(eklenti.metin("onek") + duyuru));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
            }
        }

        if (menuyuSonraKapat) {
            // Kazananı vurgula, 2 saniye sonra menüyü kapat.
            for (int i = 9; i <= 17; i++) if (i != 13) menu.setItem(i, isimliEsya(Material.BLACK_STAINED_GLASS_PANE, " "));
            Bukkit.getScheduler().runTaskLater(eklenti, () -> {
                if (oyuncu.isOnline() && oyuncu.getOpenInventory().getTopInventory().getHolder() == this) {
                    oyuncu.closeInventory();
                }
            }, 40L);
        }
    }

    boolean bittiMi() { return bitti; }

    private void seridiCiz() {
        for (int i = 0; i < 9; i++) {
            Odul o = serit.get(adim + i);
            ItemStack gosterim = o.esya.clone();
            ItemMeta meta = gosterim.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add(KasaSistemi.renk(String.format("&7Şans: &e%%%.1f", kasa.yuzde(o))));
                meta.setLore(lore);
                gosterim.setItemMeta(meta);
            }
            menu.setItem(9 + i, gosterim);
        }
    }

    private void camlariBoya() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 27; i++) {
            if (i >= 9 && i <= 17) continue;
            if (i == 4 || i == 22) continue;
            menu.setItem(i, isimliEsya(CAMLAR[r.nextInt(CAMLAR.length)], " "));
        }
    }

    static ItemStack isimliEsya(Material m, String isim) {
        ItemStack e = new ItemStack(m);
        ItemMeta meta = e.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(KasaSistemi.renk(isim));
            e.setItemMeta(meta);
        }
        return e;
    }

    @Override
    public Inventory getInventory() { return menu; }
}
