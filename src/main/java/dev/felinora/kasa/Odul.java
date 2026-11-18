package dev.felinora.kasa;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Bir kasadaki tek bir ödül. */
public final class Odul {

    final ItemStack esya;      // menüde görünen ve (sadece-komut değilse) verilen eşya
    final List<String> komutlar;
    final boolean sadeceKomut;
    final int sans;

    Odul(ItemStack esya, List<String> komutlar, boolean sadeceKomut, int sans) {
        this.esya = esya;
        this.komutlar = komutlar;
        this.sadeceKomut = sadeceKomut;
        this.sans = sans;
    }

    /** Ödülün oyuncunun göreceği adı, ör. "16x Demir Külçesi". */
    String ad() {
        String isim = esya.hasItemMeta() && esya.getItemMeta().hasDisplayName()
                ? esya.getItemMeta().getDisplayName()
                : guzelAd(esya.getType().name());
        return esya.getAmount() > 1 ? esya.getAmount() + "x " + isim : isim;
    }

    void ver(Player oyuncu) {
        if (!sadeceKomut) {
            Map<Integer, ItemStack> artan = oyuncu.getInventory().addItem(esya.clone());
            artan.values().forEach(e -> oyuncu.getWorld().dropItemNaturally(oyuncu.getLocation(), e));
        }
        for (String komut : komutlar) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), komut.replace("%oyuncu%", oyuncu.getName()));
        }
    }

    /** DIAMOND_SWORD -> Diamond Sword */
    static String guzelAd(String enumAdi) {
        StringBuilder sb = new StringBuilder();
        for (String kelime : enumAdi.toLowerCase().split("_")) {
            if (kelime.isEmpty()) continue;
            sb.append(Character.toUpperCase(kelime.charAt(0))).append(kelime.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
