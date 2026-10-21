package com.rimon.livingclock;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private final int backgroundColor = Color.rgb(10, 14, 22);
    private final int cardColor = Color.rgb(24, 30, 42);
    private final int accentColor = Color.rgb(225, 48, 65);
    private final int secondaryText = Color.rgb(157, 169, 187);

    private LinearLayout root;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(backgroundColor);
        getWindow().setNavigationBarColor(backgroundColor);
        getWindow().getDecorView().setSystemUiVisibility(0);

        buildInterface();
    }

    private void buildInterface() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(backgroundColor);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(28));
        root.setBackgroundColor(backgroundColor);

        scrollView.addView(root);
        setContentView(scrollView);

        addHeader();
        addClockPreview();
        addSectionTitle("YOUR STUDIO", "Make time personal.");

        addFeatureCard(
                "01",
                "Photo Studio",
                "Choose the portrait for your wallpaper",
                "CHOOSE PHOTO",
                this::choosePhoto
        );

        addFeatureCard(
                "02",
                "Depth Layers",
                "Prepare your background and cutouts",
                "MANAGE LAYERS",
                this::showLayerInfo
        );

        addFeatureCard(
                "03",
                "Clock Design",
                "Explore the signature red and black clock",
                "CLOCK DETAILS",
                this::showClockInfo
        );

        addFeatureCard(
                "04",
                "Motion Lab",
                "Fine-tune movement and parallax",
                "MOTION SETTINGS",
                this::showMotionInfo
        );

        addApplyButton();
        addFooter();
    }

    private void addHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);

        TextView eyebrow = text(
                "PERSONAL WALLPAPER STUDIO",
                10,
                Color.rgb(230, 92, 105)
        );
        eyebrow.setLetterSpacing(0.12f);

        TextView title = text("LIVING CLOCK", 25, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setLetterSpacing(0.035f);

        TextView subtitle = text(
                "TIME, WITH DEPTH.",
                11,
                secondaryText
        );
        subtitle.setLetterSpacing(0.17f);

        titleGroup.addView(eyebrow);
        titleGroup.addView(title);
        titleGroup.addView(subtitle);

        header.addView(
                titleGroup,
                new LinearLayout.LayoutParams(0, -2, 1f)
        );

        TextView badge = text("BETA", 10, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(8), dp(12), dp(8));
        badge.setBackground(rounded(
                Color.rgb(45, 30, 43),
                Color.rgb(105, 51, 69),
                18
        ));

        header.addView(badge);
        root.addView(header);

        View gap = new View(this);
        root.addView(gap, new LinearLayout.LayoutParams(1, dp(25)));
    }

    private void addClockPreview() {
        LinearLayout previewCard = new LinearLayout(this);
        previewCard.setOrientation(LinearLayout.VERTICAL);
        previewCard.setPadding(dp(14), dp(16), dp(14), dp(15));
        previewCard.setBackground(rounded(
                cardColor,
                Color.rgb(53, 62, 79),
                24
        ));

        LinearLayout previewHeader = new LinearLayout(this);
        previewHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView previewTitle = text("LIVE PREVIEW", 11, Color.WHITE);
        previewTitle.setTypeface(null, Typeface.BOLD);
        previewTitle.setLetterSpacing(0.12f);

        TextView status = text("●  CLOCK CONCEPT", 9,
                Color.rgb(244, 111, 124));
        status.setGravity(Gravity.CENTER_VERTICAL);

        previewHeader.addView(
                previewTitle,
                new LinearLayout.LayoutParams(0, -2, 1f)
        );
        previewHeader.addView(status);
        previewCard.addView(previewHeader);

        ClockPreview preview = new ClockPreview();

        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(-1, dp(265));
        previewParams.topMargin = dp(8);

        previewCard.addView(preview, previewParams);

        TextView caption = text(
                "A glimpse of the red-and-black clock.",
                11,
                secondaryText
        );
        caption.setGravity(Gravity.CENTER);
        previewCard.addView(caption);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(30);

        root.addView(previewCard, cardParams);
    }

    private void addSectionTitle(String title, String subtitle) {
        TextView heading = text(title, 12, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setLetterSpacing(0.14f);

        root.addView(heading);

        TextView sub = text(subtitle, 12, secondaryText);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(5);
        params.bottomMargin = dp(15);

        root.addView(sub, params);
    }

    private void addFeatureCard(
            String number,
            String title,
            String description,
            String action,
            Runnable callback
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(15), dp(17), dp(15));
        card.setBackground(rounded(
                cardColor,
                Color.rgb(44, 53, 69),
                20
        ));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView index = text(number, 11, accentColor);
        index.setTypeface(null, Typeface.BOLD);

        TextView heading = text(title, 17, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(index);

        TextView spacer = text("   ", 12, Color.WHITE);
        titleRow.addView(spacer);
        titleRow.addView(heading);

        top.addView(titleRow, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView arrow = text("↗", 21, accentColor);
        top.addView(arrow);

        card.addView(top);

        TextView detail = text(description, 12, secondaryText);

        LinearLayout.LayoutParams detailParams =
                new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(7);
        card.addView(detail, detailParams);

        Button button = new Button(this);
        button.setText(action);
        button.setTextSize(10);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setLetterSpacing(0.06f);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(rounded(
                Color.rgb(37, 44, 59),
                Color.rgb(69, 80, 101),
                12
        ));
        button.setOnClickListener(v -> callback.run());

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(-2, dp(42));
        buttonParams.topMargin = dp(13);

        card.addView(button, buttonParams);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(12);

        root.addView(card, cardParams);
    }

    private void addApplyButton() {
        Button apply = new Button(this);
        apply.setText("APPLY WALLPAPER   →");
        apply.setTextColor(Color.WHITE);
        apply.setTextSize(13);
        apply.setTypeface(null, Typeface.BOLD);
        apply.setAllCaps(false);
        apply.setLetterSpacing(0.04f);
        apply.setElevation(dp(5));
        apply.setBackground(rounded(
                accentColor,
                Color.rgb(255, 111, 126),
                17
        ));

        apply.setOnClickListener(v -> openWallpaperPicker());

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, dp(56));
        params.topMargin = dp(16);
        params.bottomMargin = dp(16);

        root.addView(apply, params);
    }

    private void addFooter() {
        TextView footer = text(
                "DESIGNED FOR YOUR SCREEN  •  BUILT WITH CARE",
                9,
                Color.rgb(104, 117, 137)
        );
        footer.setGravity(Gravity.CENTER);
        footer.setLetterSpacing(0.07f);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(5);

        root.addView(footer, params);
    }

    private void choosePhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(intent, 101);
        } catch (Exception e) {
            toast("Unable to open the photo picker.");
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 101 && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            toast("Photo selected. Layer integration comes next.");
        }
    }

    private void showLayerInfo() {
        toast("Depth-layer editing is coming next.");
    }

    private void showClockInfo() {
        toast("Clock customization is coming next.");
    }

    private void showMotionInfo() {
        toast("Parallax controls are coming next.");
    }

    private void openWallpaperPicker() {
        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, LivingClockService.class)
            );
            startActivity(intent);
        } catch (Exception e) {
            toast("Couldn't open the wallpaper preview.");
        }
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable rounded(
            int fill,
            int stroke,
            int radius
    ) {
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

    private final class ClockPreview extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ClockPreview() {
            super(MainActivity.this);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.36f;

            canvas.save();
            canvas.translate(cx, cy);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(19, 24, 35));
            canvas.drawCircle(0, 0, radius * 1.17f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(56, 66, 83));
            canvas.drawCircle(0, 0, radius, paint);

            for (int i = 0; i < 60; i++) {
                double angle = i * Math.PI / 30.0 - Math.PI / 2.0;

                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);

                boolean major = i % 5 == 0;

                paint.setStrokeWidth(major ? dp(3) : dp(1.2f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(
                        major
                                ? Color.rgb(206, 213, 225)
                                : Color.rgb(90, 101, 120)
                );

                canvas.drawLine(
                        cos * radius * 0.88f,
                        sin * radius * 0.88f,
                        cos * radius * 0.97f,
                        sin * radius * 0.97f,
                        paint
                );
            }

            long now = System.currentTimeMillis();
            float seconds = (now % 60000L) / 1000f;
            float minutes = (now % 3600000L) / 60000f;
            float hours = ((now / 3600000L) % 12) + minutes / 60f;

            drawHand(canvas, hours, 12, radius * 0.48f,
                    dp(5), Color.rgb(225, 48, 65));

            drawHand(canvas, minutes, 60, radius * 0.70f,
                    dp(3.2f), Color.WHITE);

            drawHand(canvas, seconds, 60, radius * 0.83f,
                    dp(1.4f), Color.rgb(225, 48, 65));

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(0, 0, dp(4), paint);

            canvas.restore();

            postInvalidateDelayed(33);
        }

        private void drawHand(
                Canvas canvas,
                float value,
                float divisions,
                float length,
                float thickness,
                int color
        ) {
            float angle = value * (float) (Math.PI * 2.0) / divisions
                    - (float) Math.PI / 2f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(thickness);
            paint.setColor(color);

            canvas.drawLine(
                    0,
                    0,
                    (float) Math.cos(angle) * length,
                    (float) Math.sin(angle) * length,
                    paint
            );
        }
    }
}
