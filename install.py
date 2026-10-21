#!/usr/bin/env python3
"""Sun Home: встановлення на Android TV / Google TV з комп'ютера в тій самій Wi-Fi мережі.

Запуск:   python3 install.py            (або подвійний клік на install.command / install.bat)
Ключі:    --host 192.168.1.50   адреса ТВ, щоб не питати
          --apk launcher.apk    свій APK замість завантаження з релізу на GitHub
          --skip-debloat        нічого не вимикати, лише поставити лаунчер

Що робить, по черзі, і питає перед кожним кроком, що змінює ТВ:
  1. ставить потрібне на комп'ютері (Python-клієнт ADB у tools/venv);
  2. знаходить ТВ у мережі й підключається (на ТВ треба дозволити налагодження);
  3. зберігає бекап: список пакетів, налаштування, getprop;
  4. показує, що можна вимкнути, і вимикає лише вибране (pm disable-user, нічого не видаляється);
  5. ставить лаунчер Sun Home головним екраном і місто для теми за сонцем;
  6. пише, як усе повернути (tools/restore.sh з тим самим списком).

Ключові кроки виконують перевірені скрипти репозиторію (tools/disable.sh, launcher/build-and-install.sh,
tools/theme.sh); цей файл лише питає й передає їм вибір.
"""
import argparse
import concurrent.futures
import datetime
import ipaddress
import json
import os
import re
import socket
import ssl
import subprocess
import sys
import tempfile
import urllib.request

ROOT = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.join(ROOT, "tools")
VENV = os.path.join(TOOLS, "venv")
WINDOWS = os.name == "nt"
VENV_PY = os.path.join(VENV, "Scripts" if WINDOWS else "bin", "python.exe" if WINDOWS else "python")
RELEASES = "https://api.github.com/repos/minkoroman/cleanyourtv/releases/latest"

# Пакети, без яких ТВ не працює як слід: їх не пропонуємо ніколи, хоч би що було в списках інших моделей.
NEVER = (
    "com.android.tv.settings", "com.android.vending", "com.google.android.gms", "com.google.android.gsf",
    "com.android.providers.", "com.android.systemui", "com.android.packageinstaller", "com.android.bluetooth",
    "inputmethod", "com.google.android.tv.remote", "com.android.tv.", "tvinput", "com.android.shell",
    "com.android.permissioncontroller", "com.google.android.webview", "com.android.webview",
    "com.android.externalstorage", "com.android.networkstack", "com.android.wifi", "com.gathin.tvhome",
)
# Їх вимикає встановлення лаунчера (з перевірками), а не цей список.
HANDLED_BY_LAUNCHER = ("com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
                       "com.google.android.leanbacklauncher", "com.mitv.tvhome",
                       "com.google.android.tungsten.setupwraith")
# Це питання смаку: позначаються, лише якщо ти сам їх вибереш.
ASK_PATTERNS = {
    "голосовий асистент": ("katniss", "assistant", "voice"),
    "Chromecast / трансляція екрана": ("mediashell", "chromecast", "miracast", "screenshare", "airplay", "dlna"),
    "оновлення прошивки": ("ota", "fota", "upgrade", "update"),
    "стрімінгові застосунки": ("youtube", "netflix", "amazon", "primevideo", "megogo", "sweet", "kyivstar",
                               "disney", "hbo", "spotify", "videos", "play.games", "music"),
}
FRIENDLY = {
    "com.google.android.feedback": "надсилання відгуків Google",
    "com.google.android.play.games": "Google Play Ігри",
    "com.google.android.videos": "Google TV / Play Фільми",
    "com.google.android.tvrecommendations": "ряди рекомендацій Google",
    "com.google.android.youtube.tvmusic": "YouTube Music",
    "com.google.android.katniss": "Google Асистент (голос)",
    "com.google.android.partnersetup": "партнерські налаштування Google",
    "com.google.android.syncadapters.calendar": "синхронізація календаря",
    "com.android.printspooler": "друк",
    "com.google.android.backdrop": "заставка Ambient",
    "com.google.android.marvin.talkback": "TalkBack (озвучення екрана)",
    "com.google.android.tv.bugreportsender": "звіти про помилки",
    "com.google.android.apps.mediashell": "Chromecast built-in",
    "com.google.android.youtube.tv": "YouTube",
    "com.netflix.ninja": "Netflix",
}
CITIES = [
    ("Київ", 50.45, 30.52), ("Львів", 49.84, 24.03), ("Одеса", 46.48, 30.73), ("Харків", 49.99, 36.23),
    ("Дніпро", 48.46, 35.05), ("Запоріжжя", 47.84, 35.14), ("Вінниця", 49.23, 28.47),
    ("Полтава", 49.59, 34.55), ("Кременчук", 49.07, 33.42), ("Черкаси", 49.44, 32.06),
    ("Житомир", 50.25, 28.66), ("Чернігів", 51.49, 31.29), ("Суми", 50.91, 34.80),
    ("Хмельницький", 49.42, 26.99), ("Рівне", 50.62, 26.25), ("Луцьк", 50.75, 25.34),
    ("Тернопіль", 49.55, 25.59), ("Івано-Франківськ", 48.92, 24.71), ("Ужгород", 48.62, 22.29),
    ("Чернівці", 48.29, 25.94), ("Кропивницький", 48.51, 32.26), ("Миколаїв", 46.98, 31.99),
    ("Херсон", 46.64, 32.61),
]


