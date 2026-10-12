import io
import mimetypes
import os
import re
import shutil
import tempfile
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import AsyncGenerator, Dict, List, Optional, Tuple
import aiofiles
from fastapi import HTTPException, UploadFile

from config import CHUNK_SIZE, SHARE_DIR

try:
    import magic
    _magic_detector = magic.Magic(mime=True)
except Exception:
    _magic_detector = None

try:
    from PIL import Image
    _pillow_available = True
except Exception:
    _pillow_available = False

THUMBNAILS_DIR = SHARE_DIR / ".thumbnails"
THUMBNAILS_DIR.mkdir(parents=True, exist_ok=True)


def sanitize_filename(filename: str) -> str:
    """Ensure safe filename to prevent directory traversal attacks and invalid OS characters."""
    clean_name = Path(filename).name
    # Strip forbidden characters across Windows/Linux (e.g. < > : " / \ | ? *)
    clean_name = re.sub(r'[<>:"/\\|?*\x00-\x1f]', '_', clean_name)
    if not clean_name or clean_name in (".", "..", ".thumbnails"):
        raise HTTPException(status_code=400, detail="Invalid filename")
    return clean_name


def get_mime_type(file_path: Path) -> str:
    """Detect MIME type using python-magic or fallback to mimetypes."""
    if _magic_detector and file_path.exists():
        try:
            return _magic_detector.from_file(str(file_path))
        except Exception:
            pass
    mime, _ = mimetypes.guess_type(str(file_path))
    return mime or "application/octet-stream"


def get_file_category(mime_type: str, filename: str) -> str:
    """Categorize file into image, video, audio, document, archive, or other."""
    ext = Path(filename).suffix.lower()
    if mime_type.startswith("image/") or ext in (".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg", ".bmp"):
        return "image"
    if mime_type.startswith("video/") or ext in (".mp4", ".webm", ".mov", ".mkv", ".avi"):
        return "video"
    if mime_type.startswith("audio/") or ext in (".mp3", ".wav", ".flac", ".m4a", ".ogg", ".aac"):
        return "audio"
    if ext in (".zip", ".tar", ".gz", ".7z", ".rar", ".bz2", ".tgz"):
        return "archive"
    if ext in (".pdf", ".txt", ".md", ".doc", ".docx", ".csv", ".json", ".py", ".js", ".html", ".css", ".sh", ".xml", ".log", ".ts"):
        return "document"
    return "other"


def get_disk_stats() -> Dict:
    """Get system disk usage for the storage partition."""
    total, used, free = shutil.disk_usage(str(SHARE_DIR))
    percent = round((used / total) * 100, 1) if total > 0 else 0
    return {
        "total": total,
        "used": used,
        "free": free,
        "percent_used": percent,
    }


async def list_shared_files() -> List[Dict]:
    """List all files in the shared directory with metadata and category."""
    files = []
    if not SHARE_DIR.exists():
        return files

    for entry in sorted(SHARE_DIR.iterdir(), key=lambda p: p.stat().st_mtime if p.is_file() else 0, reverse=True):
        if entry.is_file() and not entry.name.startswith("."):
            stat = entry.stat()
            modified_dt = datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc)
            mime = get_mime_type(entry)
            cat = get_file_category(mime, entry.name)
            files.append({
                "name": entry.name,
                "size": stat.st_size,
                "modified": modified_dt.isoformat(),
                "mime_type": mime,
                "category": cat,
                "has_thumbnail": cat == "image",
            })
    return files


