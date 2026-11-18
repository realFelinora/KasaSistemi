package dev.felinora.kasa;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class KasaSistemi extends JavaPlugin implements Listener {

    private final Map<String, Kasa> kasalar = new LinkedHashMap<>();
    /** "dunya:x:y:z" -> kasa id */
    private final Map<String, String> yerler = new HashMap<>();
    private final Map<String, TextDisplay> yaziler = new HashMap<>();
    private final Map<UUID, Animasyon> acanlar = new HashMap<>();

    private NamespacedKey anahtarEtiketi;
    private NamespacedKey yaziEtiketi;
    private File veriDosyasi;
    private YamlConfiguration veri;
    private int tik = 0;

    // ================= Açılış / kapanış =================

    @Override
    public void onEnable() {
        anahtarEtiketi = new NamespacedKey(this, "anahtar");
        yaziEtiketi = new NamespacedKey(this, "kasa_yazi");
        saveDefaultConfig();
        veriDosyasi = new File(getDataFolder(), "veri.yml");
        veri = YamlConfiguration.loadConfiguration(veriDosyasi);

        ayarlariYukle();
        yerleriYukle();
        getServer().getPluginManager().registerEvents(this, this);

        // Parçacıklar + kaybolan yazıları geri getirme (her 5 tikte bir)
        Bukkit.getScheduler().runTaskTimer(this, this::efektler, 20L, 5L);
        getLogger().info(kasalar.size() + " kasa, " + yerler.size() + " kasa bloğu yüklendi.");
    }

    @Override
    public void onDisable() {
        for (Animasyon a : new ArrayList<>(acanlar.values())) a.bitir(false);
        yaziler.values().forEach(TextDisplay::remove);
        yaziler.clear();
    }

    private void ayarlariYukle() {
        reloadConfig();
        kasalar.clear();
        ConfigurationSection bolum = getConfig().getConfigurationSection("kasalar");
        if (bolum == null) return;
        for (String id : bolum.getKeys(false)) {
            ConfigurationSection k = bolum.getConfigurationSection(id);
            if (k == null) continue;

            Material anahtarMalzeme = Material.matchMaterial(k.getString("anahtar.malzeme", "TRIPWIRE_HOOK"));
            if (anahtarMalzeme == null) anahtarMalzeme = Material.TRIPWIRE_HOOK;
            ItemStack anahtar = new ItemStack(anahtarMalzeme);
            ItemMeta meta = anahtar.getItemMeta();
            meta.setDisplayName(renk(k.getString("anahtar.isim", id + " anahtarı")));
            meta.setLore(k.getStringList("anahtar.aciklama").stream().map(KasaSistemi::renk).collect(Collectors.toList()));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(anahtarEtiketi, PersistentDataType.STRING, id);
            anahtar.setItemMeta(meta);

            List<Odul> oduller = new ArrayList<>();
            for (Map<?, ?> m : k.getMapList("oduller")) {
                Odul o = odulOku(id, m);
                if (o != null) oduller.add(o);
            }
            if (oduller.isEmpty()) {
                getLogger().warning("'" + id + "' kasasında hiç geçerli ödül yok, atlandı.");
                continue;
            }
            kasalar.put(id, new Kasa(id, k.getString("isim", id), anahtar, oduller));
        }
    }

    private Odul odulOku(String kasaId, Map<?, ?> m) {
        String esyaSatiri = String.valueOf(m.get("esya"));
        String[] parca = esyaSatiri.split(":");
        Material malzeme = Material.matchMaterial(parca[0].trim());
        if (malzeme == null || !malzeme.isItem()) {
            getLogger().warning(kasaId + ": bilinmeyen eşya '" + esyaSatiri + "'");
            return null;
        }
        int adet = 1;
        if (parca.length > 1) {
            try { adet = Math.max(1, Integer.parseInt(parca[1].trim())); } catch (NumberFormatException ignored) { }
        }
        ItemStack esya = new ItemStack(malzeme, adet);
        ItemMeta meta = esya.getItemMeta();
        if (meta != null) {
            if (m.get("isim") != null) meta.setDisplayName(renk(String.valueOf(m.get("isim"))));
            if (m.get("buyuler") instanceof List<?> buyuler) {
                for (Object b : buyuler) {
                    String[] bp = String.valueOf(b).split(":");
                    Enchantment buyu = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(bp[0].trim().toLowerCase()));
                    if (buyu == null) {
                        getLogger().warning(kasaId + ": bilinmeyen büyü '" + b + "'");
                        continue;
                    }
                    int seviye = bp.length > 1 ? Integer.parseInt(bp[1].trim()) : 1;
                    meta.addEnchant(buyu, seviye, true);
                }
            }
            esya.setItemMeta(meta);
        }
        List<String> komutlar = new ArrayList<>();
        if (m.get("komutlar") instanceof List<?> l) l.forEach(x -> komutlar.add(String.valueOf(x)));
        boolean sadeceKomut = Boolean.TRUE.equals(m.get("sadece-komut"));
        int sans = m.get("sans") instanceof Number n ? Math.max(1, n.intValue()) : 1;
        return new Odul(esya, komutlar, sadeceKomut, sans);
    }

    // ================= Kasa yerleri =================

    private static String anahtar(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private static Location konum(String a) {
        String[] p = a.split(":");
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        return new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    private void yerleriYukle() {
        yerler.clear();
        ConfigurationSection s = veri.getConfigurationSection("yerler");
        if (s != null) for (String k : s.getKeys(false)) yerler.put(k.replace(";", ":"), s.getString(k));
    }

    private void veriKaydet() {
        veri.set("yerler", null);
        yerler.forEach((k, v) -> veri.set("yerler." + k.replace(":", ";"), v));
        try { veri.save(veriDosyasi); } catch (IOException e) { getLogger().severe("veri.yml kaydedilemedi: " + e.getMessage()); }
    }

    // ================= Anahtarlar =================

    ItemStack anahtarOlustur(Kasa kasa, int adet) {
        ItemStack a = kasa.anahtarSablonu.clone();
        a.setAmount(adet);
        return a;
    }

    /** Eşya bir kasa anahtarıysa kasa id'sini döndürür. İsim değil gizli etiket kontrol edilir. */
    String anahtarKasasi(ItemStack e) {
        if (e == null || !e.hasItemMeta()) return null;
        return e.getItemMeta().getPersistentDataContainer().get(anahtarEtiketi, PersistentDataType.STRING);
    }

    // ================= Olaylar =================

    @EventHandler(priority = EventPriority.HIGH)
    public void kasayaTiklandi(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        String kasaId = yerler.get(anahtar(e.getClickedBlock().getLocation()));
        if (kasaId == null) return;
        e.setCancelled(true);
        Kasa kasa = kasalar.get(kasaId);
        if (kasa == null) return;
        Player p = e.getPlayer();

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            onizlemeAc(p, kasa);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!p.hasPermission("kasa.kullan")) { mesaj(p, "yetki-yok"); return; }
        if (acanlar.containsKey(p.getUniqueId())) { mesaj(p, "zaten-aciyor"); return; }

        ItemStack elde = p.getInventory().getItemInMainHand();
        String anahtarId = anahtarKasasi(elde);
        if (anahtarId == null) { mesaj(p, "anahtar-yok"); return; }
        if (!anahtarId.equals(kasaId)) {
            mesaj(p, "yanlis-anahtar", "%kasa%", kasa.isim);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        elde.setAmount(elde.getAmount() - 1);
        Animasyon a = new Animasyon(this, p, kasa, Math.max(15, getConfig().getInt("animasyon-adim", 45)));
        acanlar.put(p.getUniqueId(), a);
        a.baslat();
    }

    void animasyonBitti(Player p) { acanlar.remove(p.getUniqueId()); }

    @EventHandler
    public void menuKapandi(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Animasyon a && !a.bittiMi()) a.bitir(false);
    }

    @EventHandler
    public void cikti(PlayerQuitEvent e) {
        Animasyon a = acanlar.get(e.getPlayer().getUniqueId());
        if (a != null) a.bitir(false);
    }

    @EventHandler
    public void menuTiklama(InventoryClickEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (h instanceof Animasyon || h instanceof Onizleme) e.setCancelled(true);
    }

    @EventHandler
    public void menuSurukleme(InventoryDragEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (h instanceof Animasyon || h instanceof Onizleme) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void kasaKirildi(BlockBreakEvent e) {
        if (yerler.containsKey(anahtar(e.getBlock().getLocation()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void anahtarKonuldu(BlockPlaceEvent e) {
        if (anahtarKasasi(e.getItemInHand()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void girdi(PlayerJoinEvent e) {
        // Animasyon sırasında sunucu kapanırsa vs. bekleyen ödülleri ver.
        String yol = "bekleyen." + e.getPlayer().getUniqueId();
        List<String> bekleyen = veri.getStringList(yol);
        if (bekleyen.isEmpty()) return;
        for (String s : bekleyen) {
            String[] p = s.split(":");
            Kasa k = kasalar.get(p[0]);
            if (k == null) continue;
            int i = Integer.parseInt(p[1]);
            if (i >= 0 && i < k.oduller.size()) k.oduller.get(i).ver(e.getPlayer());
        }
        veri.set(yol, null);
        veriKaydet();
    }

    void bekleyenOdulEkle(UUID oyuncu, Kasa kasa, Odul odul) {
        String yol = "bekleyen." + oyuncu;
        List<String> l = new ArrayList<>(veri.getStringList(yol));
        l.add(kasa.id + ":" + kasa.oduller.indexOf(odul));
        veri.set(yol, l);
        veriKaydet();
    }

    // ================= Önizleme menüsü =================

    static final class Onizleme implements InventoryHolder {
        Inventory menu;
        @Override public Inventory getInventory() { return menu; }
    }

    private void onizlemeAc(Player p, Kasa kasa) {
        Onizleme h = new Onizleme();
        int boyut = Math.min(54, ((kasa.oduller.size() + 8) / 9) * 9);
        h.menu = Bukkit.createInventory(h, Math.max(9, boyut), renk(kasa.isim + " &8- İçerik"));
        for (Odul o : kasa.oduller) {
            ItemStack g = o.esya.clone();
            ItemMeta m = g.getItemMeta();
            if (m != null) {
                List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(renk(String.format("&7Şans: &e%%%.1f", kasa.yuzde(o))));
                m.setLore(lore);
                g.setItemMeta(m);
            }
            h.menu.addItem(g);
        }
        p.openInventory(h.menu);
    }

    // ================= Yazılar ve parçacıklar =================

    private void efektler() {
        tik++;
        double aci = tik * 0.35;
        for (Map.Entry<String, String> yer : yerler.entrySet()) {
            Location l = konum(yer.getKey());
            if (l == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            Location orta = l.clone().add(0.5, 0.5, 0.5);

            // Kayan iki parçacık halkası
            for (int i = 0; i < 2; i++) {
                double a = aci + i * Math.PI;
                l.getWorld().spawnParticle(Particle.END_ROD,
                        orta.clone().add(Math.cos(a) * 0.8, 0.2 + Math.sin(tik * 0.15) * 0.3, Math.sin(a) * 0.8),
                        1, 0, 0, 0, 0);
            }

            TextDisplay yazi = yaziler.get(yer.getKey());
            if (yazi == null || !yazi.isValid()) {
                Kasa k = kasalar.get(yer.getValue());
                if (k == null) continue;
                yazi = l.getWorld().spawn(l.clone().add(0.5, 1.35, 0.5), TextDisplay.class, t -> {
                    t.setText(renk(k.isim + "\n&7Sağ tık: &aAç  &8|  &7Sol tık: &eİçerik"));
                    t.setBillboard(Display.Billboard.CENTER);
                    t.setPersistent(false);
                    t.getPersistentDataContainer().set(yaziEtiketi, PersistentDataType.BYTE, (byte) 1);
                });
                yaziler.put(yer.getKey(), yazi);
            }
        }
    }

    private void yaziSil(String yerAnahtari) {
        TextDisplay t = yaziler.remove(yerAnahtari);
        if (t != null) t.remove();
    }

    // ================= Komutlar =================

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("kasalar")) {
            if (!(s instanceof Player p)) return true;
            Location alan = veri.getLocation("alan");
            if (alan == null) { mesaj(p, "alan-yok"); return true; }
            p.teleport(alan);
            p.playSound(alan, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            mesaj(p, "isinlandin");
            return true;
        }

        if (!s.hasPermission("kasa.yonetici")) { mesaj(s, "yetki-yok"); return true; }
        if (args.length == 0) { yardim(s); return true; }

        switch (args[0].toLowerCase()) {
            case "anahtar" -> {
                if (args.length < 3) { s.sendMessage(renk("&e/kasa anahtar <oyuncu|*> <kasa> [adet]")); return true; }
                Kasa k = kasalar.get(args[2].toLowerCase());
                if (k == null) { s.sendMessage(renk("&cBöyle bir kasa yok. Kasalar: &f" + String.join(", ", kasalar.keySet()))); return true; }
                int adet = 1;
                if (args.length > 3) try { adet = Math.max(1, Integer.parseInt(args[3])); } catch (NumberFormatException ignored) { }
                List<Player> hedefler = new ArrayList<>();
                if (args[1].equals("*")) hedefler.addAll(Bukkit.getOnlinePlayers());
                else {
                    Player h = Bukkit.getPlayerExact(args[1]);
                    if (h == null) { s.sendMessage(renk("&cOyuncu çevrim içi değil.")); return true; }
                    hedefler.add(h);
                }
                for (Player h : hedefler) {
                    h.getInventory().addItem(anahtarOlustur(k, adet)).values()
                            .forEach(x -> h.getWorld().dropItemNaturally(h.getLocation(), x));
                    h.sendMessage(renk(metin("onek") + "&a" + adet + "x " + k.isim + " &aanahtarı aldın!"));
                }
                s.sendMessage(renk("&a" + hedefler.size() + " oyuncuya " + adet + "x anahtar verildi."));
            }
            case "koy" -> {
                if (!(s instanceof Player p)) return true;
                if (args.length < 2 || !kasalar.containsKey(args[1].toLowerCase())) {
                    s.sendMessage(renk("&e/kasa koy <kasa> &7(bakılan bloğa) Kasalar: &f" + String.join(", ", kasalar.keySet())));
                    return true;
                }
                Block b = p.getTargetBlockExact(6);
                if (b == null || b.getType().isAir()) { s.sendMessage(renk("&cBir bloğa bakmalısın.")); return true; }
                String a = anahtar(b.getLocation());
                yaziSil(a);
                yerler.put(a, args[1].toLowerCase());
                veriKaydet();
                s.sendMessage(renk("&aBu blok artık " + kasalar.get(args[1].toLowerCase()).isim + " &a!"));
            }
            case "kaldir" -> {
                if (!(s instanceof Player p)) return true;
                Block b = p.getTargetBlockExact(6);
                if (b == null || yerler.remove(anahtar(b.getLocation())) == null) {
                    s.sendMessage(renk("&cBaktığın blok bir kasa değil."));
                    return true;
                }
                yaziSil(anahtar(b.getLocation()));
                veriKaydet();
                s.sendMessage(renk("&aKasa kaldırıldı."));
            }
            case "alan" -> {
                if (!(s instanceof Player p)) return true;
                veri.set("alan", p.getLocation());
                veriKaydet();
                s.sendMessage(renk("&aKasa alanı burası olarak ayarlandı. &7(/kasalar)"));
            }
            case "liste" -> {
                s.sendMessage(renk("&6Kasalar:"));
                for (Kasa k : kasalar.values()) {
                    long sayi = yerler.values().stream().filter(k.id::equals).count();
                    s.sendMessage(renk(" &7- &f" + k.id + " &8(" + k.isim + "&8) &7ödül: &e" + k.oduller.size() + " &7blok: &e" + sayi));
                }
            }
            case "yenile" -> {
                ayarlariYukle();
                new ArrayList<>(yaziler.keySet()).forEach(this::yaziSil);
                s.sendMessage(renk("&aAyarlar yeniden yüklendi."));
            }
            default -> yardim(s);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("kasa") || !s.hasPermission("kasa.yonetici")) return List.of();
        List<String> sec = new ArrayList<>();
        if (args.length == 1) sec.addAll(List.of("anahtar", "koy", "kaldir", "alan", "liste", "yenile"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("anahtar")) {
            sec.add("*");
            Bukkit.getOnlinePlayers().forEach(p -> sec.add(p.getName()));
        } else if ((args.length == 2 && args[0].equalsIgnoreCase("koy")) || (args.length == 3 && args[0].equalsIgnoreCase("anahtar"))) {
            sec.addAll(kasalar.keySet());
        }
        String son = args[args.length - 1].toLowerCase();
        return sec.stream().filter(x -> x.toLowerCase().startsWith(son)).collect(Collectors.toList());
    }

    private void yardim(CommandSender s) {
        s.sendMessage(renk("&6&lKasaSistemi &7komutları:"));
        s.sendMessage(renk(" &e/kasa koy <kasa> &7- Baktığın bloğu kasa yapar"));
        s.sendMessage(renk(" &e/kasa kaldir &7- Baktığın kasayı kaldırır"));
        s.sendMessage(renk(" &e/kasa anahtar <oyuncu|*> <kasa> [adet] &7- Anahtar verir"));
        s.sendMessage(renk(" &e/kasa alan &7- /kasalar ışınlanma noktasını ayarlar"));
        s.sendMessage(renk(" &e/kasa liste &7- Kasaları listeler"));
        s.sendMessage(renk(" &e/kasa yenile &7- config.yml'i yeniden yükler"));
    }

    // ================= Mesaj yardımcıları =================

    static String renk(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    String metin(String anahtar, String... degisenler) {
        String m = getConfig().getString("mesajlar." + anahtar, anahtar);
        for (int i = 0; i + 1 < degisenler.length; i += 2) m = m.replace(degisenler[i], degisenler[i + 1]);
        return m;
    }

    void mesaj(CommandSender kime, String anahtar, String... degisenler) {
        kime.sendMessage(renk(metin("onek") + metin(anahtar, degisenler)));
    }
}
