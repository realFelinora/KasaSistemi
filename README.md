# KasaSistemi

Paper/Spigot sunucuları için **anahtarlı ve animasyonlu Türkçe kasa (sandık) sistemi**.
Oyuncu doğru anahtarla kasaya sağ tıklar, eşyalar döner, yavaşlar ve rastgele bir ödülde durur.

**⬇️ İndir:** [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/key-crates-with-spin-animation-kasasistemi) (önerilen) • [GitHub Releases](../../releases)

## Özellikler
- **Sınırsız kasa:** Her kasanın kendi adı, anahtarı ve ödül listesi var
- **Kasaya özel anahtarlar:** Demir anahtar Efsanevi kasayı açmaz
- **Sahte anahtara karşı korumalı:** Anahtarlar gizli bir etiketle doğrulanır, örsle isim değiştirmek işe yaramaz
- **Dönme animasyonu:** Renkli camlar, giderek yavaşlayan şerit, tık ve zafer sesleri
- **Sol tıkla önizleme:** Kasadaki tüm ödüller ve yüzde şansları
- **Kasa üstünde yazı** ve dönen parçacık efektleri
- **Nadir ödül duyurusu:** Şansı düşük bir ödül çıkınca tüm sunucuya duyurulur
- **`/kasalar`** komutuyla kasa alanına ışınlanma
- Ödül olarak **eşya** (özel isim ve büyülerle) veya **konsol komutu**
- Menü kapatılsa ya da oyuncu çıksa bile ödül kaybolmaz
- Tüm mesajlar Türkçe ve `config.yml` üzerinden değiştirilebilir

## Komutlar
| Komut | Açıklama | Yetki |
|---|---|---|
| `/kasalar` | Kasa alanına ışınlanır | `kasa.kullan` (herkes) |
| `/kasa koy <kasa>` | Baktığın bloğu kasa yapar | `kasa.yonetici` (OP) |
| `/kasa kaldir` | Baktığın kasayı kaldırır | `kasa.yonetici` |
| `/kasa anahtar <oyuncu\|*> <kasa> [adet]` | Anahtar verir (konsoldan da çalışır) | `kasa.yonetici` |
| `/kasa alan` | `/kasalar` ışınlanma noktasını ayarlar | `kasa.yonetici` |
| `/kasa liste` | Kasaları listeler | `kasa.yonetici` |
| `/kasa yenile` | `config.yml` dosyasını yeniden yükler | `kasa.yonetici` |

Kasaya **sağ tık** açar, **sol tık** içeriği gösterir.

## Kurulum
1. [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/key-crates-with-spin-animation-kasasistemi) ya da [Releases](../../releases) kısmından `KasaSistemi-x.x.x.jar` dosyasını indirin.
2. Sunucunun `plugins` klasörüne atın ve sunucuyu yeniden başlatın.
3. Bir sandığa bakıp `/kasa koy demir` yazın, kendinize `/kasa anahtar <adınız> demir 1` ile anahtar verin.
4. Kasaları ve ödülleri `plugins/KasaSistemi/config.yml` dosyasından düzenleyip `/kasa yenile` yazın.

EssentialsX kullanıyorsanız kasa alanında `/setwarp kasalar` yazarak `/warp kasalar` da kullanabilirsiniz.

## Örnek ödül ayarı
```yaml
oduller:
  - { esya: "DIAMOND:6", sans: 25 }
  - { esya: "NETHERITE_SWORD:1", isim: "&d&lEfsanevi Kılıç", buyuler: ["SHARPNESS:5"], sans: 10 }
  - { esya: "GOLD_INGOT:1", sadece-komut: true, komutlar: ["eco give %oyuncu% 1000"], sans: 5 }
```
`sans` değerleri ağırlıktır: büyük olan daha sık çıkar.

**Uyumluluk:** Paper / Spigot 1.21+, Java 21+

## Derleme
```sh
./gradlew build
# çıktı: build/libs/KasaSistemi-1.0.0.jar
```