def get_or_create_thumbnail(filename: str, max_size=(360, 360)) -> Optional[Path]:
    """Generate or retrieve a cached thumbnail for image files."""
    if not _pillow_available:
        return None

    src_path = get_file_path(filename)
    mime = get_mime_type(src_path)
    if not mime.startswith("image/"):
        return None

    thumb_name = f"{src_path.stem}_{src_path.stat().st_mtime_ns}.webp"
    thumb_path = THUMBNAILS_DIR / thumb_name

    if thumb_path.exists():
        return thumb_path

    try:
        with Image.open(src_path) as img:
            img.thumbnail(max_size)
            # Convert RGBA to RGB if saving as WebP without alpha needs
            if img.mode in ("RGBA", "LA") and img.format != "WEBP":
                thumb_path_png = THUMBNAILS_DIR / f"{src_path.stem}_{src_path.stat().st_mtime_ns}.png"
                img.save(thumb_path_png, "PNG", optimize=True)
                return thumb_path_png

            img.save(thumb_path, "WEBP", quality=80)
            return thumb_path
    except Exception:
        return None


async def save_uploaded_file(upload_file: UploadFile) -> Tuple[str, int]:
    """
    Stream uploaded file to disk in 1MB chunks asynchronously to avoid RAM bloat.
    Returns (saved_filename, total_bytes_saved).
    """
    safe_name = sanitize_filename(upload_file.filename or "uploaded_file")
    target_path = SHARE_DIR / safe_name

    stem = target_path.stem
    suffix = target_path.suffix
    counter = 1
    while target_path.exists():
        target_path = SHARE_DIR / f"{stem}_{counter}{suffix}"
        counter += 1

    total_bytes = 0
    async with aiofiles.open(target_path, "wb") as out_file:
        while True:
            chunk = await upload_file.read(CHUNK_SIZE)
            if not chunk:
                break
            await out_file.write(chunk)
            total_bytes += len(chunk)

    # Pre-generate thumbnail if it is an image
    if get_file_category(get_mime_type(target_path), target_path.name) == "image":
        try:
            get_or_create_thumbnail(target_path.name)
        except Exception:
            pass

    return target_path.name, total_bytes


def get_file_path(filename: str) -> Path:
    """Retrieve absolute path for a filename in SHARE_DIR and check existence."""
    safe_name = sanitize_filename(filename)
    path = SHARE_DIR / safe_name
    if not path.is_file():
        raise HTTPException(status_code=404, detail="File not found")
    return path


async def delete_file(filename: str) -> bool:
    """Delete a file from the shared directory and clean up thumbnails."""
    path = get_file_path(filename)
    try:
        path.unlink()
        # Clean any associated thumbnail
        for thumb in THUMBNAILS_DIR.glob(f"{path.stem}_*"):
            try:
                thumb.unlink()
            except Exception:
                pass
        return True
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete file: {e}")


async def delete_files_batch(filenames: List[str]) -> List[str]:
    """Delete multiple files in batch."""
    deleted = []
    for fn in filenames:
        try:
            await delete_file(fn)
            deleted.append(fn)
        except Exception:
            pass
    return deleted


def create_batch_zip(filenames: List[str]) -> Path:
    """Package selected files into a temporary zip archive for batch download."""
    temp_zip = tempfile.NamedTemporaryFile(delete=False, suffix=".zip", prefix="wifishare_batch_")
    with zipfile.ZipFile(temp_zip.name, "w", zipfile.ZIP_DEFLATED) as zip_out:
        for fn in filenames:
            try:
                path = get_file_path(fn)
                zip_out.write(path, arcname=path.name)
            except Exception:
                pass
    return Path(temp_zip.name)


async def file_chunk_generator(
    path: Path,
    start: int = 0,
    length: Optional[int] = None,
    chunk_size: int = CHUNK_SIZE,
) -> AsyncGenerator[bytes, None]:
    """
    Stream file contents in async chunks, supporting byte offsets for HTTP Range.
    """
    async with aiofiles.open(path, mode="rb") as f:
        if start > 0:
            await f.seek(start)
        remaining = length if length is not None else -1
        while remaining != 0:
            bytes_to_read = chunk_size if remaining < 0 else min(chunk_size, remaining)
            chunk = await f.read(bytes_to_read)
            if not chunk:
                break
            if remaining > 0:
                remaining -= len(chunk)
            yield chunk
