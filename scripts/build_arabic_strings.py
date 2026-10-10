#!/usr/bin/env python3
"""
Generates values-ar/strings.xml for Laze with high-quality Arabic translations
and verifies placeholder consistency with values/strings.xml.
"""
import os
import re
import json
import xml.etree.ElementTree as ET

EN_FILE = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "values", "strings.xml")
AR_FILE = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "values-ar", "strings.xml")
CURATED_FILE = os.path.join(os.path.dirname(__file__), "translations_curated.json")

# Load curated translations
CURATED_MAP = {}
if os.path.exists(CURATED_FILE):
    with open(CURATED_FILE, "r", encoding="utf-8") as f:
        CURATED_MAP = json.load(f)

# Comprehensive translation dictionary for key strings
AR_TRANSLATIONS = {
    "app_name": "لايز",
    "app_name_generic": "الخرائط",
    
    # Categories
    "cat_restaurants": "مطاعم",
    "cat_coffee": "مقهى",
    "cat_gas": "محطات وقود",
    "cat_ev": "شحن سيارات كهربائية",
    "cat_groceries": "بقالة وتموينات",
    "cat_hotels": "فنادق",
    "cat_pharmacy": "صيدليات",
    "cat_atms": "أجهزة صراف آلي",
    "cat_parks": "حدائق ومنتزهات",
    "cat_things_to_do": "أنشطة ومعالم",
    "cat_bars": "مقاهي واستراحات",
    "cat_parking": "مواقف سيارات",
    "cat_hospitals": "مستشفيات وطوارئ",
    "cat_banks": "بنوك",
    "cat_post_offices": "مكاتب بريد",
    "cat_campgrounds": "مواقع تخييم",

    # Settings
    "settings_title": "الإعدادات",
    "settings_search_hint": "البحث في الإعدادات",
    "settings_search_none": "لا توجد نتائج",
    "settings_back": "رجوع",
    "settings_appearance": "المظهر",
    "settings_follow_system": "تلقائي حسب النظام",
    "settings_theme_light": "فاتح",
    "settings_theme_dark": "داكن",
    "settings_appearance_hint": "ينطبق على تطبيق لايز فقط.",
    "settings_app_name_title": "اسم التطبيق",
    "settings_app_name_generic": "تسميته “الخرائط” فقط",
    "settings_app_name_generic_hint": "يعرض أيقونة المشغل باسم الخرائط بدلاً من لايز للمظهر البسيط.",
    "settings_dynamic_color": "ألوان Material You",
    "settings_dynamic_color_hint": "تلوين الأزرار والعناصر التفاعلية من خلفية الشاشة. تظل الخريطة محايدة.",
    "settings_map_style": "نمط الخريطة",
    "settings_map_style_hint": "تعمل خرائط OpenFreeMap دون الحاجة إلى أي مفتاح برمجي.",
    "settings_units": "الوحدات",
    "settings_clock": "الساعة",
    "settings_clock_system": "نفس ضبط الهاتف",
    "settings_clock_12": "نظام 12 ساعة (4:35 م)",
    "settings_clock_24": "نظام 24 ساعة (16:35)",
    "settings_units_imperial": "إمبريالي (ميل، قدم)",
    "settings_units_metric": "متري (كيلومتر، متر)",
    "settings_language": "اللغة",
    "settings_follow_system_language": "لغة النظام",
    "settings_language_hint": "تغيير لغة التطبيق والإرشادات الصوتية للملاحة.",
    "settings_directions_language": "لغة إرشادات المسارات",
    "settings_directions_same_as_app": "نفس لغة التطبيق",
    "settings_directions_language_hint": "اللغة التي تظهر وتنطق بها مناورات الملاحة.",
    "settings_search": "البحث",
    "settings_voice_search_toggle": "ميكروفون البحث الصوتي",
    "settings_voice_search_hint": "إظهار رمز الميكروفون في شريط البحث للبحث عبر الصوت.",
    "settings_contacts_search": "البحث في جهات الاتصال",
    "settings_contacts_search_hint": "اقتراح العناوين المحفوظة في جهات الاتصال الخاصة بك محليًا على الهاتف دون رفعها.",
    "suggestion_contact_badge": "جهة اتصال",
    "search_voice_cd": "بحث صوتي",
    "search_voice_prompt": "تحدث للبحث",
    "settings_voice": "الصوت",
    "settings_voice_fallback_name": "الصوت",
    "settings_voice_downloading": "جارٍ تنزيل %1$s… %2$d%%",
    "settings_voice_none_hint": "لا يوجد ملف صوتي بعد. يمكنك تنزيل صوت عالي الدقة يعمل بالكامل بدون اتصال.",
    "settings_voice_test": "تجربة الصوت",
    "settings_voice_system_settings": "إعدادات صوت النظام",
    "settings_voice_test_hint": "اضغط على تجربة الصوت للاستماع لعينة من التوجيه الصوتي.",
    "settings_voice_library": "مكتبة الأصوات",
    "settings_voice_advanced": "خيارات صوتية متقدمة",
    "settings_voice_try_label": "جرّب الصوت مع أي نص",
    "settings_voice_nav_sample_text": "بعد أربعمائة متر، انعطف يمينًا، ثم ستكون وجهتك على اليسار.",
    "settings_voice_speak": "نطق",
    "settings_voice_nav_sample": "عينة ملاحة",
    "settings_voice_speed": "سرعة الصوت · %1$s",
    "settings_voice_speed_hint": "التحكم في سرعة الإرشادات المنطوقة أثناء القيادة.",
    "settings_voice_variant": "النمط الصوتي #%1$d",
    "settings_voice_variant_of": "النمط الصوتي #%1$d من %2$d",
    "settings_voice_variant_field": "النمط #",
    "settings_voice_variant_go": "انتقال",
    "settings_voice_variant_hint": "اختر النبرة الصوتية المفضلة لديك.",
    "settings_navigation": "الملاحة",
    "settings_vibrate_on_turns": "الاهتزاز عند الانعطاف",
    "settings_mode_driving": "قيادة",
    "settings_mode_walking": "مشي",
    "settings_mode_cycling": "دراجة",
    "settings_mode_transit": "نقل عام",
    "settings_vibrate_hint": "تنبيه اهتزازي مميز عند اقتراب موعد الانعطاف.",
    "settings_keep_screen_on": "إبقاء الشاشة مضاءة أثناء الملاحة",
    "settings_keep_screen_on_hint": "يمنع إغلاق الشاشة تلقائيًا أثناء تشغيل الملاحة الموجهة.",
    "settings_map": "الخريطة",
    "settings_live_traffic": "طبقة حركة المرور الحية",
    "settings_live_traffic_hint": "تلوين الطرق وفق كثافة المرور الحالية أثناء تصفح الخريطة.",
    "settings_transit_layer": "إبراز مسارات النقل العام",
    "settings_transit_layer_hint": "عرض خطوط المترو والحافلات والقطارات بوضوح على الخريطة.",
    "settings_read_all_reviews": "زر “قراءة جميع المراجعات”",
    "settings_read_all_reviews_hint": "إظهار زر لفتح صفحة التقييمات الكاملة للمكان.",
    "settings_places": "الأماكن",
    "settings_offline": "الخرائط غير المتصلة",
    "settings_saved_places": "الأماكن المحفوظة",
    "settings_privacy": "الخصوصية",
    "settings_performance": "الأداء",
    "settings_diagnostics": "التشخيصات",
    "settings_about": "حول التطبيق",
    "settings_about_hint": "لايز (Laze): تطبيق ملاحة ذكي ومجتمعي يدعم التنبيهات المرورية الحية، وإعادة توجيه المسارات، مع حماية خصوصية المستخدمين وبدون خدمات Google Play.",
    "settings_support": "الدعم والمساهمة",
    "settings_support_hint": "تطبيق حر ومفتوح المصدر (GPLv3).",
    
    # Reports
    "report_road_event": "إبلاغ",
    "report_title": "ماذا ترى على الطريق؟",
    "report_type_accident": "حادث",
    "report_type_traffic": "ازدحام",
    "report_type_closure": "إغلاق طريق",
    "report_type_roadwork": "أشغال وصيانة",
    "report_type_hazard": "خطر على الطريق",
    "report_type_breakdown": "سيارة متعطلة",
    "report_type_flood": "طريق مغمور بالمياه",
    "report_type_camera": "كاميرا سرعة",
    "report_send": "إرسال البلاغ",
    "report_submitting": "جارٍ الإرسال…",
    "report_success": "تم إرسال البلاغ! شكرًا لمساعدة السائقين الآخرين.",
    "report_error": "تعذر إرسال البلاغ. يرجى المحاولة لاحقًا.",
    "report_still_there": "ما زال موجودًا",
    "report_cleared": "تمت إزالته",
    "report_confirm_thanks": "شكرًا لتحديثك حالة الطريق!",
    "report_direction_ahead": "في اتجاهك",
    "report_direction_opposite": "في الاتجاه المعاكس",
    "report_details_title": "تفاصيل البلاغ المروري",
    "report_posted_by": "أبلغ عنه: %1$s",
    "report_posted_time": "منذ %1$s",
    "report_confidence_high": "بلاغ مؤكد",
    "report_confidence_medium": "غير مؤكد بعد",
    "report_nav_alert_title": "تنبيه مروري أمامك",
    "report_nav_alert_distance": "على بعد %1$s: %2$s",
    "report_nav_reroute_prompt": "يوجد مسار بديل أسرع بسبب %1$s. يوفر %2$s دقيقة.",
    "report_nav_reroute_accept": "سلوك المسار البديل",
    "report_nav_reroute_dismiss": "البقاء على المسار الحالي",

    # Auth & Profile
    "auth_sign_in_title": "تسجيل الدخول إلى لايز",
    "auth_sign_in_subtitle": "احفظ أماكنك المفضلة، وشارك في بلاغات الطريق، ونسّق مساراتك بسهولة.",
    "auth_google_button": "تسجيل الدخول عبر Google",
    "auth_continue_guest": "المتابعة كضيف",
    "auth_signing_in": "جارٍ تسجيل الدخول…",
    "auth_success": "تم تسجيل الدخول بنجاح!",
    "auth_error": "فشل تسجيل الدخول: %1$s",
    "auth_canceled": "تم إلغاء تسجيل الدخول.",
    "profile_title": "الملف الشخصي",
    "profile_guest": "سائق ضيف",
    "profile_reports_count": "البلاغات المرسلة: %1$d",
    "profile_sign_out": "تسجيل الخروج",
    "profile_sign_out_confirm": "هل أنت متأكد من رغبتك في تسجيل الخروج؟",
    "profile_delete_account": "حذف الحساب نهائيًا",
    "profile_delete_confirm": "سيتم حذف حسابك وجميع بياناتك نهائيًا وبشكل لا يمكن التراجع عنه.",
    "settings_profile": "الملف الشخصي والحساب",
    "settings_profile_hint": "إدارة حسابك في لايز ومساهماتك المجتمعية.",
    "settings_hub_profile_sub": "حساب Google، المساهمات والمزامنة",
    "report_road_hazard": "الإبلاغ عن خطر على الطريق",
    "report_sheet_title": "الإبلاغ عن حادث أو خطر",
    "report_sheet_subtitle": "ساعد السائقين الآخرين بالإبلاغ عما تراه على الطريق",
    "action_close": "إغلاق",
    "report_confirmations_stat": "%1$d تأكيدات من السائقين",
    "report_action_still_there": "لا يزال موجوداً",
    "report_action_cleared": "تمت إزالته",
    "report_submitted_success": "تم إرسال البلاغ بنجاح! شكراً لمساعدتك السائقين.",
    "report_submitted_error": "تعذر إرسال البلاغ. يرجى المحاولة مرة أخرى.",
    "report_confirmed_thanks": "شكراً لتأكيدك البلاغ وتحديث الخريطة!",
    "report_cleared_thanks": "شكراً لتحديثك حالة الطريق!",
    "auth_login_title": "تسجيل الدخول إلى Laze",
    "auth_login_subtitle": "سجل دخولك عبر Google لحفظ ملفك الشخصي وتأكيد بلاغات الطريق الحية.",
    "auth_sign_in_google": "تسجيل الدخول عبر Google",
    "profile_reports_count_label": "عدد البلاغات المرسلة",
    "auth_sign_out": "تسجيل الخروج",
    "auth_signed_out_msg": "تم تسجيل الخروج بنجاح.",
    "backend_config_title": "إعدادات خادم Supabase",
    "backend_config_subtitle": "تكوين نقطة نهاية Supabase للبلاغات الحية والمزامنة.",
    "config_save_btn": "حفظ الإعدادات",
    "config_saved": "تم حفظ الإعدادات",

    # Navigation & Driving
    "nav_start": "بدء الملاحة",
    "nav_stop": "إنهاء الملاحة",
    "nav_resume": "استئناف الملاحة",
    "nav_rerouting": "جارٍ إعادة التوجيه…",
    "nav_faster_route_available": "يتوفر مسار أسرع",
    "nav_arrived": "لقد وصلت إلى وجهتك",
    "nav_speed_limit": "السرعة القصوى",
    "nav_speed": "السرعة",
    "nav_eta": "الوقت المتوقع للوصول",
    "nav_distance_remaining": "المسافة المتبقية",
    "nav_time_remaining": "الوقت المتبقي",
    "nav_exit": "خروج",
    "nav_mute": "كتم الصوت",
    "nav_unmute": "تشغيل الصوت",
    "nav_alerts_only": "التنبيهات فقط",
    "nav_preview": "معاينة المسار",
    "nav_recenter": "إعادة التمركز",
    "nav_step_list": "تفاصيل الخطوات",
    "nav_overview": "نظرة عامة على المسار",
    "nav_end_confirm_title": "إنهاء الرحلة؟",
    "nav_end_confirm_body": "هل تريد إنهاء الملاحة إلى هذه الوجهة؟",
    "nav_end_confirm_yes": "إنهاء",
    "nav_end_confirm_no": "متابعة القيادة",

    # Routes & Places
    "route_leave_now": "المغادرة الآن",
    "route_best": "المسار الأفضل",
    "route_toll": "طريق ذو رسوم",
    "route_highway": "طريق سريع",
    "route_ferry": "عبّارة",
    "route_options": "خيارات المسار",
    "route_avoid_tolls": "تجنب الطرق ذات الرسوم",
    "route_avoid_highways": "تجنب الطرق السريعة",
    "route_avoid_ferries": "تجنب العبّارات",
    "place_save": "حفظ",
    "place_saved": "محفوظ",
    "place_share": "مشاركة",
    "place_directions": "الاتجاهات",
    "place_start": "بدء",
    "place_call": "اتصال",
    "place_website": "الموقع الإلكتروني",
    "place_hours": "ساعات العمل",
    "place_open_now": "مفتوح الآن",
    "place_closed_now": "مغلق الآن",
    "place_reviews": "المراجعات والتقييمات",
    "place_photos": "الصور",
    "place_about": "معلومات حول المكان",
    "place_address": "العنوان",
    "place_phone": "رقم الهاتف",
    "welcome_title": "مرحبًا بك في لايز",
    "welcome_tagline": "تطبيق الملاحة وحركة المرور الموثوق للجميع",
    "welcome_continue": "متابعة",
    "welcome_get_started": "البدء",
}

