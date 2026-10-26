# Zuxrash AI — Android yordamchi

Qora interfeys, neon binafsha/pushti aksentlar va o‘zbekcha buyruqlar bilan Android prototipi. Bu **alohida loyiha**; Nari AI kodini qayta nomlash emas.

## Hozirgi prototipdagi imkoniyatlar

- Chat ko‘rinishi va qurilmada saqlanadigan oxirgi 80 ta xabar.
- Matnli AI chat: Cloudflare Workers AI API’siga murojaat qiladi.
- Tugma orqali ovoz bilan kiritish (`uz-UZ`) va Android Text-to-Speech orqali javobni o‘qish.
- Telefon vaqti va batareya darajasini aytish.
- Wi-Fi/telefon sozlamalari, kamera va brauzer qidiruvini ochish.
- Sozlamalar oynasida API endpointini o‘zgartirish.

## Ovoz va maxfiylik

Ilova mikrofonni fon rejimida doimiy tinglamaydi. Mikrofon faqat foydalanuvchi mikrofon tugmasini bosganda ishlatiladi. “Hey Zuxrash” hozircha faqat ilovaning ochiq holatida yozilgan matnda aniqlanadi; bu doimiy wake-word xizmati emas.

Chat xabarlari AI javobi uchun sozlangan Cloudflare Worker endpointiga yuboriladi. Mahalliy buyruqlar (masalan, vaqt/batareya) telefonda bajariladi. Mahalliy chat tarixi ushbu qurilmadagi ilova xotirasida saqlanadi. Maxfiy parol yoki API kalitlarini chatga yozmang.

## AI backend manzili

`app/build.gradle.kts` ichidagi `API_BASE_URL` hozir `https://zuxrash-ai.bekiuz282.workers.dev` qilib sozlangan. Agar sening Cloudflare Worker manziling boshqacha bo‘lsa, shu qiymatni almashtir.

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

Bu hozircha birinchi prototip: mustaqil fon rejimidagi wake-word, qo‘ng‘iroq/SMS kabi keng ruxsatli buyruqlar, doimiy bulut xotirasi va chiqarishga tayyor signed release APK hali tayyor emas. Signed APK uchun alohida release signing sozlamalari kerak.
