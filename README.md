# SportivTV IPTV - تطبيق IPTV رياضي عربي

تطبيق IPTV رياضي عربي فاخر لـ Android TV والهواتف.

## المعمارية

### المحرك
- **ExoPlayer/Media3** - محرك تشغيل الفيديو الأساسي
- **OkHttp** - طبقة الشبكة
- لا يوجد مشغل فيديو ثانٍ. الصوت الخارجي يستخدم AudioTrack مستقل.

### المكونات الرئيسية

| المكون | الوصف |
|--------|-------|
| `PlayerFactory` | إنشاء ExoPlayer مهيأ لبث IPTV المباشر |
| `PlayerActivity` | المشغل المتقدم مع تبديل القنوات والتحكم الكامل |
| `ExternalAudioManager` | استخراج الصوت المستقل (تجريبي) |
| `M3UParser` | تحليل قوائم تشغيل M3U/M3U8 |
| `EpgParser` | تحليل دليل البرامج XMLTV |
| `ChannelRepository` | مصدر بيانات القنوات مع تخزين مؤقت |
| `ChannelSearch` | بحث بالعربية والإنجليزية مع توحيد الحروف |
| `FavoritesManager` | إدارة القنوات المفضلة |
| `SubscriptionManager` | إدارة اشتراكات IPTV |
| `SecureCredentialStorage` | تخزين مشفر لكلمات المرور (AES-256-GCM) |
| `BackendManager` | إدارة الأجهزة والإعدادات الآمنة |

## البناء

### المتطلبات
- Android Studio Hedgehog (2023.1) أو أحدث
- JDK 17
- Android SDK 34 (compileSdk)
- Gradle 8.5

### بناء APK

```bash
# بناء نسخة ARM64 (للإصدار)
./gradlew assembleRelease

# بناء نسخة تصحيح
./gradlew assembleDebug

# بناء APK لكل معماريات
./gradlew assembleDebug -PsplitAbi=true
```

### APK الناتج

سيتم إنشاء الملفات في:
```
app/build/outputs/apk/release/
├── SportivTV-v1.0.0-arm64-v8a.apk     (الإصدار الأساسي)
├── SportivTV-v1.0.0-armeabi-v7a.apk   (للأجهزة القديمة)
├── SportivTV-v1.0.0-x86_64.apk        (للمحاكيات)
└── SportivTV-v1.0.0-universal.apk     (شامل)
```

### الاختبارات

```bash
# اختبارات الوحدة
./gradlew test

# اختبارات الأجهزة
./gradlew connectedAndroidTest
```

## التثبيت

### على Android TV
```bash
adb install -r SportivTV-v1.0.0-arm64-v8a.apk
```

### على الهاتف
```bash
adb install -r SportivTV-v1.0.0-arm64-v8a.apk
```

## المتطلبات

- Android 5.0 (API 21) أو أحدث
- اتصال بالإنترنت
- اشتراك IPTV صالح

## القيود المعروفة

1. **الصوت الخارجي (تجريبي)**: يعمل مع بثوث TS/HLS القياسية فقط. لا يدعم البثوث المشفرة.
2. **EPG**: يعتمد على توفير رابط XMLTV من مزود الاشتراك.
3. **مزامنة الصوت**: قد تختلف دقتها حسب خصائص البث. يمكن ضبط الإزاحة يدويًا.

## الأذونات

- `INTERNET` - لبث IPTV
- `ACCESS_NETWORK_STATE` - للتحقق من الاتصال
- `WAKE_LOCK` - لإبقاء الشاشة مضاءة أثناء المشاهدة
- `READ_EXTERNAL_STORAGE` - لاستيراد قوائم التشغيل (إذا لزم)

## الأمان

- كلمات مرور الاشتراكات مخزنة باستخدام AES-256-GCM
- لا يتم تسجيل أو كشف بيانات الاشتراك الحساسة
- اتصالات الشبكة تستخدم HTTPS عند توفرها
