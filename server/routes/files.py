import os
import re
from typing import List
from fastapi import APIRouter, BackgroundTasks, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse, Response, StreamingResponse
from pydantic import BaseModel

from routes.ws import ws_manager
from services.file_manager import (
    create_batch_zip,
    delete_file,
    delete_files_batch,
    file_chunk_generator,
    get_disk_stats,
    get_file_path,
    get_mime_type,
    get_or_create_thumbnail,
    list_shared_files,
    save_uploaded_file,
)

router = APIRouter(tags=["files"])


class BatchFilesPayload(BaseModel):
    filenames: List[str]


@router.get("/files")
async def get_files():
    """List all shared files with metadata, categories, and thumbnail flags."""
    files = await list_shared_files()
    return files


@router.get("/system/stats")
async def get_stats():
    """Retrieve disk storage statistics for the Ubuntu shared directory."""
    return get_disk_stats()


@router.post("/upload")
async def upload_files(files: List[UploadFile]):
    """Accept multiple uploaded files, stream to disk in chunks, and notify clients."""
    if not files:
        raise HTTPException(status_code=400, detail="No files provided")

    uploaded = []
    for file in files:
        saved_name, total_size = await save_uploaded_file(file)
        uploaded.append({"name": saved_name, "size": total_size})

        # Broadcast live event
        await ws_manager.broadcast({
            "event": "file_added",
            "file": {"name": saved_name, "size": total_size},
        })

    return {"ok": True, "files": uploaded}


@router.get("/preview/{filename}")
async def preview_file(filename: str, request: Request):
    """
    Stream file with Content-Disposition: inline.
    Allows in-browser video playback, audio listening, image viewing, and text preview.
    Supports HTTP Range requests for seeking in videos.
    """
    path = get_file_path(filename)
    file_size = path.stat().st_size
    mime = get_mime_type(path)

    range_header = request.headers.get("range")
    if range_header:
        match = re.match(r"^bytes=(\d+)-(\d+)?$", range_header.strip())
        if match:
            start = int(match.group(1))
            end_val = match.group(2)
            end = int(end_val) if end_val else file_size - 1

            if start >= file_size:
                raise HTTPException(
                    status_code=416,
                    detail="Requested Range Not Satisfiable",
                    headers={"Content-Range": f"bytes */{file_size}"},
                )

            end = min(end, file_size - 1)
            content_length = (end - start) + 1

            headers = {
                "Content-Range": f"bytes {start}-{end}/{file_size}",
                "Accept-Ranges": "bytes",
                "Content-Length": str(content_length),
                "Content-Disposition": f'inline; filename="{path.name}"',
            }

            return StreamingResponse(
                file_chunk_generator(path, start=start, length=content_length),
                status_code=206,
                headers=headers,
                media_type=mime,
            )

    headers = {
        "Content-Length": str(file_size),
        "Accept-Ranges": "bytes",
        "Content-Disposition": f'inline; filename="{path.name}"',
    }

    return StreamingResponse(
        file_chunk_generator(path, start=0, length=file_size),
        status_code=200,
        headers=headers,
        media_type=mime,
    )


@router.get("/thumbnail/{filename}")
async def get_thumbnail(filename: str):
    """
    Serve lightweight cached thumbnail for gallery and feed preview.
    Falls back to inline original image if thumbnailing is unavailable.
    """
    thumb_path = get_or_create_thumbnail(filename)
    if thumb_path and thumb_path.exists():
        return FileResponse(
            thumb_path,
            headers={"Cache-Control": "public, max-age=86400"},
            media_type="image/webp" if thumb_path.suffix == ".webp" else "image/png",
        )

    # Fallback to serving the original file directly
    orig_path = get_file_path(filename)
    mime = get_mime_type(orig_path)
    return FileResponse(
        orig_path,
        headers={"Cache-Control": "public, max-age=3600"},
        media_type=mime,
    )


@router.get("/download/{filename}")
async def download_file(filename: str, request: Request):
    """
    Stream download requested file as an attachment.
    Supports HTTP Range requests for interrupted transfer resumption.
    """
    path = get_file_path(filename)
    file_size = path.stat().st_size
    mime = get_mime_type(path)

    range_header = request.headers.get("range")
    if range_header:
        match = re.match(r"^bytes=(\d+)-(\d+)?$", range_header.strip())
        if match:
            start = int(match.group(1))
            end_val = match.group(2)
            end = int(end_val) if end_val else file_size - 1

            if start >= file_size:
                raise HTTPException(
                    status_code=416,
                    detail="Requested Range Not Satisfiable",
                    headers={"Content-Range": f"bytes */{file_size}"},
                )

            end = min(end, file_size - 1)
            content_length = (end - start) + 1

            headers = {
                "Content-Range": f"bytes {start}-{end}/{file_size}",
                "Accept-Ranges": "bytes",
                "Content-Length": str(content_length),
                "Content-Disposition": f'attachment; filename="{path.name}"',
            }

            return StreamingResponse(
                file_chunk_generator(path, start=start, length=content_length),
                status_code=206,
                headers=headers,
                media_type=mime,
            )

    headers = {
        "Content-Length": str(file_size),
        "Accept-Ranges": "bytes",
        "Content-Disposition": f'attachment; filename="{path.name}"',
    }

    return StreamingResponse(
        file_chunk_generator(path, start=0, length=file_size),
        status_code=200,
        headers=headers,
        media_type=mime,
    )


@router.post("/download/zip")
async def download_batch_zip(payload: BatchFilesPayload, background_tasks: BackgroundTasks):
    """Bundle selected files into a zip archive and stream as attachment."""
    if not payload.filenames:
        raise HTTPException(status_code=400, detail="No files selected")

    zip_path = create_batch_zip(payload.filenames)

    # Clean up temporary zip file after response completes
    def cleanup():
        try:
            if zip_path.exists():
                zip_path.unlink()
        except Exception:
            pass

    background_tasks.add_task(cleanup)

    return FileResponse(
        zip_path,
        media_type="application/zip",
        filename="wifishare_download.zip",
        headers={"Content-Disposition": 'attachment; filename="wifishare_download.zip"'},
    )


@router.delete("/files/{filename}")
async def remove_file(filename: str):
    """Delete a shared file and broadcast event."""
    await delete_file(filename)
    await ws_manager.broadcast({
        "event": "file_deleted",
        "file": {"name": filename},
    })
    return {"ok": True}


@router.post("/files/batch-delete")
async def batch_delete_files(payload: BatchFilesPayload):
    """Delete multiple files in a single request."""
    if not payload.filenames:
        raise HTTPException(status_code=400, detail="No files specified")

    deleted = await delete_files_batch(payload.filenames)
    for fn in deleted:
        await ws_manager.broadcast({
            "event": "file_deleted",
            "file": {"name": fn},
        })
    return {"ok": True, "deleted": deleted}
