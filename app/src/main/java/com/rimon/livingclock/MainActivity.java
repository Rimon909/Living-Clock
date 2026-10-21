package com.rimon.livingclock;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_BACKGROUND = 101;
    private static final int REQ_SUBJECT = 102;

    private final int backgroundColor = Color.rgb(10, 14, 22);
    private final int cardColor = Color.rgb(24, 30, 42);
    private final int cardStroke = Color.rgb(44, 53, 69);
    private final int accentColor = Color.rgb(225, 48, 65);
    private final int secondaryText = Color.rgb(157, 169, 187);

    private LivingPreview preview;
    private TextView depthLabel;
    private Button tiltButton;
    private Button sweepButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(backgroundColor);
        getWindow().setNavigationBarColor(backgroundColor);
        buildInterface();
    }

    @Override
    protected void onResume() {
        super.onResume();
        preview.setActive(true);
        refreshControls();
    }

    @Override
    protected void onPause() {
        preview.setActive(false);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_BACKGROUND) {
            importLayer(uri, LayerLoader.BACKGROUND_FILE, "Background set.");
        } else if (requestCode == REQ_SUBJECT) {
            importLayer(uri, LayerLoader.FOREGROUND_FILE, "Subject layer set.");
        }
    }

    private void importLayer(Uri uri, String fileName, String successMessage) {
        LayerLoader.importAsync(this, uri, fileName, ok -> {
            if (ok) {
                preview.reloadLayers();
                toast(successMessage);
            } else {
                toast("Couldn't import that photo.");
            }
        });
    }

    // ---------- layout ----------

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(backgroundColor);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(28));
        scroll.addView(root);
        setContentView(scroll);

        addHeader(root);
        addPreview(root);
        addSectionTitle(root, "YOUR STUDIO", "Make time personal.");
        addPhotoCard(root);
        addMotionCard(root);
        addDesignCard(root);
        addApplyButton(root);
        addFooter(root);
    }

    private void addHeader(LinearLayout root) {
        LinearLayout header = row();

        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);

        TextView eyebrow = text("PERSONAL WALLPAPER STUDIO", 10, Color.rgb(230, 92, 105));
        eyebrow.setLetterSpacing(0.12f);

        TextView title = text("LIVING CLOCK", 25, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setLetterSpacing(0.035f);

        TextView subtitle = text("TIME, WITH DEPTH.", 11, secondaryText);
        subtitle.setLetterSpacing(0.17f);

        titleGroup.addView(eyebrow);
        titleGroup.addView(title);
        titleGroup.addView(subtitle);
        header.addView(titleGroup, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView badge = text("BETA", 10, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(8), dp(12), dp(8));
        badge.setBackground(rounded(Color.rgb(45, 30, 43), Color.rgb(105, 51, 69), 18));
        header.addView(badge);
        root.addView(header);

        root.addView(new View(this), new LinearLayout.LayoutParams(1, dp(25)));
    }

    private void addPreview(LinearLayout root) {
        LinearLayout card = panel();

        LinearLayout header = row();
        TextView title = text("LIVE PREVIEW", 11, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setLetterSpacing(0.12f);
        TextView status = text("●  LIVE", 9, Color.rgb(244, 111, 124));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(status);
        card.addView(header);

        preview = new LivingPreview(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(300));
        previewParams.topMargin = dp(8);
        card.addView(preview, previewParams);

        TextView caption = text("Tilt your phone to see the layers move.", 11, secondaryText);
        caption.setGravity(Gravity.CENTER);
        card.addView(caption);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(30);
        root.addView(card, cardParams);
    }

    private void addSectionTitle(LinearLayout root, String title, String subtitle) {
        TextView heading = text(title, 12, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setLetterSpacing(0.14f);
        root.addView(heading);

        TextView sub = text(subtitle, 12, secondaryText);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(5);
        params.bottomMargin = dp(15);
        root.addView(sub, params);
    }

    private void addPhotoCard(LinearLayout root) {
        LinearLayout card = featureCard(root, "01", "Photo Studio",
                "Pick a portrait for the background, then add a transparent PNG of the subject. "
                        + "The clock sits between them.");

        LinearLayout buttons = buttonRow(card);
        actionButton(buttons, "BACKGROUND", this::chooseBackground);
        actionButton(buttons, "SUBJECT PNG", this::chooseSubject);

        TextView clear = text("Remove both photos", 11, secondaryText);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(0, dp(12), 0, 0);
        clear.setOnClickListener(v -> clearLayers());
        card.addView(clear, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addMotionCard(LinearLayout root) {
        LinearLayout card = featureCard(root, "02", "Depth & Motion",
                "Tilt the phone or swipe home screens and the layers shift at different speeds. "
                        + "When the phone is still, nothing redraws.");

        depthLabel = text("", 11, secondaryText);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(14);
        card.addView(depthLabel, labelParams);

        SeekBar depth = new SeekBar(this);
        depth.setMax(100);
        depth.setProgress(Settings.depthPercent(this));
        depth.setProgressTintList(ColorStateList.valueOf(accentColor));
        depth.setThumbTintList(ColorStateList.valueOf(accentColor));
        depth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateDepthLabel(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                Settings.prefs(MainActivity.this).edit()
                        .putInt(Settings.KEY_DEPTH, bar.getProgress()).apply();
                preview.applySettings();
            }
        });
        card.addView(depth, new LinearLayout.LayoutParams(-1, -2));

        tiltButton = actionButton(buttonRow(card), "", this::toggleTilt);
    }

    private void addDesignCard(LinearLayout root) {
        LinearLayout card = featureCard(root, "03", "Clock Design",
                "A glass face with red-and-black hands and a soft pulse on each second. "
                        + "Efficient ticks once a second and sleeps between ticks. "
                        + "Balanced sweeps at 30 fps. Ultra sweeps at 60 fps.");

        sweepButton = actionButton(buttonRow(card), "", this::cycleSweep);
    }

    private void addApplyButton(LinearLayout root) {
        Button apply = new Button(this);
        apply.setText("APPLY WALLPAPER   →");
        apply.setTextColor(Color.WHITE);
        apply.setTextSize(13);
        apply.setTypeface(null, Typeface.BOLD);
        apply.setAllCaps(false);
        apply.setLetterSpacing(0.04f);
        apply.setElevation(dp(5));
        apply.setBackground(rounded(accentColor, Color.rgb(255, 111, 126), 17));
        apply.setOnClickListener(v -> openWallpaperPicker());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(56));
        params.topMargin = dp(16);
        params.bottomMargin = dp(16);
        root.addView(apply, params);
    }

    private void addFooter(LinearLayout root) {
        TextView footer = text("DESIGNED FOR YOUR SCREEN  •  BUILT WITH CARE", 9,
                Color.rgb(104, 117, 137));
        footer.setGravity(Gravity.CENTER);
        footer.setLetterSpacing(0.07f);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(5);
        root.addView(footer, params);
    }

    /** A card with a number, title and description, added to root. Buttons go in afterwards. */
    private LinearLayout featureCard(LinearLayout root, String number, String title, String description) {
        LinearLayout card = panel();

        LinearLayout top = row();
        TextView index = text(number, 11, accentColor);
        index.setTypeface(null, Typeface.BOLD);
        TextView heading = text("  " + title, 17, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        top.addView(index);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(top);

        TextView detail = text(description, 12, secondaryText);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(7);
        card.addView(detail, detailParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(12);
        root.addView(card, cardParams);
        return card;
    }

    private LinearLayout buttonRow(LinearLayout card) {
        LinearLayout buttons = row();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(13);
        card.addView(buttons, params);
        return buttons;
    }

    private Button actionButton(LinearLayout row, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(10);
        b.setTextColor(Color.WHITE);
        b.setLetterSpacing(0.06f);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setBackground(rounded(Color.rgb(37, 44, 59), Color.rgb(69, 80, 101), 12));
        b.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        if (row.getChildCount() > 0) {
            params.leftMargin = dp(8);
        }
        row.addView(b, params);
        return b;
    }

    // ---------- actions ----------

    private void chooseBackground() {
        pickImage(REQ_BACKGROUND);
    }

    private void chooseSubject() {
        pickImage(REQ_SUBJECT);
    }

    private void pickImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            toast("No photo app available.");
        }
    }

    private void clearLayers() {
        LayerLoader.clearAsync(this, () -> {
            preview.reloadLayers();
            toast("Photos removed.");
        });
    }

    private void toggleTilt() {
        boolean on = !Settings.motionEnabled(this);
        Settings.prefs(this).edit().putBoolean(Settings.KEY_MOTION, on).apply();
        preview.applySettings();
        refreshControls();
    }

    private void cycleSweep() {
        int next = (Settings.sweepMode(this) + 1) % 3;
        Settings.prefs(this).edit().putInt(Settings.KEY_SWEEP, next).apply();
        preview.applySettings();
        refreshControls();
    }

    private void refreshControls() {
        tiltButton.setText(Settings.motionEnabled(this) ? "TILT PARALLAX: ON" : "TILT PARALLAX: OFF");
        sweepButton.setText(sweepLabel(Settings.sweepMode(this)));
        updateDepthLabel(Settings.depthPercent(this));
    }

    private void updateDepthLabel(int percent) {
        depthLabel.setText("DEPTH  " + percent + "%");
    }

    private String sweepLabel(int mode) {
        switch (mode) {
            case Settings.SWEEP_EFFICIENT:
                return "SECOND HAND · EFFICIENT";
            case Settings.SWEEP_ULTRA:
                return "SECOND HAND · ULTRA 60 FPS";
            default:
                return "SECOND HAND · BALANCED 30 FPS";
        }
    }

    private void openWallpaperPicker() {
        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, LivingClockService.class));
            startActivity(intent);
        } catch (Exception e) {
            toast("Couldn't open the wallpaper preview.");
        }
    }

    // ---------- view helpers ----------

    private LinearLayout panel() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(15), dp(17), dp(15));
        card.setBackground(rounded(cardColor, cardStroke, 20));
        return card;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ---------- live preview ----------

    /**
     * The studio preview uses the same ClockRenderer as the wallpaper, so what you see is what
     * you get. It animates only while the app is open and follows the phone's tilt.
     */
    private static final class LivingPreview extends View implements TiltSensor.Listener {

        private final ClockRenderer renderer = new ClockRenderer();
        private final TiltSensor tilt;
        private boolean active;
        private boolean motion;
        private int sweepMode;
        private int loadGeneration;

        LivingPreview(Context context) {
            super(context);
            tilt = new TiltSensor(context, this, false);
            applySettings();
        }

        void applySettings() {
            Context c = getContext();
            motion = Settings.motionEnabled(c);
            sweepMode = Settings.sweepMode(c);
            renderer.setSweepMode(sweepMode);
            renderer.setDepthPercent(Settings.depthPercent(c));
            if (active && motion) {
                tilt.start();
            } else {
                tilt.stop();
            }
            if (!motion) {
                renderer.setTarget(0f, 0f);
            }
            postInvalidateOnAnimation();
        }

        void setActive(boolean value) {
            active = value;
            applySettings();
        }

        void reloadLayers() {
            if (getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            final int generation = ++loadGeneration;
            LayerLoader.loadAsync(getContext(), getWidth(), getHeight(), layers -> {
                if (generation != loadGeneration) {
                    LayerLoader.recycle(layers);
                    return;
                }
                renderer.setLayers(layers);
                postInvalidateOnAnimation();
            });
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            renderer.setSize(w, h);
            reloadLayers();
        }

        @Override
        public void onTilt(float x, float y) {
            if (renderer.setTarget(x, y)) {
                postInvalidateOnAnimation();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            long nowMs = System.currentTimeMillis();
            boolean moving = renderer.step(System.nanoTime());
            renderer.draw(canvas, nowMs);

            if (!active) {
                return;
            }
            if (moving || sweepMode != Settings.SWEEP_EFFICIENT) {
                postInvalidateOnAnimation();
            } else {
                postInvalidateDelayed(1000L - (nowMs % 1000L) + 4L);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            tilt.stop();
            super.onDetachedFromWindow();
        }
    }
}
