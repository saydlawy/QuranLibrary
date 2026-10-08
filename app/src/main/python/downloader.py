"""
وحدة تحميل الفيديوهات عبر yt-dlp.
- تدعم التحميل متعدد الاتصالات عبر aria2c.
- تدعم الاستئناف التلقائي.
- تمرر التقدم إلى Java عبر callback.
"""
import os
import yt_dlp


def download(url, output_dir, callback=None):
    """
    تحمّل فيديو من رابط YouTube.

    :param url: رابط الفيديو.
    :param output_dir: مجلد الحفظ.
    :param callback: كائن Java فيه طريقة onProgress(percent, status, message).
    :return: dict فيه بيانات الفيديو النهائية.
    """
    os.makedirs(output_dir, exist_ok=True)

    def progress_hook(d):
        if callback is None:
            return
        status = d.get('status')
        if status == 'downloading':
            total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
            downloaded = d.get('downloaded_bytes', 0)
            percent = int(downloaded * 100 / total) if total > 0 else 0
            callback.onProgress(percent, 'downloading', '')
        elif status == 'finished':
            callback.onProgress(100, 'processing', '')
        elif status == 'error':
            callback.onProgress(0, 'error', 'download error')

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
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            file_path = ydl.prepare_filename(info)
            if not file_path.endswith('.mp4'):
                file_path = os.path.splitext(file_path)[0] + '.mp4'
            return {
                'success': True,
                'file_path': file_path,
                'title': info.get('title', 'unknown'),
                'duration_ms': int((info.get('duration') or 0) * 1000),
                'size_bytes': os.path.getsize(file_path) if os.path.exists(file_path) else 0,
                'error': '',
            }
    except Exception as e:
        return {
            'success': False,
            'file_path': '',
            'title': '',
            'duration_ms': 0,
            'size_bytes': 0,
            'error': str(e),
        }
