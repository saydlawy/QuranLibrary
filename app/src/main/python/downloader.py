"""
وحدة تحميل الفيديوهات عبر yt-dlp.
- progress_hook محصّن ضد الأخطاء لضمان استمرار التحميل.
- تسجيل تفصيلي للأخطاء.
"""
import os
import traceback
import yt_dlp


def download(url, output_dir, callback=None):
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

    ydl_opts = {
        'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
        'format': 'bestvideo[ext=mp4][height<=1080]+bestaudio[ext=m4a]/best[ext=mp4]/best',
        'merge_output_format': 'mp4',
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

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            if info is None:
                return {
                    'success': False, 'file_path': '', 'title': '',
                    'duration_ms': 0, 'size_bytes': 0,
                    'error': 'extract_info returned None (possibly a playlist or unsupported URL)',
                }
            file_path = ydl.prepare_filename(info)
            if not file_path.endswith('.mp4'):
                file_path = os.path.splitext(file_path)[0] + '.mp4'
            size = os.path.getsize(file_path) if os.path.exists(file_path) else 0
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
            'error': f"{type(e).__name__}: {str(e)[:300]}",
        }
