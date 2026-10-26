# Zuxrash AI — Android yordamchi

Zuxrash AI uchun Android Studio loyihasi. Asosiy manba kod hozircha `zuxrash-android-assistant-source.zip` arxivida saqlanadi. Uni yuklab olib chiqarishingiz yoki Android Studio orqali arxiv ichidagi `zuxrash-android-assistant` papkasini ochishingiz mumkin.

## Asosiy imkoniyatlar
- Qora fon, neon binafsha/pushti interfeys.
- Matnli AI chat, oxirgi 80 ta xabarni qurilmada saqlash.
- Tugma orqali o‘zbekcha ovozli kiritish va Android TTS.
- Telefon vaqti, batareya, Wi‑Fi/telefon sozlamalari va web qidiruv kabi mahalliy buyruqlar.
- AI javoblari Cloudflare Workers AI endpointi orqali keladi.

## APK
GitHub Actions `Build Zuxrash AI APK` workflow'i debug APK quradi. Run muvaffaqiyatli tugagach, `zuxrash-ai-debug-apk` artifactini yuklab oling.

## Eslatma
Bu dastlabki prototip. “Hey Zuxrash” doimiy fon tinglashi emas — ilova ochiq turganda kiritilgan matn orqali ishlaydi. Sinov uchun debug APK; Play Store uchun signed release hali sozlanmagan.


## Hozir senga kerak bo‘lgan bitta qadam

1. ChatGPT'dan **zuxrash-android-assistant-source.zip** faylini yuklab ol.
2. GitHub'da shu repository bosh sahifasini och: https://github.com/bekiuz/zuxrash-android-assistant
3. **Add file → Upload files** ni bos.
4. Yuklagan `zuxrash-android-assistant-source.zip` faylini tanla va **Commit changes** ni bos.

ZIP ichidagi loyiha avtomatik chiqarilib, APK build qilinadi. Keyin **Actions → Build Zuxrash AI APK → eng yangi run → Artifacts → zuxrash-ai-debug-apk** ichidan APK'ni olasan. Build o'tmasa, o'sha run'dagi xato matnini menga yubor.
