#!/usr/bin/env python3
"""Validate Google Play listing copy under android/play-assets/metadata/android.

Checks every locale folder for Play's limits (title 30, short 80, full 4000),
the house rules (no em dashes, no prices, no Apple wording, no promo words in
the title, no medical claims) and the StatScout specifics: the brand in every
title, the Google Play billing paragraph, the privacy and terms links, the NFL
non-affiliation line and, outside English, the line saying the app is in
English. Exit 1 on any failure.

Usage: scripts/validate-play-listing.py [--root DIR]
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "android/play-assets/metadata/android"
LIMITS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}
# Play language codes accepted by the Publishing API (fastlane supply list).
PLAY_LANGS = set(
    "af am ar az-AZ be bg bn-BD ca cs-CZ da-DK de-DE el-GR en-AU en-CA en-GB en-IN "
    "en-SG en-US en-ZA es-419 es-ES es-US et eu-ES fa fi-FI fil fr-CA fr-FR gl-ES "
    "hi-IN hr hu-HU hy-AM id is-IS it-IT iw-IL ja-JP ka-GE km-KH kn-IN ko-KR ky-KG "
    "lo-LA lt lv mk-MK ml-IN mn-MN mr-IN ms ms-MY my-MM ne-NP nl-NL no-NO pl-PL "
    "pt-BR pt-PT rm ro ru-RU si-LK sk sl sr sv-SE sw ta-IN te-IN th tr-TR uk vi "
    "zh-CN zh-HK zh-TW zu".split()
)
# The 50 locales this listing ships in (same set as Sober).
EXPECTED = set(
    "en-US en-GB en-AU en-CA en-IN de-DE fr-FR fr-CA es-ES es-419 es-US pt-BR pt-PT "
    "it-IT nl-NL sv-SE no-NO da-DK fi-FI pl-PL cs-CZ sk sl hr hu-HU ro el-GR tr-TR "
    "ru-RU uk ar iw-IL hi-IN bn-BD mr-IN ta-IN te-IN kn-IN ml-IN id ms ms-MY th vi "
    "ja-JP ko-KR zh-CN zh-TW zh-HK ca".split()
)
# The sentence each non-English description must carry: the Android app is English only.
ENGLISH_NOTE = {
    "de-DE": "Die App selbst ist auf Englisch.",
    "fr-FR": "L'application elle-même est en anglais.",
    "fr-CA": "L'application elle-même est en anglais.",
    "es-ES": "La app está en inglés.",
    "es-419": "La app está en inglés.",
    "es-US": "La app está en inglés.",
    "pt-BR": "O app está em inglês.",
    "pt-PT": "A app está em inglês.",
    "it-IT": "L'app è in inglese.",
    "nl-NL": "De app zelf is in het Engels.",
    "ca": "L'app està en anglès.",
    "sv-SE": "Själva appen är på engelska.",
    "no-NO": "Selve appen er på engelsk.",
    "da-DK": "Selve appen er på engelsk.",
    "fi-FI": "Itse sovellus on englanninkielinen.",
    "pl-PL": "Sama aplikacja jest w języku angielskim.",
    "cs-CZ": "Samotná aplikace je v angličtině.",
    "sk": "Samotná aplikácia je v angličtine.",
    "sl": "Aplikacija sama je v angleščini.",
    "hr": "Sama aplikacija je na engleskom jeziku.",
    "hu-HU": "Maga az alkalmazás angol nyelvű.",
    "ro": "Aplicația în sine este în limba engleză.",
    "el-GR": "Η ίδια η εφαρμογή είναι στα αγγλικά.",
    "tr-TR": "Uygulamanın kendisi İngilizcedir.",
    "ru-RU": "Само приложение на английском языке.",
    "uk": "Сам застосунок англійською мовою.",
    "ar": "التطبيق نفسه باللغة الإنجليزية.",
    "iw-IL": "האפליקציה עצמה באנגלית.",
    "hi-IN": "ऐप खुद अंग्रेज़ी में है।",
    "bn-BD": "অ্যাপটি নিজে ইংরেজিতে।",
    "mr-IN": "ॲप स्वतः इंग्रजीमध्ये आहे.",
    "ta-IN": "செயலி ஆங்கிலத்தில் மட்டுமே உள்ளது.",
    "te-IN": "యాప్ ఇంగ్లీష్‌లో మాత్రమే ఉంది.",
    "kn-IN": "ಆ್ಯಪ್ ಇಂಗ್ಲಿಷ್‌ನಲ್ಲಿ ಮಾತ್ರ ಇದೆ.",
    "ml-IN": "ആപ്പ് ഇംഗ്ലീഷിൽ മാത്രമാണ്.",
    "id": "Aplikasinya sendiri berbahasa Inggris.",
    "ms": "Aplikasi ini sendiri dalam bahasa Inggeris.",
    "ms-MY": "Aplikasi ini sendiri dalam bahasa Inggeris.",
    "th": "ตัวแอปเป็นภาษาอังกฤษ",
    "vi": "Bản thân ứng dụng sử dụng tiếng Anh.",
    "ja-JP": "アプリの表示言語は英語です。",
    "ko-KR": "앱 자체는 영어로 제공됩니다.",
    "zh-CN": "应用本身为英文界面。",
    "zh-TW": "App 本身為英文介面。",
    "zh-HK": "App 本身為英文介面。",
}
BRAND = "StatScout"
TITLE_BANNED = re.compile(r"\b(free|best|top|#1|new|sale)\b", re.I)
PRICE = re.compile(r"[$€£¥₹]\s?\d|\d[.,]\d\d\s?(?:€|zł|kr|lei|Kč|₺|₽|₴)")
APPLE = re.compile(r"\b(?:Apple|App Store|iPhone|iPad|iCloud|Apple Watch)\b", re.I)
MEDICAL = re.compile(r"\b(treat|treats|treatment|cure|cures|diagnose|diagnosis)\b", re.I)
URL_OK = re.compile(r"https://jackwallner\.github\.io/\S+")
PRIVACY_URL = "https://jackwallner.github.io/football/android-privacy.html"
TERMS_URL = "https://jackwallner.github.io/football/android-terms.html"


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8").strip() if p.exists() else ""


def check(locale_dir: Path) -> list[str]:
    errs: list[str] = []
    loc = locale_dir.name
    if loc not in PLAY_LANGS:
        errs.append(f"unknown Play language code {loc}")
    for name, limit in LIMITS.items():
        p = locale_dir / name
        if not p.exists():
            errs.append(f"missing {name}")
            continue
        text = read(p)
        n = len(text)
        if n == 0 or n > limit:
            errs.append(f"{name} {n}/{limit}")
        if "—" in text:
            errs.append(f"{name} has an em dash")
        if re.search("[­​﻿]", text):
            errs.append(f"{name} has an invisible character")
        if PRICE.search(text):
            errs.append(f"{name} looks like it has a price: {PRICE.search(text).group(0)!r}")
        if APPLE.search(URL_OK.sub("", text)):
            errs.append(f"{name} mentions Apple wording: {APPLE.search(text).group(0)!r}")
        if MEDICAL.search(text):
            errs.append(f"{name} makes a medical claim: {MEDICAL.search(text).group(0)!r}")
        if name != "full_description.txt" and "\n" in text:
            errs.append(f"{name} spans lines")
    title = read(locale_dir / "title.txt")
    full = read(locale_dir / "full_description.txt")
    if TITLE_BANNED.search(title):
        errs.append(f"title has a promo word: {title!r}")
    if BRAND not in title:
        errs.append(f"title drops the {BRAND} brand")
    if "Google Play" not in full:
        errs.append("full description lacks the Google Play billing paragraph")
    for url in (PRIVACY_URL, TERMS_URL):
        if url not in full:
            errs.append(f"full description lacks {url}")
    if "NFLPA" not in full:
        errs.append("full description lacks the NFL non-affiliation line")
    if not loc.startswith("en"):
        note = ENGLISH_NOTE.get(loc)
        if note is None:
            errs.append("no app-is-in-English sentence defined for this locale")
        elif note not in full:
            errs.append(f"full description lacks {note!r}")
    return errs


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", type=Path, default=ROOT)
    args = ap.parse_args()
    bad = 0
    dirs = sorted(d for d in args.root.iterdir() if d.is_dir())
    for d in dirs:
        errs = check(d)
        t = read(d / "title.txt")
        s = read(d / "short_description.txt")
        f = read(d / "full_description.txt")
        status = "OK " if not errs else "BAD"
        print(f"{status} {d.name:7} t{len(t):>3} s{len(s):>3} f{len(f):>5}  {t}")
        for e in errs:
            print(f"      - {e}")
        bad += bool(errs)
    missing = sorted(EXPECTED - {d.name for d in dirs})
    if missing:
        print(f"\nmissing locales: {' '.join(missing)}")
        bad += len(missing)
    print(f"\n{len(dirs)} locales, {bad} with problems")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
