"""
downloader.py - YouTube video / playlist downloader for Chaquopy (yt-dlp).

Public functions:
    get_info(url, mode="auto")                                  -> JSON string
    download(url, out_dir, ffmpeg_path, quality, mode, cb)      -> JSON string
    cancel()                                                    -> None

quality: 0 = best, 1080 / 720 / 480 / 360 = max height, -1 = audio only (m4a)
mode:    "auto" | "video" | "playlist"
cb:      Java object implementing DownloadCallback (may be None)
"""
import json
import os
import threading
import traceback
from urllib.parse import parse_qs, urlparse

import yt_dlp
from yt_dlp.utils import DownloadCancelled, DownloadError, sanitize_filename

_cancel = threading.Event()

_YT_HOSTS = {
    "youtube.com", "www.youtube.com", "m.youtube.com",
    "music.youtube.com", "youtu.be", "www.youtu.be",
}


# --------------------------------------------------------------------------
# URL handling
# --------------------------------------------------------------------------
def classify_url(url, mode="auto"):
    """Return (kind, clean_url, video_id_or_None). kind is 'video' or 'playlist'."""
    url = (url or "").strip()
    if not url:
        raise ValueError("Empty URL")
    if "://" not in url:
        url = "https://" + url

    p = urlparse(url)
    host = (p.hostname or "").lower()
    if host not in _YT_HOSTS:
        raise ValueError("Not a YouTube link")

    q = parse_qs(p.query)
    parts = [s for s in p.path.split("/") if s]
    plist = (q.get("list") or [None])[0]

    vid = None
    if host.endswith("youtu.be"):
        vid = parts[0] if parts else None
    elif p.path == "/watch":
        vid = (q.get("v") or [None])[0]
    elif len(parts) >= 2 and parts[0] in ("shorts", "live", "embed"):
        vid = parts[1]

    playlist_url = f"https://www.youtube.com/playlist?list={plist}" if plist else None
    video_url = f"https://www.youtube.com/watch?v={vid}" if vid else None

    if mode == "playlist":
        if playlist_url:
            return "playlist", playlist_url, None
        raise ValueError("This link has no playlist")
    if mode == "video":
        if video_url:
            return "video", video_url, vid
        raise ValueError("This link has no single video")

    if p.path == "/playlist" and playlist_url:
        return "playlist", playlist_url, None
    if video_url:
        return "video", video_url, vid
    if playlist_url:
        return "playlist", playlist_url, None
    raise ValueError("Could not find a video or playlist in this link")


# --------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------
def _safe(cb, name, *args):
    if cb is None:
        return
    try:
        getattr(cb, name)(*args)
    except Exception:
        pass


def _fmt_speed(bps):
    if not bps:
        return ""
    for unit in ("B/s", "KB/s", "MB/s", "GB/s"):
        if bps < 1024:
            return f"{bps:.1f} {unit}"
        bps /= 1024
    return f"{bps:.1f} TB/s"


def _fmt_eta(sec):
    if sec is None:
        return ""
    sec = int(sec)
    m, s = divmod(sec, 60)
    h, m = divmod(m, 60)
    return f"{h}:{m:02d}:{s:02d}" if h else f"{m}:{s:02d}"


def _ffmpeg_ok(path):
    return bool(path) and os.path.isfile(path) and os.access(path, os.X_OK)


def _format_selector(quality, has_ffmpeg):
    if quality == -1:
        return "bestaudio[ext=m4a]/bestaudio/best"
    if not has_ffmpeg:
        return "b[ext=mp4]/b"
    h = f"[height<={quality}]" if quality and quality > 0 else ""
    return f"bv*{h}+ba/b{h}"


def _esc(s):
    return s.replace("%", "%%")


def _err(e):
    s = str(e)
    return s.replace("ERROR: ", "").strip() or e.__class__.__name__


# --------------------------------------------------------------------------
# Public API
# --------------------------------------------------------------------------
def cancel():
    _cancel.set()


def get_info(url, mode="auto"):
    """Fast metadata lookup (no download). Returns a JSON string."""
    try:
        kind, clean, vid = classify_url(url, mode)
        opts = {
            "quiet": True, "no_warnings": True, "skip_download": True,
            "extract_flat": "in_playlist", "noplaylist": kind == "video",
            "socket_timeout": 30,
        }
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(clean, download=False)

        if kind == "video":
            entries = [{"index": 1, "id": info.get("id"), "title": info.get("title"),
                        "duration": info.get("duration")}]
            title = info.get("title")
        else:
            entries = []
            for e in info.get("entries") or []:
                if not e or not e.get("id"):
                    continue
                entries.append({"index": len(entries) + 1, "id": e["id"],
                                "title": e.get("title"), "duration": e.get("duration")})
            title = info.get("title")
        return json.dumps({"ok": True, "kind": kind, "title": title,
                           "count": len(entries), "entries": entries}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"ok": False, "error": _err(e)}, ensure_ascii=False)


