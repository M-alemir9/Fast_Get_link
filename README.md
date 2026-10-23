# FastGet - مدير التحميل المتقدم للأندرويد 🚀
**FastGet: High-Performance Multi-Part Android Download Manager**

مشروع أندرويد متكامل ومبني بأحدث تقنيات أندرويد الرسمية:
- **Jetpack Compose + Material 3** مع دعم الثيم الداكن والفاتح ودعم RTL كامل للعربية.
- **OkHttp** للاتصالات الشبكية مع دعم Range Headers وتنزيل متعدد الأجزاء بالتوازي (4 - 16 خيط/Thread).
- **Room Database** لتخزين المهام وحفظ تقدم كل جزء لاستئناف حقيقي بعد انقطاع الاتصال أو إعادة تشغيل الهاتف.
- **Foreground Service** مع إشعار تفاعلي يسمح بالإيقاف المؤقت، الاستئناف، والإلغاء مباشرة من شريط الإشعارات.
- **Coroutines & Flow** للتعامل اللحظي مع التدفقات والسرعات.
- **Share Intent** لاستقبال الروابط مباشرة من متصفحات كروم وفايرفوكس.
- **GitHub Actions CI/CD** لبناء الـ APK تلقائياً.

---

## 🛠️ متطلبات البناء (Build Requirements)
- **Android Studio**: Koala, Iguana, Hedgehog أو أحدث.
- **JDK**: Java 17 (مطلوب لـ Gradle 8.6 و Android Gradle Plugin 8.4+).
- **minSdk**: 24 (Android 7.0 Nougat فما فوق).
- **targetSdk**: 34 (Android 14).

---

## 🚀 خطوات البناء والتشغيل (How to Build & Run)

### 1. عبر Android Studio:
1. قم بفك ضغط ملف المشروع وافتحه في Android Studio عبر **File -> Open**.
2. انتظر مزامنة Gradle (Gradle Sync).
3. اختر جهاز تشغيل أو محاكي (Emulator) بنظام أندرويد 7.0 أو أحدث.
4. اضغط على زر **Run ▶** أو `Shift + F10`.

### 2. عبر سطر الأوامر (Command Line):
```bash
# فحص الصلاحيات وتنفيذ البناء
chmod +x gradlew
./gradlew assembleDebug
```
ستجد ملف الـ APK المنشأ في المسار التالي:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📦 بناء APK تلقائياً عبر GitHub Actions:
المشروع مزود بملف سير عمل جاهز (`.github/workflows/build.yml`). بمجرد رفع الكود إلى مستودع GitHub:
1. سيعمل الـ Workflow تلقائياً على خوادم GitHub Ubuntu.
2. يتم تجهيز بيئة Java 17 و Gradle 8.6.
3. يتم تجميع التطبيق وإنتاج `app-debug.apk`.
4. يمكنك تحميل الـ APK فوراً من تبويب **Actions -> Artifacts**.