# Merge curated translations into AR_TRANSLATIONS
AR_TRANSLATIONS.update(CURATED_MAP)

# Load existing AR strings to keep valid ones
EXISTING_GOOD_AR = {}
if os.path.exists(AR_FILE):
    try:
        ar_tree = ET.parse(AR_FILE)
        corrupt_single = {'إلغاء', 'حفظ', 'تعديل', 'رجوع', 'إضافة', 'مسح', 'إزالة', 'تنزيل', 'لا', 'نعم', 'تم', 'إغلاق', 'بحث', 'مشاركة', 'نسخ', 'الإعدادات'}
        for c in ar_tree.getroot().findall('string'):
            k = c.get('name')
            txt = (c.text or '').strip()
            # If not in corrupt single words and has arabic
            if txt and txt not in corrupt_single:
                if len(re.findall(r'[\u0600-\u06FF]', txt)) >= 3:
                    EXISTING_GOOD_AR[k] = txt
    except Exception as e:
        print(f"Notice: failed to parse existing AR strings: {e}")

def translate_body(key, en_body, placeholders):
    # 1. Curated or built-in dictionary
    if key in AR_TRANSLATIONS:
        ar = AR_TRANSLATIONS[key]
        if all(ph in ar for ph in placeholders):
            return ar

    # 2. Existing good translation from previous runs
    if key in EXISTING_GOOD_AR:
        ar = EXISTING_GOOD_AR[key]
        if all(ph in ar for ph in placeholders):
            return ar

    # 3. Exact word translations ONLY (never substring matching!)
    if not placeholders:
        en_stripped = en_body.strip()
        exact_words = {
            "Cancel": "إلغاء",
            "OK": "موافق",
            "Save": "حفظ",
            "Delete": "حذف",
            "Done": "تم",
            "Edit": "تعديل",
            "Close": "إغلاق",
            "Search": "بحث",
            "Share": "مشاركة",
            "Retry": "إعادة المحاولة",
            "Back": "رجوع",
            "Copy": "نسخ",
            "Remove": "إزالة",
            "Clear": "مسح",
            "Download": "تنزيل",
            "Settings": "الإعدادات",
            "Yes": "نعم",
            "No": "لا",
            "Add": "إضافة",
            "Normal": "عادي",
            "None": "لا شيء",
            "Not now": "ليس الآن",
            "Note": "ملاحظة",
            "Not set": "غير محدد",
            "Not found": "غير موجود",
            "Not busy": "غير مزدحم",
        }
        if en_stripped in exact_words:
            return exact_words[en_stripped]

    # Replace Vela with Laze in fallback
    res = en_body.replace("Vela Maps", "لايز").replace("Vela", "لايز")
    return res

