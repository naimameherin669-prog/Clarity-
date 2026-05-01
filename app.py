import os
import json
import uuid
from datetime import datetime

from flask import Flask, render_template, request, redirect, url_for, jsonify, send_from_directory, flash
from werkzeug.utils import secure_filename

try:
    from replit import db
except Exception:
    db = None

app = Flask(__name__)
app.secret_key = os.getenv("SECRET_KEY", "change-me")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
UPLOAD_FOLDER = os.path.join(BASE_DIR, "uploads")
MEDIA_FOLDER = os.path.join(UPLOAD_FOLDER, "media")
VAULT_FOLDER = os.path.join(UPLOAD_FOLDER, "vault")
os.makedirs(MEDIA_FOLDER, exist_ok=True)
os.makedirs(VAULT_FOLDER, exist_ok=True)

ALLOWED_MEDIA = {"png", "jpg", "jpeg", "gif", "mp4", "mov", "webm"}
ALLOWED_VAULT = {"pdf", "txt"}


class LocalDB:
    def __init__(self, path="clarity_db.json"):
        self.path = path
        self.data = {}
        self._load()

    def _load(self):
        if os.path.exists(self.path):
            with open(self.path, "r", encoding="utf-8") as f:
                self.data = json.load(f)

    def _save(self):
        with open(self.path, "w", encoding="utf-8") as f:
            json.dump(self.data, f, indent=2)

    def get(self, k, default=None):
        return self.data.get(k, default)

    def __getitem__(self, k):
        return self.data[k]

    def __setitem__(self, k, v):
        self.data[k] = v
        self._save()

    def __contains__(self, k):
        return k in self.data


local_db = LocalDB()
S = db if db is not None else local_db


def get_json(key, default):
    raw = S.get(key, None)
    if raw is None:
        return default
    if isinstance(raw, str):
        try:
            return json.loads(raw)
        except Exception:
            return default
    return raw


def set_json(key, value):
    if db is not None:
        S[key] = json.dumps(value)
    else:
        S[key] = value


def now_str():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def init_db():
    if "total_score" not in S:
        S["total_score"] = 0
    if "activity_logs" not in S:
        set_json("activity_logs", [])
    if "media_items" not in S:
        set_json("media_items", [])
    if "vault_items" not in S:
        set_json("vault_items", [])
    if "settings" not in S:
        set_json("settings", {
            "ai_engine": "Gemini",
            "api_keys": {
                "Gemini": "",
                "Perplexity": "",
                "Kimi": "",
                "Gauth AI": "",
                "Studly": ""
            },
            "interventions": {
                "Instagram": "Stop scrolling, you are killing your dreams!",
                "TikTok": "You are trading focus for dopamine.",
                "YouTube": "Close the feed and return to your goals."
            }
        })
    if "alarm_settings" not in S:
        set_json("alarm_settings", {
            "time": "",
            "message": "",
            "sequence": "Both",
            "music_link": ""
        })


def allowed_file(filename, allowed):
    return "." in filename and filename.rsplit(".", 1)[1].lower() in allowed


def analyze_topic(filename, text, engine):
    source = f"{filename} {text}".lower()
    topic = "General Study Material"
    if "math" in source:
        topic = "Math"
    elif "physics" in source:
        topic = "Physics"
    elif "chem" in source:
        topic = "Chemistry"
    elif "bio" in source:
        topic = "Biology"
    elif "history" in source:
        topic = "History"
    elif "english" in source or "essay" in source:
        topic = "English"
    elif "python" in source or "code" in source or "programming" in source:
        topic = "Programming"

    methods = {
        "Math": "Practice problems + Active Recall",
        "Physics": "Feynman Technique + problem sets",
        "Chemistry": "Spaced repetition + concept maps",
        "Biology": "Active Recall + diagrams",
        "History": "Story-based summarization + flashcards",
        "English": "Practice writing + self-explanation",
        "Programming": "Build projects + deliberate practice",
        "General Study Material": "Active Recall + spaced repetition"
    }

    tools = {
        "Math": ["Khan Academy", "Symbolab", "Photomath"],
        "Physics": ["Khan Academy", "PhET", "Wolfram Alpha"],
        "Chemistry": ["Khan Academy", "ChemLibreTexts", "Wolfram Alpha"],
        "Biology": ["Khan Academy", "Quizlet", "Anki"],
        "History": ["Perplexity", "Notion AI", "Quizlet"],
        "English": ["Grammarly", "QuillBot", "Notion AI"],
        "Programming": ["freeCodeCamp", "LeetCode", "GitHub Copilot"],
        "General Study Material": ["Perplexity", "Quizlet", "Anki"]
    }

    base = {
        "Math": 45, "Physics": 40, "Chemistry": 35, "Biology": 30,
        "History": 25, "English": 20, "Programming": 50, "General Study Material": 30
    }

    bias = {"Gemini": 0.95, "Perplexity": 0.9, "Kimi": 1.05, "Gauth AI": 0.85, "Studly": 0.92}
    return {
        "topic": topic,
        "estimated_learning_time_hours": int(base[topic] * bias.get(engine, 1.0)),
        "best_method": methods[topic],
        "free_tools": tools[topic]
    }


@app.route("/")
def index():
    logs = list(reversed(get_json("activity_logs", [])[-50:]))
    return render_template("index.html", total_score=int(S.get("total_score", 0)), logs=logs)


@app.route("/log", methods=["GET", "POST"])
def log_activity():
    if request.method == "POST":
        activity = request.form.get("activity", "").strip()
        impact = max(-10, min(10, int(request.form.get("impact", "0"))))
        S["total_score"] = int(S.get("total_score", 0)) + impact
        logs = get_json("activity_logs", [])
        logs.append({
            "time": now_str(),
            "activity": activity,
            "impact": impact,
            "kind": "Growth" if impact >= 0 else "Downfall"
        })
        set_json("activity_logs", logs)
        flash("Activity saved.")
        return redirect(url_for("index"))
    return render_template("log.html")


