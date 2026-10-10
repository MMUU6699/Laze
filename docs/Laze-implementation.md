# Laze: التقرير النهائي الشامل للتحويل والتنفيذ المعماري

تم تحويل وتطوير مشروع Vela Maps إلى **Laze** — تطبيق ملاحة مجتمعي وسحابي ذكي شبيه بـ Waze، يدعم اللغة العربية بالكامل واتجاه RTL، ويحتوي على تسجيل دخول حقيقي إلزامي عبر Google مدعوم بـ Supabase، ونظام بلاغات طرق ومرور حي، وتفاعل فوري مع مسارات القيادة والتوجيه الصوتي وتغيير المسار التلقائي، ومعالجة جذرية لكافة المشاكل المعمارية والمنطقية والتقطيع والانهيارات.

---

## 1. الهوية والعلامة التجارية (Laze)

- **اسم التطبيق**: تم تغيير اسم التطبيق في المشغّل (`launcher`) وكافة شاشات الترحيب والواجهات إلى **Laze** (لايز).
- **معرّف الحزمة (`applicationId`)**: تم الإبقاء عمدًا وبشكل دقيق على `app.vela` لضمان التوافق مع نظام التثبيت ومكتبات الخرائط ومنع أي تعارض أثناء البناء والترقية.
- **لغة التصميم**: تم الحفاظ على لغة تصميم Material 3 الأصلية ونظام الألوان الداكن/الفاتح والخطوط والأيقونات، مع إضافة عناصر تفاعلية مخصصة للسائقين بأعلى تباين وسهولة وصول أثناء القيادة.

---

## 2. حل مشكلة التقطيع الشديد في الخريطة (Map Jitter & Jank Elimination)

### الأسباب الجذرية التي تم تشخيصها بدقة:
1. **تراكب المباني الثقيل (`BuildingOverlay`) و MapSnapshotter**:
   - كان `BuildingOverlay.on` مفعلًا افتراضيًا (`true`)، مما يؤدي عند كل تحريك للخريطة أو توقف مؤقت للكاميرا (`onCameraIdle` / `idleWork`) إلى إنشاء كائن `MapSnapshotter` وعمل استعلامات HTTP Range لملفات `.pmtiles` خارجية وإعادة توليد طبقات المتجهات داخل MapLibre على الخيط الرئيسي.
2. **استدعاء متكرر لجلب البلاغات أثناء تحريك الكاميرا**:
   - كان تدفق `_state.map { it.center }.distinctUntilChanged()` يُطلق استدعاء شبكي `fetchNearbyReports` عند كل فريم من فريمات التحريك (60 إطارًا بالثانية). ومع كل استجابة، يتم تحديث `_state` مما يعيد تركيب (Recomposition) شاشات Compose وعناصر الخريطة باستمرار.
3. **فحص المخاطر وممر الأمان على الخيط الرئيسي (`Main Dispatcher`)**:
   - كان بلوك `_state.collect` يفحص هندسة المسار بالكامل (`route.polyline.map { it.lat to it.lng }`) ويجري حسابات رياضية على آلاف النقاط مع كل تحديث للحالة (موقع GPS، سرعة، زاوية دوران)، مما يتسبب في ضغط هائل على الذاكرة وGarbage Collection وهبوط حاد في معدل الإطارات.
4. **تحديث الطبقات غير الضرورية أثناء الملاحة النشطة في `onViewport`**:
   - أثناء القيادة والملاحة، كانت دالة `onViewport` تستمر في تحديث محطات النقل العام وخطوط السكك الحديدية وتواريخ صور الأقمار الصناعية ونقاط الاهتمام المحيطة على كل حركة للمركبة.

