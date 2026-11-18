package dev.felinora.kasa;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** config.yml'deki bir kasa tanımı. */
public final class Kasa {

    final String id;
    final String isim;
    final ItemStack anahtarSablonu;
    final List<Odul> oduller;
    final int toplamSans;

    Kasa(String id, String isim, ItemStack anahtarSablonu, List<Odul> oduller) {
        this.id = id;
        this.isim = isim;
        this.anahtarSablonu = anahtarSablonu;
        this.oduller = oduller;
        this.toplamSans = oduller.stream().mapToInt(o -> o.sans).sum();
    }

    /** Şanslara göre rastgele bir ödül seçer. */
    Odul rastgeleOdul() {
        int r = ThreadLocalRandom.current().nextInt(toplamSans);
        for (Odul o : oduller) {
            r -= o.sans;
            if (r < 0) return o;
        }
        return oduller.get(oduller.size() - 1);
    }

    double yuzde(Odul o) {
        return o.sans * 100.0 / toplamSans;
    }
}
