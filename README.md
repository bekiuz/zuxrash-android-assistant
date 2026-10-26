# Zuxrash AI — Android yordamchi

Qora interfeys, neon binafsha/pushti aksentlar va o‘zbekcha buyruqlar bilan Android prototipi. Bu **alohida loyiha**; Nari AI kodini qayta nomlash emas.

## Hozirgi prototipdagi imkoniyatlar

- Chat ko‘rinishi va qurilmada saqlanadigan oxirgi 80 ta xabar.
- Matnli AI chat: Cloudflare Workers AI API’siga murojaat qiladi.
- Tugma orqali ovoz bilan kiritish (`uz-UZ`) va Android Text-to-Speech orqali javobni o‘qish.
- Sozlamadan qo‘lda yoqiladigan “Hey Zuxrash” fon rejimi: doimiy foreground bildirishnoma va “To‘xtatish” tugmasi bilan.
- Telefon vaqti va batareya darajasini aytish.
- Wi-Fi/telefon sozlamalari, kamera va brauzer qidiruvini ochish.
- Sozlamalar oynasida API endpointini o‘zgartirish.

## Ovoz va maxfiylik

“Hey Zuxrash” fon rejimi foydalanuvchi Sozlamalardan yoqqandagina mikrofonni faol tutadi. Android’ning doimiy foreground bildirishnomasida tinglash holati ko‘rsatiladi va “To‘xtatish” tugmasi bor. Bu hozircha Android SpeechRecognizer xizmatini qayta ishga tushirib turadigan prototip; oflayn wake-word modeli emas, shuning uchun qurilmadagi nutqni tanish xizmati va internet talab qilinishi mumkin. Fon tinglashni ishlatmasangiz, sozlamadan o‘chirib qo‘ying.

Chat xabarlari AI javobi uchun sozlangan Cloudflare Worker endpointiga yuboriladi. Mahalliy buyruqlar (masalan, vaqt/batareya) telefonda bajariladi. Mahalliy chat tarixi ushbu qurilmadagi ilova xotirasida saqlanadi. Maxfiy parol yoki API kalitlarini chatga yozmang.

## AI backend manzili

`app/build.gradle.kts` ichidagi `API_BASE_URL` hozir `https://zuxrash-ai.bekiuz282.workers.dev` qilib sozlangan. Agar Cloudflare panelidagi to‘liq Worker URL boshqacha bo‘lsa, shu qiymatni haqiqiy manzilga moslang. Ilova `/api/chat` yo‘lini o‘zi qo‘shadi — sozlamaga `/api/chat` yoki `/api/health` bilan tugaydigan URL kiritmang. Zuxrash Worker HTTP 404 qaytarsa, ilova vaqtincha `bekiuz-ai.bekiuz282.workers.dev` API’sini sinab ko‘radi.

Worker quyidagi API shartini qabul qilishi kerak:

- `GET /api/health` — ixtiyoriy health tekshiruvi.
- `POST /api/chat` — JSON `{ "mode": "general", "messages": [{"role":"user","content":"Salom"}] }`.
- JSON javob: `{ "ok": true, "reply": "..." }`.
- Public Worker’ning rate limitidan o‘tish uchun `X-Zuxrash-Client` header yuboriladi.

## Android Studio’da qurish

1. Repository’ni yuklab oling.
2. Android Studio’da shu papkani oching.
3. Gradle sync tugashini kuting.
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)** ni tanlang.
5. Debug APK odatda `app/build/outputs/apk/debug/app-debug.apk` ichida bo‘ladi.

## GitHub Actions orqali qurish

`.github/workflows/build-apk.yml` `app/` yoki Kotlin fayllari yangilanganda build qiladi. GitHub Actions ichidagi `Build Zuxrash AI APK` ishining muvaffaqiyatli tugashini tekshiring, so‘ng `zuxrash-ai-debug-apk` artifact’ini yuklab oling.

## Hali qilinmagan ishlar

Bu hozircha prototip: fon rejimidagi wake-word Android nutqni tanish xizmatiga bog‘liq va ishlab chiqarish darajasidagi oflayn hotword emas; qo‘ng‘iroq/SMS kabi keng ruxsatli buyruqlar, doimiy bulut xotirasi va chiqarishga tayyor signed release APK hali tayyor emas. Signed APK uchun alohida release signing sozlamalari kerak.