### الحلول الجذرية المنفذة:
1. **تعطيل `BuildingOverlay` افتراضيًا في [BuildingOverlay.kt](file:///e:/laze/app/src/main/java/app/vela/ui/BuildingOverlay.kt)**:
   - تم ضبط الإعداد الافتراضي على `false`، حيث أن طبقة المتجهات القياسية لـ OpenFreeMap Liberty تحتوي بالفعل على المباني ثلاثية الأبعاد المدمجة في خريطة الشارع المفتوح، مما منع استدعاءات `MapSnapshotter` وطلبات النطاق الثقيلة تمامًا أثناء التحريك.
2. **كبح ومفاضلة طلبات البلاغات (Debouncing & Distance Threshold)**:
   - في [MapViewModel.kt](file:///e:/laze/app/src/main/java/app/vela/ui/map/MapViewModel.kt): تم إضافة كبح زمني `debounce(2500L)` لطلبات تحديث البلاغات، مع اشتراط تحرك الكاميرا لمسافة تزيد عن **2000 متر** من آخر نقطة تم جلب بياناتها، لمنع سيل الطلبات الشبكية وإعادة التركيب أثناء التحريك والقيادة.
3. **عزل مراقبة المخاطر في حلقة خلفية (`Dispatchers.Default`)**:
   - نقل فحص المخاطر من `_state.collect` إلى حلقة دورية تعمل كل 3 ثوانٍ على خيوط المعالجة الخلفية `Dispatchers.Default`، مع تخزين مؤقت لإحداثيات المسار (`cachedPoints`) لعدم إعادة تخصيص آلاف الكائنات في الذاكرة كل ثانية.
4. **تخفيف أحمال `onViewport` أثناء الملاحة**:
   - تجاوز جلب طبقات النقل العام والصور الفضائية وأرقام المنازل والـ Ambient POIs عندما تكون الملاحة نشطة (`isNav == true`) للحفاظ على سلاسة 60 إطارًا بالثانية لحركة سيارة الملاحة.

---

## 3. حل مشكلة الانهيار عند حساب مسار جديد (Route Calculation Crash Fix)

### الأسباب الجذرية التي تم تشخيصها:
1. **استدعاء `primary.first()` دون التحقق من تفريغ القائمة**:
   - في [GoogleMapsDataSource.kt](file:///e:/laze/core/src/main/java/app/vela/core/data/google/GoogleMapsDataSource.kt): عند حساب بدائل المسارات، كان الكود يستدعي `primary.first()` مباشرة داخل دمج البدائل. فإذا كانت استجابة المسارات الأولية فارغة أو لم تتطابق مع شروط المرور، ينهار التطبيق باستثناء `NoSuchElementException`.
2. **فك التغليف القسري `trafficRoute!!` و `snapped!!`**:
   - استخدام المعامل `!!` على مسارات قد تكون غير متوفرة أو ملغاة أثناء التحويل.
3. **الوصول المباشر للفهرس صفر `cur[0]` و `routes.first()`**:
   - في [MapViewModel.kt](file:///e:/laze/app/src/main/java/app/vela/ui/map/MapViewModel.kt): داخل دالتي `refreshFlockOnRoute` و `offerSavedRoutes` و `roadFeaturesCoverRoute`، كان يتم قراءة `cur[0]` أو `poly.first()` دون فحص تفريغ القائمة، فإذا ألغى المستخدم طلب المسار أو رجع للخلف أثناء جلب المسار يحدث انهيار `IndexOutOfBoundsException`.

### الحلول الجذرية المنفذة:
1. **معالجة آمنة لمرشحي المسارات**:
   - تعديل دمج البدائل في `GoogleMapsDataSource.kt` لاستخدام `primary.firstOrNull()` وبناء قائمة المرشحين بشكل آمن بالكامل دون أي استدعاء قد يثير `NoSuchElementException`.
2. **إزالة كافة معاملات الفك القسري (`!!`)**:
   - استبدالها بفحوصات أمان صريحة `snapKeptStops && snapped != null` و `snapWorthIt && trafficRoute != null`.
3. **حماية كافة الدوال المستهلكة للمسارات**:
   - التحقق من `cur.isEmpty()` و `cur.firstOrNull()` في `refreshFlockOnRoute`.
   - استخدام `poly.firstOrNull() ?: return RoadCover.NONE` في `roadFeaturesCoverRoute`.
   - حماية دالة المسارات المحفوظة `offerSavedRoutes` ضد القوائم الفارغة.
4. **حالة تحميل المسارات وانتقال الآلة الحتمية**:
   - ضبط `routesLoading = true` مسبقًا وتصفيرها بأمان في كل المسارات الاستثنائية والناجحة، مع عرض رسالة خطأ واضحة وزر «إعادة المحاولة» بدل تجميد الواجهة.

---

## 4. البنية السحابية المعتمدة (Cloud Stack & Zero Forced Downloads)

1. **عرض الخريطة (MapLibre + OpenFreeMap)**:
   - تحميل بلاطات المتجهات السحابية الحية عبر الإنترنت من الرابط الرسمي:
     `https://tiles.openfreemap.org/styles/liberty`
   - عرض المعالم والشوارع والمباني ثلاثية الأبعاد بسلاسة تامة ودون الحاجة لتنزيل أي حزم خرائط مسبقة.
2. **محرك التوجيه وحساب المسارات (OSRM & Valhalla)**:
   - استخدام خوادم OSRM العالمية الرسمية السريعة لحساب المسارات الحقيقية بالمسافة والوقت وهندسة الطرق والمناورات التفصيلية.
   - تم التحقق من حساب المسار الحقيقي الطويل عبر العراق (من **أربيل** `36.19, 44.01` إلى **بغداد** `33.31, 44.36`) بمسافة تقارب **360 كم** ومدة **4.5 ساعات** ومناورات واقعية.
3. **البحث والأماكن (Photon OSM Geocoder)**:
   - دمج محرك البحث الحي `https://photon.komoot.io/api/` الذي يوفر بحثًا لحظيًا مكبوحًا عن المدن والمعالم والشوارع بدعم كامل للغة العربية والإنجليزية وإحداثيات دقيقة.
4. **التوجيه الصوتي عبر النظام (Android System TextToSpeech)**:
   - استخدام محرك تحويل النص إلى كلام القياسي في نظام أندرويد على الجهاز (دعم العربية والإنجليزية).
   - تعطيل أي مطالبات أو نوافذ تجبر المستخدم على تنزيل نماذج Piper أو Kokoro العصبية الضخمة (ضبط `showVoicePrompt.value = false` دائمًا في شاشات الترحيب والإعداد).
5. **نظام التقارير السحابية (Supabase + PostGIS)**:
   - ربط التطبيق بالمشروع السحابي الحي عبر الجداول الجغرافية `road_reports` والـ RPCs المكانية الحقيقية لحساب البلاغات القريبة وممر التحذير.

---

## 5. بوابة تسجيل الدخول الإلزامية وإلغاء الاستخدام دون حساب (Mandatory Authentication)

1. **بوابة المصادقة المركزية في جذر التطبيق ([VelaRoot.kt](file:///e:/laze/app/src/main/java/app/vela/ui/VelaRoot.kt))**:
   - لا يمكن للمستخدم الوصول إلى شاشة الترحيب أو الخريطة أو أي جزء من التطبيق دون وجود جلسة تسجيل دخول صالحة.
   - في حالة `AuthState.Loading`: تظهر دائرة تحميل مركزية فقط.
   - في حالة `AuthState.Unauthenticated` أو `AuthState.Error`: تظهر شاشة تسجيل الدخول الإلزامية [LoginScreen.kt](file:///e:/laze/app/src/main/java/app/vela/ui/profile/LoginScreen.kt).
   - في حالة `AuthState.Authenticated`: ينتقل التطبيق إلى الواجهة الرئيسية فورًا.
2. **إلغاء نمط الضيف نهائيًا**:
   - حذف `AuthState.Guest` و `continueAsGuest()` من [AuthRepository.kt](file:///e:/laze/core/src/main/java/app/vela/core/backend/AuthRepository.kt) و [MapViewModel.kt](file:///e:/laze/app/src/main/java/app/vela/ui/map/MapViewModel.kt).
   - حذف زر "المتابعة كضيف" من كافة الشاشات.
3. **Google OAuth الحقيقي واستعادة الجلسة**:
   - معالجة الرابط العائد عبر الروابط العميقة (Deep Links) في [AndroidManifest.xml](file:///e:/laze/app/src/main/AndroidManifest.xml) لكلا المخططين (`laze://auth/callback` و `app.vela://auth/callback`).
   - استخراج الـ Tokens وحفظ الجلسة محليًا لتسجيل الدخول التلقائي في المرات اللاحقة.

---

## 6. التعريب الشامل وفصل RTL عن المناورات الفيزيائية

1. **التعريب الكامل بدون اختصارات خاطئة**:
   - القضاء على خطأ تحويل النصوص إلى كلمة «لا» مجردة والتعريب الشامل لكافة المفاتيح الـ **1401**.
   - اجتياز أداة التحقق الرسمية للمشروع بنجاح:
     `python tools/check-translations.py` -> **Translation check OK: 1401 translatable keys**.
2. **فصل اتجاه RTL عن أيقونات الانعطاف**:
   - تثبيت اتجاه أيقونات المناورات والدوارات ومخطط الحارات (Lane Diagrams) على LTR الصريح في [NavOverlays.kt](file:///e:/laze/app/src/main/java/app/vela/ui/nav/NavOverlays.kt) و [StepsSheet.kt](file:///e:/laze/app/src/main/java/app/vela/ui/nav/StepsSheet.kt) لضمان عدم عكس أسهم الانعطاف يمينًا ويسارًا أثناء تفعيل اللغة العربية.

---

## 7. ظهور زر إضافة البلاغ أثناء الملاحة النشطة فقط (Report FAB Gating)

- تقييد ظهور الزر في [MapFloaters.kt](file:///e:/laze/app/src/main/java/app/vela/ui/map/MapFloaters.kt) بالشرط:
  ```kotlin
  if (state.navigating && !state.searching && state.streetView == null) {
      ReportFab(...)
  }
  ```
- يختفي الزر فورًا عند إلغاء الملاحة، أو انتهاء الرحلة، أو أثناء البحث، أو أثناء معاينة المسارات.

---

## 8. التحقق والاختبارات وبناء الـ APK النهائي

### 1. اختبارات الملاحة والمسارات الحية (`LazeOnlineNavigationTest`):
تم إنشاء وتنفيذ فحص شامل ومخصص في [LazeOnlineNavigationTest.kt](file:///e:/laze/core/src/test/java/app/vela/core/LazeOnlineNavigationTest.kt):
- **اختبار مسار أربيل إلى بغداد**:
  - الاتصال الحي بخادم OSRM والتأكد من إرجاع مسار قيادة حقيقي هندسي كامل (> 300 كم، > 10,800 ثانية، ومناورات وخط مسار متصل).
- **اختبار بحث Photon الحي لبغداد**:
  - البحث عن مدينة بغداد واسترجاع الإحداثيات والاسم باللغتين العربية والإنجليزية.
- **اختبار أمان دمج المسارات المرشحة**:
  - التأكد من عدم حدوث أي استثناء عند تفريغ القوائم أو الحالات الحدية.

### 2. نتائج اختبارات Gradle:
- **اختبارات `:core:testDebugUnitTest`**:
  ```powershell
  ./gradlew.bat :core:testDebugUnitTest --tests app.vela.core.LazeOnlineNavigationTest
  ```
  **النتيجة: BUILD SUCCESSFUL (Exit code 0)**.
- **اختبارات التطبيق الكاملة `:app:testDebugUnitTest`**:
  ```powershell
  ./gradlew.bat :app:testDebugUnitTest
  ```
  **النتيجة: BUILD SUCCESSFUL (Exit code 0)**.

### 3. بناء ملف الـ APK النهائي الجاهز للاختبار الفعلي:
- أمر التجميع:
  ```powershell
  ./gradlew.bat :app:assembleDebug
  ```
  **النتيجة: BUILD SUCCESSFUL in 48s (Exit code 0)**.
- **ملف الحزمة الناتج**:
  - **المسار**: `app/build/outputs/apk/debug/app-debug.apk`
  - **الحجم**: `150,174,246 بايت` (~150 ميجابايت).
  - **تاريخ ووقت الإنشاء**: `10/10/2026 3:13:49 PM`.
  - **الحالة**: مبني بنجاح وخالٍ من الأخطاء وجاهز للتثبيت والتجربة الميدانية المباشرة.