@app.route("/webhook", methods=["POST"])
def webhook():
    data = request.get_json(silent=True) or {}
    app_name = str(data.get("app", "")).strip()

    settings = get_json("settings", {})
    interventions = settings.get("interventions", {})
    distraction_apps = {"instagram", "tiktok", "youtube", "facebook", "reddit", "snapchat", "x"}

    matched = next((k for k in interventions if k.lower() == app_name.lower()), None)
    message = interventions.get(matched, "Stay focused.") if matched else "Stay focused."

    delta = -1 if app_name.lower() in distraction_apps else 1
    S["total_score"] = int(S.get("total_score", 0)) + delta

    logs = get_json("activity_logs", [])
    logs.append({
        "time": now_str(),
        "activity": f"Webhook: {app_name}",
        "impact": delta,
        "kind": "Downfall" if delta < 0 else "Growth"
    })
    set_json("activity_logs", logs)

    return jsonify({
        "app": app_name,
        "impact": delta,
        "message": message,
        "total_score": int(S.get("total_score", 0))
    })


@app.route("/media", methods=["GET", "POST"])
def media():
    if request.method == "POST":
        file = request.files.get("file")
        if not file or file.filename == "":
            flash("No file selected.")
            return redirect(url_for("media"))
        if not allowed_file(file.filename, ALLOWED_MEDIA):
            flash("Only images and videos are allowed.")
            return redirect(url_for("media"))

        original = secure_filename(file.filename)
        ext = original.rsplit(".", 1)[1].lower()
        unique = f"{uuid.uuid4().hex}.{ext}"
        file.save(os.path.join(MEDIA_FOLDER, unique))

        items = get_json("media_items", [])
        items.append({
            "time": now_str(),
            "filename": unique,
            "original": original,
            "url": url_for("uploaded_media", filename=unique)
        })
        set_json("media_items", items)
        flash("Media uploaded.")
        return redirect(url_for("media"))

    return render_template("media.html", items=list(reversed(get_json("media_items", [])[-50:])))


@app.route("/vault", methods=["GET", "POST"])
def vault():
    settings = get_json("settings", {})
    engine = settings.get("ai_engine", "Gemini")
    if request.method == "POST":
        file = request.files.get("file")
        if not file or file.filename == "":
            flash("No file selected.")
            return redirect(url_for("vault"))
        if not allowed_file(file.filename, ALLOWED_VAULT):
            flash("Only PDF and TXT files are allowed.")
            return redirect(url_for("vault"))

        original = secure_filename(file.filename)
        ext = original.rsplit(".", 1)[1].lower()
        unique = f"{uuid.uuid4().hex}.{ext}"
        path = os.path.join(VAULT_FOLDER, unique)
        file.save(path)

        text = ""
        if ext == "txt":
            with open(path, "r", encoding="utf-8", errors="ignore") as f:
                text = f.read()[:10000]

        analysis = analyze_topic(original, text, engine)
        items = get_json("vault_items", [])
        items.append({
            "time": now_str(),
            "filename": unique,
            "original": original,
            "engine": engine,
            "analysis": analysis,
            "url": url_for("uploaded_vault", filename=unique)
        })
        set_json("vault_items", items)
        flash("File analyzed.")
        return redirect(url_for("vault"))

    return render_template("vault.html", items=list(reversed(get_json("vault_items", [])[-50:])), engine=engine)


@app.route("/alarms", methods=["GET", "POST"])
def alarms():
    if request.method == "POST":
        set_json("alarm_settings", {
            "time": request.form.get("time", ""),
            "message": request.form.get("message", ""),
            "sequence": request.form.get("sequence", "Both"),
            "music_link": request.form.get("music_link", "")
        })
        flash("Alarm saved.")
        return redirect(url_for("alarms"))
    return render_template("alarms.html", alarm_settings=get_json("alarm_settings", {}))


@app.route("/get_alarm")
def get_alarm():
    a = get_json("alarm_settings", {})
    return jsonify({
        "message": a.get("message", ""),
        "sequence": a.get("sequence", ""),
        "music_link": a.get("music_link", ""),
        "time": a.get("time", "")
    })


@app.route("/settings", methods=["GET", "POST"])
def settings():
    settings = get_json("settings", {})
    if request.method == "POST":
        settings["ai_engine"] = request.form.get("ai_engine", "Gemini")
        settings["api_keys"] = {
            "Gemini": request.form.get("api_gemini", ""),
            "Perplexity": request.form.get("api_perplexity", ""),
            "Kimi": request.form.get("api_kimi", ""),
            "Gauth AI": request.form.get("api_gauth", ""),
            "Studly": request.form.get("api_studly", "")
        }
        settings["interventions"] = {
            "Instagram": request.form.get("msg_Instagram", ""),
            "TikTok": request.form.get("msg_TikTok", ""),
            "YouTube": request.form.get("msg_YouTube", "")
        }
        set_json("settings", settings)
        flash("Settings saved.")
        return redirect(url_for("settings"))
    return render_template("settings.html", settings=settings)


@app.route("/uploads/media/<filename>")
def uploaded_media(filename):
    return send_from_directory(MEDIA_FOLDER, filename)


@app.route("/uploads/vault/<filename>")
def uploaded_vault(filename):
    return send_from_directory(VAULT_FOLDER, filename)


@app.errorhandler(413)
def too_large(_):
    return "File too large", 413


init_db()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "8080")), debug=True)