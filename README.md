# Kalanim

Bimcell ve Turk Telekom hatlarinda kalan bakiye, dakika, SMS ve internet bilgilerini tek tusla ogrenmeyi saglayan sade bir Android uygulamasidir.

Dedelerin veya kucuk yazilari okumakta zorlananlarin "Benim internetim bitti mi?" sorusuna kesin cozum olarak gelistirilmistir.

## Ozellikler

- Tek Tusla Sorgu: Butona basildiginda 5555'e gerekli mesajlari atar ve yaniti bekler.
- Sesli Okuma: Yanit geldiginde bakiye ve kalan haklari Turkce olarak sesli okur.
- Tekrar Dinle: Kaciranlar icin aninda yeniden seslendirir.
- Otomatik Geri Sayim: Paket bitisine kac gun kaldigini her gece 00:00'da kendiliginden hesaplayip gunceller.
- Ana Ekran Widget'lari: 3 farkli boyutta (hizli buton, ozet ve detayli) widget destegi sunar. Hizli buton uygulamayi acmadan arka planda sorguyu calistirip sonucu sesli okur.

## Kurulum

Releases sayfasindan guncel APK dosyasini indirip telefonunuza kurabilirsiniz.

## Gelistirme

```bash
git clone https://github.com/kayafurkanipekci/Kalanim.git
cd Kalanim
./gradlew assembleRelease
```
