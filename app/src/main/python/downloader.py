"""
وحدة تحميل الفيديوهات عبر yt-dlp.
- تختار الصيغة تلقائيًا حسب توفر ffmpeg حقيقي.
- عند توفر ffmpeg: دمج bestvideo+bestaudio للحصول على أفضل جودة.
- بدون ffmpeg: استخدام صيغة مدمجة (عادة 360p).
"""
import os
import traceback
import yt_dlp


def _is_real_executable(path):
    """
    يتحقق أن الملف تنفيذي حقيقي (وليس ZIP).
    - يتخطى الملفات غير الموجودة أو التي تبدأ بـ PK (ZIP).
    - يتحقق من صلاحية التنفيذ X_OK.
    """
    if not path or not os.path.exists(path):
        return False
    try:
        with open(path, 'rb') as f:
            magic = f.read(2)
        if magic == b'PK':
            return False
        return os.access(path, os.X_OK)
    except Exception:
        return False


def download(url, output_dir, ffmpeg_path, callback=None):
    os.makedirs(output_dir, exist_ok=True)

    def safe_progress(percent, status, message):
        try:
            if callback is not None:
                callback.onProgress(int(percent), str(status), str(message))
        except Exception as e:
            print(f"progress_hook error: {e}")

    def progress_hook(d):
        try:
            status = d.get('status')
            if status == 'downloading':
                total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
                downloaded = d.get('downloaded_bytes', 0)
                percent = int(downloaded * 100 / total) if total > 0 else 0
                safe_progress(percent, 'downloading', '')
            elif status == 'finished':
                safe_progress(100, 'processing', '')
        except Exception as e:
            print(f"progress_hook outer error: {e}")

    ffmpeg_ok = _is_real_executable(ffmpeg_path)

    if ffmpeg_ok:
        format_str = ('bestvideo[ext=mp4][height<=1080]+bestaudio[ext=m4a]'
                      '/bestvideo[height<=1080]+bestaudio/best')
        merge_format = 'mp4'
        print(f"[downloader] FFmpeg OK, using merge strategy: {ffmpeg_path}")
    else:
        format_str = 'best[ext=mp4]/best[ext=webm]/best'
        merge_format = None
        print(f"[downloader] FFmpeg unavailable (path={ffmpeg_path}), "
              f"using pre-merged format (lower quality expected)")

    ydl_opts = {
        'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
        'format': format_str,
        'progress_hooks': [progress_hook],
        'continuedl': True,
        'noplaylist': True,
        'quiet': True,
        'no_warnings': True,
        'retries': 10,
        'fragment_retries': 10,
        'ignoreerrors': False,
        'socket_timeout': 30,
    }

    if merge_format:
        ydl_opts['merge_output_format'] = merge_format
        ydl_opts['ffmpeg_location'] = ffmpeg_path

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            if info is None:
                return {
                    'success': False, 'file_path': '', 'title': '',
                    'duration_ms': 0, 'size_bytes': 0,
                    'error': 'extract_info returned None (unsupported URL?)',
                }

            base_path = ydl.prepare_filename(info)
            file_path = base_path
            if not os.path.exists(file_path):
                for ext in ('.mp4', '.mkv', '.webm', '.m4a', '.mp3'):
                    candidate = os.path.splitext(base_path)[0] + ext
                    if os.path.exists(candidate):
                        file_path = candidate
                        break

            if not os.path.exists(file_path):
                return {
                    'success': False, 'file_path': file_path,
                    'title': info.get('title', 'unknown'),
                    'duration_ms': 0, 'size_bytes': 0,
                    'error': f'File not found after download. Expected near: {base_path}',
                }

            size = os.path.getsize(file_path)
            return {
                'success': True,
                'file_path': file_path,
                'title': info.get('title', 'unknown'),
                'duration_ms': int((info.get('duration') or 0) * 1000),
                'size_bytes': size,
                'error': '',
            }
    except Exception as e:
        tb = traceback.format_exc()
        print(tb)
        return {
            'success': False, 'file_path': '', 'title': '',
            'duration_ms': 0, 'size_bytes': 0,
            'error': f"{type(e).__name__}: {str(e)[:400]}",
        }
