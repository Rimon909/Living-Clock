package com.rimon.livingclock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Imports the user's photos into private storage and decodes them off the UI thread.
 * Each layer is cropped to the exact overscan size the renderer draws, so a frame is a
 * plain blit with no scaling. The background's vignette is baked in here once, which
 * removes a full-screen blend from every frame.
 */
public final class LayerLoader {

    public static final String BACKGROUND_FILE = "layer_background.jpg";
    public static final String FOREGROUND_FILE = "layer_foreground.png";

    /** Border around the screen (fraction of width) that absorbs parallax travel. */
    public static final float OVERSCAN = 0.03f;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface LayersCallback {
        void onLayers(Layers layers);
    }

    public interface ImportCallback {
        void onImported(boolean ok);
    }

    public static final class Layers {
        public final Bitmap background;
        public final Bitmap foreground;
        public final int margin;

        Layers(Bitmap background, Bitmap foreground, int margin) {
            this.background = background;
            this.foreground = foreground;
            this.margin = margin;
        }
    }

    private LayerLoader() {}

    public static void importAsync(Context context, Uri uri, String fileName, ImportCallback callback) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            boolean ok = copy(app, uri, fileName);
            if (ok) {
                Settings.bumpLayersVersion(app);
            }
            MAIN.post(() -> callback.onImported(ok));
        });
    }

    public static void clearAsync(Context context, Runnable onDone) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            new File(app.getFilesDir(), BACKGROUND_FILE).delete();
            new File(app.getFilesDir(), FOREGROUND_FILE).delete();
            Settings.bumpLayersVersion(app);
            MAIN.post(onDone);
        });
    }

    /** Delivers prepared layers on the main thread. The callback receives null if nothing can be drawn. */
    public static void loadAsync(Context context, int width, int height, LayersCallback callback) {
        if (width <= 0 || height <= 0) {
            MAIN.post(() -> callback.onLayers(null));
            return;
        }
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            int margin = Math.round(width * OVERSCAN);
            int pw = width + margin * 2;
            int ph = height + margin * 2;
            Bitmap bg = prepare(new File(app.getFilesDir(), BACKGROUND_FILE), pw, ph, false);
            Bitmap fg = prepare(new File(app.getFilesDir(), FOREGROUND_FILE), pw, ph, true);
            Layers layers = new Layers(bg, fg, margin);
            MAIN.post(() -> callback.onLayers(layers));
        });
    }

    /** Frees layers that were loaded but are no longer needed. */
    public static void recycle(Layers layers) {
        if (layers == null) {
            return;
        }
        if (layers.background != null) {
            layers.background.recycle();
        }
        if (layers.foreground != null) {
            layers.foreground.recycle();
        }
    }

    private static boolean copy(Context app, Uri uri, String fileName) {
        File out = new File(app.getFilesDir(), fileName);
        File tmp = new File(app.getFilesDir(), fileName + ".tmp");
        try (InputStream in = app.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(tmp)) {
            if (in == null) {
                return false;
            }
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                os.write(buffer, 0, n);
            }
        } catch (IOException | SecurityException e) {
            tmp.delete();
            return false;
        }
        return tmp.renameTo(out);
    }

    private static Bitmap prepare(File file, int w, int h, boolean alpha) {
        if (!file.exists()) {
            return null;
        }
        try {
            Bitmap src = decodeScaled(file, w, h, alpha);
            if (src == null) {
                return null;
            }
            Bitmap out = coverFit(src, w, h, alpha);
            src.recycle();
            if (!alpha) {
                applyVignette(out);
            }
            return out;
        } catch (OutOfMemoryError e) {
            // A very large photo on a low-memory phone: skip the layer instead of crashing.
            return null;
        }
    }

    private static Bitmap decodeScaled(File file, int maxW, int maxH, boolean alpha) {
        String path = file.getAbsolutePath();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxW, maxH);
        opts.inPreferredConfig = alpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeFile(path, opts);
        if (bitmap == null) {
            return null;
        }

        int rotation = exifRotation(file);
        if (rotation != 0) {
            Matrix m = new Matrix();
            m.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            bitmap = rotated;
        }
        return bitmap;
    }

    /** Largest power-of-two downsample that still covers the target size. */
    private static int sampleSize(int w, int h, int maxW, int maxH) {
        int s = 1;
        while (w / (s * 2) >= maxW && h / (s * 2) >= maxH) {
            s *= 2;
        }
        return s;
    }

    private static int exifRotation(File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private static Bitmap coverFit(Bitmap src, int w, int h, boolean alpha) {
        Bitmap out = Bitmap.createBitmap(w, h,
                alpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        float dx = (w - src.getWidth() * s) * 0.5f;
        float dy = (h - src.getHeight() * s) * 0.5f;
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate(dx, dy);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
    }

    private static void applyVignette(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = Math.max(w, h) * 0.72f;
        p.setShader(new RadialGradient(w * 0.5f, h * 0.5f, r,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(120, 0, 0, 0)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(bitmap).drawRect(0, 0, w, h, p);
    }
}
