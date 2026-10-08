"""
وحدة تحميل الفيديوهات عبر yt-dlp.
- تدعم التحميل متعدد الاتصالات.
- تستخدم FFmpeg المُضمَّن في التطبيق لدمج الصوت والفيديو.
- تمرر التقدم إلى Java عبر callback.
"""
import os
import traceback
import yt_dlp


def download(url, output_dir, ffmpeg_path, callback=None):
    """
    :param url: رابط الفيديو.
    :param output_dir: مجلد الحفظ.
    :param ffmpeg_path: مسار تنفيذي FFmpeg المُضمَّن.
    :param callback: كائن Java فيه onProgress(percent, status, message).
    :return: dict فيه بيانات الفيديو النهائية.
    """
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

    # تمرير مسار FFmpeg المُضمَّن
    if ffmpeg_path and os.path.exists(ffmpeg_path):
        ydl_opts['ffmpeg_location'] = ffmpeg_path
    else:
        return {
            'success': False, 'file_path': '', 'title': '',
            'duration_ms': 0, 'size_bytes': 0,
            'error': f'FFmpeg not found at: {ffmpeg_path}',
        }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            if info is None:
                return {
                    'success': False, 'file_path': '', 'title': '',
                    'duration_ms': 0, 'size_bytes': 0,
                    'error': 'extract_info returned None',
                }
            file_path = ydl.prepare_filename(info)
            if not file_path.endswith('.mp4'):
                file_path = os.path.splitext(file_path)[0] + '.mp4'
            if not os.path.exists(file_path):
                return {
                    'success': False, 'file_path': file_path,
                    'title': info.get('title', 'unknown'),
                    'duration_ms': 0, 'size_bytes': 0,
                    'error': f'File not found after download: {file_path}',
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