def say(text=""):
    print(text, flush=True)


def ask(prompt, default=""):
    try:
        answer = input(f"{prompt} " + (f"[{default}] " if default else ""))
    except EOFError:
        answer = ""
    return answer.strip() or default


def yes(prompt, default=True):
    answer = ask(prompt + (" (Т/н)" if default else " (т/Н)")).lower()
    return default if not answer else answer[0] in "tтyд"


# ---- 1. комп'ютер ---------------------------------------------------------------------------------------

def bootstrap():
    """Створює tools/venv з клієнтом ADB і перезапускає інсталятор у ньому."""
    if os.path.abspath(sys.prefix) == os.path.abspath(VENV):
        return
    if not os.path.exists(VENV_PY):
        say("Готую комп'ютер: ставлю Python-клієнт ADB у tools/venv (один раз)…")
        subprocess.check_call([sys.executable, "-m", "venv", VENV])
        subprocess.check_call([VENV_PY, "-m", "pip", "install", "-q", "-r", os.path.join(TOOLS, "requirements.txt")])
    os.execv(VENV_PY, [VENV_PY, os.path.abspath(__file__)] + sys.argv[1:])


def tv(host, *args, check=False):
    """tools/tv.py з адресою ТВ; повертає (код, вивід)."""
    env = dict(os.environ, TV_HOST=host)
    p = subprocess.run([VENV_PY, "-I", os.path.join(TOOLS, "tv.py"), *args], env=env,
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(p.stderr.strip() or p.stdout.strip())
    return p.returncode, p.stdout


def script(host, name, *args, extra_env=None):
    """Запускає скрипт репозиторію: .sh на Mac/Linux, .ps1 на Windows. Вивід іде прямо на екран."""
    env = dict(os.environ, TV_HOST=host, **(extra_env or {}))
    if WINDOWS:
        cmd = ["powershell", "-ExecutionPolicy", "Bypass", "-File", os.path.join(ROOT, name + ".ps1"),
               "-TvHost", host, *args]
    else:
        cmd = ["bash", os.path.join(ROOT, name + ".sh"), *args]
    return subprocess.call(cmd, env=env, cwd=ROOT)


# ---- 2. ТВ у мережі -------------------------------------------------------------------------------------

def local_subnet():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ipaddress.ip_network(ip + "/24", strict=False)
    except OSError:
        return None


def port_open(ip, port=5555, timeout=0.4):
    try:
        with socket.create_connection((str(ip), port), timeout=timeout):
            return True
    except OSError:
        return False


def find_tvs():
    net = local_subnet()
    if not net:
        return []
    say(f"Шукаю ТВ у мережі {net} (порт ADB 5555)…")
    with concurrent.futures.ThreadPoolExecutor(64) as pool:
        hosts = list(net.hosts())
        return [str(h) for h, ok in zip(hosts, pool.map(port_open, hosts)) if ok]


def connect(host_arg):
    host = host_arg
    if not host:
        found = find_tvs()
        if len(found) == 1 and yes(f"Знайшов ТВ на {found[0]}. Це він?"):
            host = found[0]
        elif found:
            say("Знайшов кілька: " + ", ".join(found))
        if not host:
            host = ask("Введи IP-адресу ТВ (Налаштування → Мережа → про мережу):")
    say("\nПідключаюсь. Якщо на ТВ з'явиться «Дозволити налагодження USB?», постав галочку")
    say("«Завжди дозволяти з цього комп'ютера» і натисни OK пультом.")
    for attempt in range(3):
        code, out = tv(host, "--probe")
        if code == 0 and "ro.product.model" in out:
            props = dict(re.findall(r"(ro\.[\w.]+) = (.*)", out))
            say(f"Підключено: {props.get('ro.product.manufacturer', '')} {props.get('ro.product.model', '')}, "
                f"Android {props.get('ro.build.version.release', '?')}")
            return host, props
        say("Не вийшло підключитись. Перевір, що ТВ увімкнений, у тій самій мережі, і в «Для розробників»")
        say("ввімкнене «Налагодження USB» (і «Налагодження по мережі», якщо є).")
        if attempt < 2 and not yes("Спробувати ще раз?"):
            break
    sys.exit("Не вдалося підключитись до ТВ.")


# ---- 3. бекап -------------------------------------------------------------------------------------------

def backup(host, props):
    model = re.sub(r"[^A-Za-z0-9_.-]+", "_", props.get("ro.product.model", "tv"))
    folder = os.path.join(ROOT, "backups", f"{model}-{datetime.datetime.now():%Y%m%d-%H%M}")
    os.makedirs(folder, exist_ok=True)
    for name, cmd in [("packages.txt", "pm list packages -f"), ("disabled-before.txt", "pm list packages -d"),
                      ("third-party.txt", "pm list packages -3"), ("getprop.txt", "getprop"),
                      ("settings-global.txt", "settings list global"), ("settings-secure.txt", "settings list secure"),
                      ("settings-system.txt", "settings list system")]:
        _, out = tv(host, cmd)
        with open(os.path.join(folder, name), "w", encoding="utf-8") as f:
            f.write(out)
    say(f"Бекап збережено: {os.path.relpath(folder, ROOT)}")
    return folder


# ---- 4. що вимкнути -------------------------------------------------------------------------------------

def known_bloat():
    """Скільки разів кожен пакет вимикали на інших моделях з examples/."""
    counts = {}
    ex = os.path.join(ROOT, "examples")
    for d in os.listdir(ex):
        f = os.path.join(ex, d, "debloat.txt")
        if not os.path.isfile(f):
            continue
        for line in open(f, encoding="utf-8", errors="replace"):
            pkg = line.split("#", 1)[0].strip().split()
            if pkg and re.fullmatch(r"[A-Za-z][\w]*(\.[\w]+)+", pkg[0]):
                counts[pkg[0]] = counts.get(pkg[0], 0) + 1
    return counts


def taste_group(pkg):
    """Короткі слова (ota) — лише цілою частиною назви, щоб rotationresolver не став «оновленням»."""
    low = pkg.lower()
    parts = re.split(r"[._]", low)
    for group, words in ASK_PATTERNS.items():
        if any((w in parts) if len(w) <= 4 else (w in low) for w in words):
            return group
    return None


def choose(host):
    _, enabled_out = tv(host, "pm list packages -e")
    _, third_out = tv(host, "pm list packages -3")
    enabled = sorted(re.findall(r"package:(\S+)", enabled_out))
    third = set(re.findall(r"package:(\S+)", third_out))
    seen = known_bloat()
    rows = []   # (pkg, підпис, позначено)
    for pkg in enabled:
        if any(n in pkg for n in NEVER) or pkg in HANDLED_BY_LAUNCHER:
            continue
        group = taste_group(pkg)
        if pkg in seen:
            note = FRIENDLY.get(pkg, f"є у списках інших ТВ: {seen[pkg]}")
            # Позначаю лише те, що вимикали щонайменше на трьох моделях: рідкісне могло бути потрібне там.
            # Встановлене людиною (Apple TV, MEGOGO) не позначаю ніколи, хоч би де його вимикали.
            rows.append((pkg, ("питання смаку: " + group + "; " if group else "")
                         + ("твій застосунок; " if pkg in third else "") + note,
                         group is None and pkg not in third and seen[pkg] >= 3))
        elif pkg in third:
            rows.append((pkg, "твій застосунок", False))
    if not rows:
        say("Нічого з відомого сміття на цьому ТВ не знайшов.")
        return []
    say("\nЩо вимкнути. [x] — вимкну, [ ] — лишу. Нічого не видаляється, усе повертається restore.")
    say("Позначено те, що вимикали щонайменше на 3 інших моделях. Голос, Chromecast, оновлення і застосунки —")
    say("на твій вибір. Якщо назва нічого не каже, краще лиши: вимкнути можна й пізніше.")
    picked = [r[2] for r in rows]
    while True:
        for i, (pkg, note, _) in enumerate(rows, 1):
            say(f"  {i:3}. [{'x' if picked[i - 1] else ' '}] {pkg}  — {note}")
        answer = ask("\nНомери через пробіл, щоб змінити позначку; «все» / «нічого»; Enter — далі:")
        if not answer:
            break
        if answer.lower() in ("все", "all"):
            picked = [True] * len(rows)
        elif answer.lower() in ("нічого", "none"):
            picked = [False] * len(rows)
        else:
            for n in re.findall(r"\d+", answer):
                if 1 <= int(n) <= len(rows):
                    picked[int(n) - 1] = not picked[int(n) - 1]
    return [r[0] for r, p in zip(rows, picked) if p]


def stock_launcher(host):
    _, out = tv(host, "cmd package resolve-activity --brief -a android.intent.action.MAIN "
                      "-c android.intent.category.HOME")
    lines = [l.strip() for l in out.splitlines() if "/" in l]
    pkg = lines[-1].split("/")[0] if lines else ""
    return "" if pkg in ("", "android", "com.gathin.tvhome") else pkg


def write_restore(folder, host, lst, stock):
    """Спершу інструкція, потім зміни: якщо щось урветься посередині, вона вже лежить у бекапі."""
    rel = os.path.relpath(lst, ROOT)
    restore = os.path.join(folder, "RESTORE.txt")
    with open(restore, "w", encoding="utf-8") as f:
        f.write("Як повернути ТВ як було (з папки репозиторію, ТВ у мережі, налагодження ввімкнене):\n\n")
        if WINDOWS:
            f.write(f"powershell -ExecutionPolicy Bypass -File tools\\restore.ps1 -TvHost {host} {rel} {stock}\n")
        else:
            f.write(f"TV_HOST={host} tools/restore.sh {rel} {stock}\n")
        f.write("\nЦе вмикає все вимкнене і повертає стоковий головний екран"
                + (f" ({stock})" if stock else "") + ". Сам лаунчер після цього можна прибрати:\n"
                f"TV_HOST={host} tools/tv.py 'pm uninstall com.gathin.tvhome'\n")
    return restore


def debloat(host, folder, packages, props):
    lst = os.path.join(folder, "debloat.txt")
    with open(lst, "w", encoding="utf-8") as f:
        f.write(f"# {props.get('ro.product.manufacturer', '')} {props.get('ro.product.model', '')}, "
                f"Android {props.get('ro.build.version.release', '?')}: вибрано в install.py "
                f"{datetime.date.today()}\n")
        f.write("".join(p + "\n" for p in packages))
    if not packages:
        say("Нічого не вимикаю.")
        return lst
    say(f"\nВимикаю {len(packages)} пакетів (tools/disable.sh)…")
    if script(host, os.path.join("tools", "disable"), lst) != 0:
        sys.exit("Вимкнення зупинилось з помилкою, див. вище. ТВ можна повернути: див. RESTORE.txt у бекапі.")
    return lst


# ---- 5. лаунчер і місто ---------------------------------------------------------------------------------

def download(url, path=None):
    """HTTPS that works on a Mac too: python.org's Python there has no CA certificates of its own, so take them
    from certifi (installed into tools/venv on first use), and fall back to the system curl."""
    try:
        try:
            import certifi
        except ImportError:
            subprocess.check_call([sys.executable, "-m", "pip", "install", "-q", "--disable-pip-version-check", "certifi"])
            import certifi
        ctx = ssl.create_default_context(cafile=certifi.where())
        req = urllib.request.Request(url, headers={"User-Agent": "sun-home-installer"})
        with urllib.request.urlopen(req, timeout=60, context=ctx) as r:
            data = r.read()
    except Exception as e:   # no pip/network for certifi, or SSL still refused: let curl try
        say(f"(завантаження через Python не вдалося: {type(e).__name__}; пробую curl)")
        out = subprocess.run(["curl", "-fsSL", "-A", "sun-home-installer", url], capture_output=True)
        if out.returncode != 0:
            sys.exit("Не вдалося завантажити " + url + ": " + out.stderr.decode(errors="replace").strip())
        data = out.stdout
    if path:
        with open(path, "wb") as f:
            f.write(data)
    return data


def launcher_apk(apk_arg):
    if apk_arg:
        return os.path.abspath(apk_arg)
    say("Завантажую лаунчер з останнього релізу на GitHub…")
    release = json.loads(download(RELEASES))
    asset = next(a for a in release["assets"] if a["name"].endswith(".apk"))
    path = os.path.join(tempfile.gettempdir(), asset["name"])
    download(asset["browser_download_url"], path)
    say(f"Лаунчер {release.get('tag_name', '')}: {asset['name']}")
    return path


def pick_city():
    say("\nМісто для теми за сонцем (схід — світла, захід — темна):")
    for i, (name, _, _) in enumerate(CITIES, 1):
        say(f"  {i:2}. {name}")
    say(f"  {len(CITIES) + 1:2}. інше (введу координати)")
    while True:
        n = ask("Номер:", "1")
        if n.isdigit() and 1 <= int(n) <= len(CITIES):
            return CITIES[int(n) - 1]
        if n == str(len(CITIES) + 1):
            m = re.findall(r"-?\d+(?:[.,]\d+)?", ask("Широта і довгота, напр. 49.59 34.55:"))
            if len(m) == 2:
                lat, lon = (float(x.replace(",", ".")) for x in m)
                if abs(lat) <= 90 and abs(lon) <= 180:
                    return ("свої координати", lat, lon)
        say("Не зрозумів, спробуй ще.")


def push_city(host, city):
    theme = tempfile.mkdtemp(prefix="sunhome-")
    with open(os.path.join(theme, "config.json"), "w", encoding="utf-8") as f:
        json.dump({"sun_lat": city[1], "sun_lon": city[2]}, f)
    script(host, os.path.join("tools", "theme"), theme)


def main():
    parser = argparse.ArgumentParser(description="Sun Home: встановлення на Android TV")
    parser.add_argument("--host")
    parser.add_argument("--apk")
    parser.add_argument("--skip-debloat", action="store_true")
    args = parser.parse_args()

    bootstrap()
    say("Sun Home — чистий головний екран для Android TV / Google TV.\n"
        "Перед стартом на ТВ: Налаштування → Налаштування пристрою → Про пристрій → 7 разів OK на «Збірка»,\n"
        "потім «Для розробників» → «Налагодження USB» (і «Налагодження по мережі», якщо є).\n")
    host, props = connect(args.host)
    folder = backup(host, props)
    stock = stock_launcher(host)
    restore = write_restore(folder, host, os.path.join(folder, "debloat.txt"), stock)
    packages = [] if args.skip_debloat else choose(host)
    if packages and not yes(f"Вимкнути {len(packages)} вибраних пакетів?"):
        packages = []
    lst = debloat(host, folder, packages, props)

    city = pick_city()
    if not yes("Поставити лаунчер Sun Home головним екраном?"):
        sys.exit("Зупинився перед лаунчером. Вимкнене повертається командою з RESTORE.txt.")
    apk = launcher_apk(args.apk)
    code = script(host, os.path.join("launcher", "build-and-install"), extra_env={"PREBUILT_APK": apk})
    if code != 0:
        sys.exit("Лаунчер не став головним екраном (деталі вище); стоковий лаунчер лишився.")
    push_city(host, city)

    say(f"\nГотово. Місто: {city[0]}. Як усе повернути: {os.path.relpath(restore, ROOT)}")
    say("Коли закінчиш, вимкни на ТВ «Налагодження USB» і «Налагодження по мережі»: порт 5555 не варто "
        "лишати відкритим.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("\nЗупинено.")