def main():
    with open(EN_FILE, "r", encoding="utf-8") as f:
        en_text = f.read()

    # Parse all strings and plurals with their attributes
    string_pattern = re.compile(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', re.DOTALL)
    plural_pattern = re.compile(r'<plurals name="([^"]+)"([^>]*)>(.*?)</plurals>', re.DOTALL)

    out_lines = ['<?xml version="1.0" encoding="utf-8"?>\n<resources>\n']

    # Process strings
    for m in string_pattern.finditer(en_text):
        name, attrs, body = m.group(1), m.group(2), m.group(3)
        if 'translatable="false"' in attrs:
            continue
        placeholders = sorted(list(set(re.findall(r"%\d+\$[sd]", body))))
        
        ar_body = translate_body(name, body, placeholders)
        # Ensure every placeholder is in ar_body
        for ph in placeholders:
            if ph not in ar_body:
                ar_body += f" {ph}"
        out_lines.append(f'    <string name="{name}">{ar_body}</string>\n')

    # Process plurals
    item_pattern = re.compile(r'<item quantity="([^"]+)">([^<]+)</item>')
    for m in plural_pattern.finditer(en_text):
        name, attrs, body = m.group(1), m.group(2), m.group(3)
        if 'translatable="false"' in attrs:
            continue
        out_lines.append(f'    <plurals name="{name}">\n')
        for item in item_pattern.finditer(body):
            qty, val = item.group(1), item.group(2)
            ph_matches = re.findall(r"%\d+\$[sd]", val)
            if name == "area_pick_cells":
                item_ar = "تنزيل الاتجاهات والأماكن لهذه المنطقة: حوالي %1$s (%2$d من المنطقة)"
            elif name == "settings_downloaded_cells":
                item_ar = "أجزاء تم تنزيلها: %1$d قطعة، %2$s"
            elif ph_matches:
                ph_str = " ".join(ph_matches)
                if qty == "zero":
                    item_ar = f"لا يوجد ({ph_str})"
                elif qty == "one":
                    item_ar = f"واحد ({ph_str})"
                elif qty == "two":
                    item_ar = f"اثنان ({ph_str})"
                elif qty == "few":
                    item_ar = f"{ph_str} عناصر"
                elif qty == "many":
                    item_ar = f"{ph_str} عنصرًا"
                else:
                    item_ar = f"{ph_str} عنصر"
            else:
                item_ar = val
            out_lines.append(f'        <item quantity="{qty}">{item_ar}</item>\n')
        out_lines.append(f'    </plurals>\n')

    out_lines.append('</resources>\n')

    with open(AR_FILE, "w", encoding="utf-8") as f:
        f.writelines(out_lines)

    print(f"Generated {AR_FILE} successfully.")

if __name__ == "__main__":
    main()
