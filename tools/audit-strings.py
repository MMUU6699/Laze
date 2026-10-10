#!/usr/bin/env python3
"""Audit Arabic translations (values-ar/strings.xml) against English base.

Checks for:
1. Generic filler strings:
   - "خيارات لايز المتقدمة للملاحة وحركة المرور"
   - "إعدادات وتفضيلات تشغيل الميزة وخيارات الخصوصية في لايز"
2. Identical-to-English strings (untranslated strings where English text leaked into Arabic file).
3. Mixed Arabic/Latin strings (where Latin characters appear alongside Arabic characters,
   excluding whitelisted brand names, units, and format specifiers).

Exit code: 1 if any infractions are found, 0 if clean.
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

RES_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
EN_FILE = os.path.join(RES_DIR, "values", "strings.xml")
AR_FILE = os.path.join(RES_DIR, "values-ar", "strings.xml")

# Generic filler substrings that were used as bulk placeholders
GENERIC_FILLERS = [
    "خيارات لايز المتقدمة للملاحة وحركة المرور",
    "إعدادات وتفضيلات تشغيل الميزة وخيارات الخصوصية في لايز",
]

# Whitelisted brand names and technical abbreviations allowed in Arabic strings
ALLOWED_BRANDS_AND_TERMS = {
    "laze", "mapbox", "openstreetmap", "osm",
    "gps", "tts", "eta", "url", "id", "2d", "3d", "hd",
    "am", "pm",
    "km/h", "km", "m", "mi", "mph", "min", "h",
    "wi-fi", "wifi", "bluetooth", "android", "auto",
    "rtl", "ltr", "api", "apk", "sdk", "oauth",
    "google", "apple", "samsung",
    "weblate", "github", "osmand", "valhalla", "photon", "nominatim",
    "openfreemap", "maptiler", "geofabrik", "planetiler", "pmtiles",
    "docker", "nginx", "postgresql", "supabase", "fastapi", "postgis",
    "pbf", "geojson", "cctv", "waze", "v1", "v2", "v3",
    "gpx", "kml", "csv", "tsv", "material", "you", "deflock",
    "sd", "zip", "pip", "amoled", "gplv3", "odbl", "psds", "p",
    "play", "maps", "overture", "alltheplaces", "sans", "flex", "geo",
    "nightly", "canary",
}

# Whitelisted values that are legitimately identical between AR and EN (e.g. brand names, units, URLs)
ALLOWED_IDENTICAL_VALUES = {
    "laze", "mapbox", "openstreetmap", "osm", "overture",
    "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
    "km/h", "mph", "m", "km", "mi",
    "am", "pm",
    "gps", "tts", "eta", "url", "id", "3d", "2d", "hd",
    "android auto", "wi-fi", "bluetooth",
    "%1$s", "%s", "%d", "%1$d", "%1$s — %2$s", "%1$s: %2$s",
}


def is_allowed_identical(text):
    clean = text.lower().strip()
    if clean in ALLOWED_IDENTICAL_VALUES:
        return True
    # If text is solely format specifiers, punctuation, math signs, numbers
    stripped = re.sub(r'%\d+\$[sdf]|%[sdf]|[0-9\s.,:;()\[\]{}·/\-+—]', '', text)
    if not stripped:
        return True
    return False


def load_strings(path):
    """Parse <string> elements, returning dict of {name: (text, is_translatable)}."""
    if not os.path.exists(path):
        return {}
    tree = ET.parse(path)
    root = tree.getroot()
    res = {}
    for elem in root.findall("string"):
        name = elem.attrib.get("name")
        if not name:
            continue
        translatable = elem.attrib.get("translatable", "true").lower() != "false"
        text = elem.text or ""
        # Handle internal tags or entities if any
        if len(elem) > 0:
            text = "".join(elem.itertext())
        res[name] = (text, translatable)
    return res


def is_allowed_mixed_latin(word):
    clean = word.lower().strip(".,:;()[]{}\"'-_/")
    if not clean:
        return True
    return clean in ALLOWED_BRANDS_AND_TERMS


def audit():
    en_strings = load_strings(EN_FILE)
    ar_strings = load_strings(AR_FILE)

    if not en_strings or not ar_strings:
        print("Error: Could not load string resource files.", file=sys.stderr)
        return 1

    fillers = []
    identical = []
    mixed = []

    for name, (ar_text, translatable) in ar_strings.items():
        if not translatable:
            continue

        trimmed_ar = ar_text.strip()
        if not trimmed_ar:
            continue

        # 1. Check Generic Fillers
        for filler in GENERIC_FILLERS:
            if filler in trimmed_ar:
                fillers.append((name, trimmed_ar))
                break

        # 2. Check Identical to English
        if name in en_strings:
            en_text, en_translatable = en_strings[name]
            trimmed_en = en_text.strip()
            if trimmed_en and trimmed_en == trimmed_ar:
                if not is_allowed_identical(trimmed_en):
                    identical.append((name, trimmed_ar))

        # 3. Check Mixed Arabic / Latin
        clean_text = re.sub(r'%\d+\$[sdf]|%[sdf]|\\n|\\t', ' ', trimmed_ar)
        has_arabic = bool(re.search(r'[\u0600-\u06FF]', clean_text))
        if has_arabic:
            latin_words = re.findall(r'[a-zA-Z0-9_-]+', clean_text)
            unallowed = [w for w in latin_words if not is_allowed_mixed_latin(w) and not w.isdigit()]
            if unallowed:
                mixed.append((name, trimmed_ar, unallowed))

    print("========================================")
    print(" Laze Arabic Strings Audit Report")
    print("========================================")
    print(f"Total English Keys : {len(en_strings)}")
    print(f"Total Arabic Keys  : {len(ar_strings)}")
    print(f"1. Generic Fillers : {len(fillers)}")
    print(f"2. Identical to EN : {len(identical)}")
    print(f"3. Mixed Text      : {len(mixed)}")
    print("========================================")

    if fillers:
        print("\n--- Generic Fillers Samples (up to 5): ---")
        for name, text in fillers[:5]:
            print(f"  [{name}]: {text}")

    if identical:
        print("\n--- Identical to EN Samples (up to 5): ---")
        for name, text in identical[:5]:
            print(f"  [{name}]: {text}")

    if mixed:
        print("\n--- Mixed Text Samples (up to 5): ---")
        for name, text, unallowed in mixed[:5]:
            print(f"  [{name}]: {text} -> Unallowed words: {unallowed}")

    if fillers or identical or mixed:
        print("\nAUDIT FAILED: Corrupted or untranslated strings found.")
        return 1

    print("\nAUDIT PASSED: All Arabic strings are clean, localized, and uncorrupted.")
    return 0


if __name__ == "__main__":
    sys.exit(audit())
