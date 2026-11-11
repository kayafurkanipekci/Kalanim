# Kalanım 📱🍗

> *"Dedemin her ay 'Oğlum bu ay benim neyim kalmış bi baksana' demesinden gına gelen bir torunun vatana millete en büyük hizmeti."*

**Kalanım**, Bimcell ve Türk Telekom kullanıcılarının kalan bakiye, dakika, SMS ve internet haklarını **tek dokunuşla** sorgulayan, sonucu ekrana kocaman harflerle basıp üstüne bir de **bağıra bağıra sesli okuyan** ultra sade Android uygulamasıdır.

---

## 🧐 Bu Proje Neden Doğdu?

Bir gün dedeniz yanınıza gelir ve sorar:
> — *"Yavrum benim internet bitti mi? Şurada bi video açtım donup duruyor."*

Siz de telefona bakıp 5555'e `KALAN` yazıp SMS atarsınız. Arkasından bir de `KALAN TL` atarsınız. Gelen mesajda:
> *"BIMCELL Dost Kucuk paketinizden 26.11.2026 11:56 tarihine kadar kullanabileceginiz 246 adet yurt ici her yone SMS, 641 dakika 6 saniye..."*

Dedeye bunu okutmaya çalışırsınız, gözlüğünü arar bulamaz. Okusanız *"Kaç kaldı dedin? Kaç gün var?"* diye 3 kere sorar.

**İşte Kalanım bu dramı tarihe gömmek için geliştirildi.**

---

## ✨ Hayat Kurtaran Özellikler

- **Tek Dokunuşla Sorgu:** Ekranda dev gibi bir **"KALANI ÖĞREN"** butonu var. Basıyorsun, 5555'e gereken mesajları kendisi atıp cevabı bekliyor.
- **Google Teyze Sesli Okuyor:** Gözlük arama derdine son. Cevap gelir gelmez Türkçe ses motoru devreye giriyor:  
  *— "Hattında 22 lira bakiye var. Kalan hakların: 641 dakika konuşma, 246 SMS, 3 gigabayt internet. 15 gün kaldı."*
- **Tekrar Dinle Butonu:** İlkinde anlamayanlar veya televizyonun sesi açıkken basanlar için anında tekrar okuma.
- **Akıllı Geri Sayım:** Paket bitiş tarihini hafızaya atar. Her gece saat 00:00 olduğunda operatöre gereksiz SMS atmadan kalan günü `15 -> 14 -> 13` diye kendi kendine düşürür.
- **Sıfır Emoji Politikası:** Ciddiyetten taviz verilmedi. Süs püs yok, saf bilgi var.

---

## 🪟 Ana Ekran Widget'ları (Akraba Meclisinde Hava Atmalık)

Telefonu açıp uygulamaya girmeye bile üşenenler için 3 farklı boyutta ana ekran widget'ı:

1. **Hızlı Buton (1x1):** Ekranda tek bir yuvarlak buton. Basıyorsun; uygulama hiç açılmadan arka planda sorguyu çekiyor, telefon cebindeyken bile sonucu kulağına okuyor.
2. **Kalan & Bakiye (2x2):** Bakiye ve kalan gün sayısını gösteren derli toplu widget.
3. **Full Paket Özeti (4x2):** Bakiye, kalan gün, dakika, internet ve SMS'i tek bakışta gösteren şöbiyet kıvamında widget.

---

## 🏆 Cihaz Uyumluluğu Sertifikası

> **Resmi Reeder P13 Blue Max 2021 Onaylı!**  
> Evet yanlış duymadınız. Uygulama, Android camiasının en çetin sınavı olan *Reeder P13 Blue Max 2021 (Android 11)* üzerinde bizzat test edilmiş ve sıfır donma ile çalışmıştır. Reeder'da stabil çalışan bir uygulama, NASA'nın Mars keşif araçlarında bile çalışır.

---

## 🛠️ Teknik Detaylar (Yazılımcısına Notlar)

- **%100 Kotlin & Native Android:** Sıfır hantal kütüphane, saf framework gücü.
- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** 34 (Android 14)
- **İzinler:** `SEND_SMS`, `RECEIVE_SMS`, `FOREGROUND_SERVICE`
- **TTS:** Google Speech Services entegrasyonu (kesilmelere karşı 3 sn audio buffer toleranslı).

---

## 🚀 Kurulum

1. Projeyi klonlayın:
   ```bash
   git clone https://github.com/kayafurkanipekci/Kalanim.git
   ```
2. Android Studio ile açın veya CLI üzerinden derleyin:
   ```bash
   ./gradlew assembleDebug
   ```
3. Dedemin telefonunu kabloyla bağlayıp basın:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 📜 Lisans

Bu proje, torunların dedelerine huzurlu bir emeklilik yaşatabilmesi amacıyla açık kaynak olarak sunulmuştur. Afiyetle kullanınız.