def download(url, out_dir, ffmpeg_path="", quality=0, mode="auto", cb=None):
    """Download one video or a whole playlist. Returns a JSON string."""
    _cancel.clear()
    result = {"ok": False, "cancelled": False, "kind": None, "folder": out_dir,
              "downloaded": [], "failed": [], "warnings": [], "error": None}
    try:
        kind, clean, vid = classify_url(url, mode)
        result["kind"] = kind
        os.makedirs(out_dir, exist_ok=True)

        has_ffmpeg = _ffmpeg_ok(ffmpeg_path)
        if ffmpeg_path and not has_ffmpeg:
            result["warnings"].append(
                "FFmpeg path is invalid or not executable; downloading a single-file format instead.")
        elif not ffmpeg_path:
            result["warnings"].append("No FFmpeg path given; quality may be limited.")

        if kind == "video":
            items = [{"id": vid, "title": None}]
            folder = out_dir
        else:
            flat_opts = {"quiet": True, "no_warnings": True, "skip_download": True,
                         "extract_flat": "in_playlist", "socket_timeout": 30}
            with yt_dlp.YoutubeDL(flat_opts) as ydl:
                info = ydl.extract_info(clean, download=False)
            items = []
            for e in info.get("entries") or []:
                if not e or not e.get("id"):
                    continue
                t = e.get("title") or ""
                if t in ("[Private video]", "[Deleted video]"):
                    result["failed"].append({"title": t, "error": "Unavailable"})
                    continue
                items.append({"id": e["id"], "title": t})
            name = sanitize_filename(info.get("title") or "playlist").strip() or "playlist"
            folder = os.path.join(out_dir, name)
            os.makedirs(folder, exist_ok=True)

        result["folder"] = folder
        total = len(items)
        if total == 0:
            result["error"] = "No downloadable videos found"
            return json.dumps(result, ensure_ascii=False)

        for idx, item in enumerate(items, start=1):
            if _cancel.is_set():
                break
            title = item["title"] or ""
            state = {"path": None}

            def progress_hook(d, idx=idx, total=total):
                if _cancel.is_set():
                    raise DownloadCancelled()
                if d.get("status") != "downloading":
                    return
                tb = d.get("total_bytes") or d.get("total_bytes_estimate")
                pct = int(d.get("downloaded_bytes", 0) * 100 / tb) if tb else -1
                inf = d.get("info_dict") or {}
                _safe(cb, "onProgress", int(pct), _fmt_speed(d.get("speed")),
                      _fmt_eta(d.get("eta")), inf.get("title") or title, idx, total)

            def pp_hook(d, state=state):
                if d.get("status") == "finished" and d.get("postprocessor") == "MoveFiles":
                    state["path"] = (d.get("info_dict") or {}).get("filepath")

            prefix = f"{idx:03d} - " if kind == "playlist" else ""
            opts = {
                "outtmpl": os.path.join(_esc(folder), prefix + "%(title)s.%(ext)s"),
                "format": _format_selector(quality, has_ffmpeg),
                "noplaylist": True,
                "retries": 5, "fragment_retries": 10, "socket_timeout": 30,
                "continuedl": True, "nooverwrites": True, "trim_file_name": 150,
                "quiet": True, "no_warnings": True, "noprogress": True,
                "progress_hooks": [progress_hook],
                "postprocessor_hooks": [pp_hook],
            }
            if has_ffmpeg:
                opts["ffmpeg_location"] = ffmpeg_path
                if quality != -1:
                    opts["merge_output_format"] = "mp4"

            try:
                with yt_dlp.YoutubeDL(opts) as ydl:
                    ydl.download([f"https://www.youtube.com/watch?v={item['id']}"])
                path = state["path"] or ""
                result["downloaded"].append(path)
                _safe(cb, "onItemFinished", idx, total, title, path)
            except DownloadCancelled:
                break
            except DownloadError as e:
                msg = _err(e)
                result["failed"].append({"title": title or item["id"], "error": msg})
                _safe(cb, "onItemFailed", idx, total, title or item["id"], msg)

        result["cancelled"] = _cancel.is_set()
        result["ok"] = bool(result["downloaded"]) and not result["cancelled"]
        if not result["ok"] and not result["cancelled"] and not result["error"]:
            result["error"] = "No video was downloaded"
        return json.dumps(result, ensure_ascii=False)

    except Exception as e:
        result["error"] = _err(e)
        result["traceback"] = traceback.format_exc()
        return json.dumps(result, ensure_ascii=False)
