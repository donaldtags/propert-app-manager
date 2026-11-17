"use client";

/**
 * Shrinks photos before they're queued so offline storage and slow connections cope: longest
 * side at most 2048 px, re-encoded as JPEG at 0.8. PDFs and anything that isn't an image pass
 * through untouched. If the browser can't decode the image, the original is kept.
 */
export async function compressForUpload(file: File, maxSide = 2048, quality = 0.8): Promise<{ blob: Blob; name: string }> {
  if (!file.type.startsWith("image/") || typeof createImageBitmap === "undefined") {
    return { blob: file, name: file.name };
  }
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
    const width = Math.round(bitmap.width * scale);
    const height = Math.round(bitmap.height * scale);
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    canvas.getContext("2d")!.drawImage(bitmap, 0, 0, width, height);
    bitmap.close();
    const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, "image/jpeg", quality));
    if (!blob || blob.size >= file.size) return { blob: file, name: file.name };
    return { blob, name: file.name.replace(/\.[^.]+$/, "") + ".jpg" };
  } catch {
    return { blob: file, name: file.name };
  }
}
