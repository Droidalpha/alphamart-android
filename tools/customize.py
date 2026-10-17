#!/usr/bin/env python3
"""Customise a freshly generated Capacitor android project: icons, splash, manifest, signing, version.
usage: customize.py <project dir> <app label> <variant: customer|delivery>"""
import sys, os, re, math, glob
from PIL import Image, ImageDraw

proj, label, variant = sys.argv[1], sys.argv[2], sys.argv[3]
a = os.path.join(proj, 'android', 'app')
res = os.path.join(a, 'src/main/res')
REEF, MANGO, FOAM = (18, 41, 46), (240, 162, 46), (238, 243, 242)
bg, fg1, fg2 = (REEF, MANGO, FOAM) if variant == 'customer' else (MANGO, REEF, (255, 255, 255))

def wave(d, S, y0, amp, thick, color, phase=0):
    r = thick / 2
    x = S * .17
    while x <= S * .83:
        y = y0 + amp * math.sin((x / S) * 2 * math.pi * 1.5 + phase)
        d.ellipse([x - r, y - r, x + r, y + r], fill=color)
        x += 1.5

def mark(S, transparent):
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0) if transparent else bg + (255,))
    d = ImageDraw.Draw(img)
    wave(d, S, S * .43, S * .07, int(S * .075), fg1)
    wave(d, S, S * .60, S * .07, int(S * .075), fg2, phase=1.2)
    return img

BIG = 1024
fg_big, full_big = mark(BIG, True), mark(BIG, False)
dens = {'mdpi': (48, 108), 'hdpi': (72, 162), 'xhdpi': (96, 216), 'xxhdpi': (144, 324), 'xxxhdpi': (192, 432)}
for k, (legacy, adaptive) in dens.items():
    d = os.path.join(res, f'mipmap-{k}')
    fg_big.resize((adaptive, adaptive), Image.LANCZOS).save(f'{d}/ic_launcher_foreground.png')
    sq = full_big.resize((legacy, legacy), Image.LANCZOS)
    m = Image.new('L', (legacy, legacy), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, legacy - 1, legacy - 1], legacy // 5, fill=255)
    o = Image.new('RGBA', (legacy, legacy), (0, 0, 0, 0)); o.paste(sq, (0, 0), m); o.save(f'{d}/ic_launcher.png')
    m = Image.new('L', (legacy, legacy), 0); ImageDraw.Draw(m).ellipse([0, 0, legacy - 1, legacy - 1], fill=255)
    o = Image.new('RGBA', (legacy, legacy), (0, 0, 0, 0)); o.paste(sq, (0, 0), m); o.save(f'{d}/ic_launcher_round.png')
hexbg = '#%02X%02X%02X' % bg
open(f'{res}/values/ic_launcher_background.xml', 'w').write(f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">{hexbg}</color>\n</resources>\n')

# splash: reef-coloured with the mark in the middle, same sizes as the generated files
for f in glob.glob(f'{res}/drawable*/splash.png'):
    w, h = Image.open(f).size
    img = Image.new('RGBA', (w, h), REEF + (255,))
    s = int(min(w, h) * .38)
    m = mark(BIG, False) if variant == 'customer' else mark(BIG, False)
    # splash is always reef; use the customer mark colours so it reads on dark
    sp = Image.new('RGBA', (BIG, BIG), REEF + (255,)); dd = ImageDraw.Draw(sp)
    wave(dd, BIG, BIG * .43, BIG * .07, int(BIG * .075), MANGO); wave(dd, BIG, BIG * .60, BIG * .07, int(BIG * .075), FOAM, 1.2)
    img.paste(sp.resize((s, s), Image.LANCZOS), ((w - s) // 2, (h - s) // 2)); img.convert('RGB').save(f)

# app name
sp = f'{res}/values/strings.xml'; s = open(sp).read()
s = re.sub(r'(<string name="app_name">)[^<]*', r'\g<1>' + label, s)
s = re.sub(r'(<string name="title_activity_main">)[^<]*', r'\g<1>' + label, s)
open(sp, 'w').write(s)

# status bar / theme colours
cp = f'{res}/values/colors.xml'
if os.path.exists(cp):
    c = open(cp).read()
    c = re.sub(r'(<color name="colorPrimary(?:Dark)?">)[^<]*', r'\g<1>#12292E', c); open(cp, 'w').write(c)

# gradle: signing from env, version from env
gp = f'{a}/build.gradle'; g = open(gp).read()
if 'KEYSTORE_PATH' not in g:
    g = g.replace('versionCode 1', 'versionCode (System.getenv("VERSION_CODE") ?: "1").toInteger()')
    g = g.replace('versionName "1.0"', 'versionName (System.getenv("VERSION_NAME") ?: "1.0")')
    g = g.replace('    buildTypes {', '''    signingConfigs {
        release {
            // Filled from environment variables so the keystore never lives in the repo.
            def ks = System.getenv("KEYSTORE_PATH")
            if (ks) {
                storeFile file(ks)
                storePassword System.getenv("KEYSTORE_PASSWORD")
                keyAlias System.getenv("KEY_ALIAS")
                keyPassword System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {''', 1)
    g = g.replace("            minifyEnabled false\n", "            minifyEnabled false\n            if (System.getenv(\"KEYSTORE_PATH\")) { signingConfig signingConfigs.release }\n", 1)
open(gp, 'w').write(g)

# manifest
mp = f'{a}/src/main/AndroidManifest.xml'; m = open(mp).read()
if '<queries>' not in m:
    q = '''    <!-- Let the page open UPI apps (GPay, PhonePe, Paytm...) and phone / maps links -->
    <queries>
        <intent><action android:name="android.intent.action.VIEW" /><data android:scheme="upi" /></intent>
        <intent><action android:name="android.intent.action.VIEW" /><data android:scheme="tel" /></intent>
        <intent><action android:name="android.intent.action.VIEW" /><data android:scheme="geo" /></intent>
        <intent><action android:name="android.intent.action.VIEW" /><data android:scheme="https" /></intent>
    </queries>

'''
    m = m.replace('    <application', q + '    <application', 1)
    m = m.replace('android:allowBackup="true"', 'android:allowBackup="false"\n        android:usesCleartextTraffic="false"' if False else 'android:allowBackup="false"')
    perms = '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n'
    if variant == 'delivery':
        perms += '''    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
'''
    m = m.replace('<uses-permission android:name="android.permission.INTERNET" />', '<uses-permission android:name="android.permission.INTERNET" />\n' + perms.rstrip('\n'))
open(mp, 'w').write(m)
print('customised', proj)
