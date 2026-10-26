package com.yan.ios26battery;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Runtime-only battery renderer. It does not replace or resign SystemUI.apk.
 * The renderer is activated when either crDroid horizontal battery style is selected.
 */
public final class HookEntry implements IXposedHookLoadPackage {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String SETTINGS = "com.android.settings";

    private static final Object DRAW_LOCK = new Object();
    private static final Paint OUTER = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint EMPTY = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint LEVEL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private static final RectF BODY = new RectF();
    private static final RectF INNER = new RectF();
    private static final RectF CAP = new RectF();
    private static final RectF FILL = new RectF();
    private static final Path INNER_PATH = new Path();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (SYSTEM_UI.equals(lp.packageName)) {
            hookDrawable("com.android.settingslib.graph.LandscapeBatteryDrawable", lp.classLoader);
            hookDrawable("com.android.settingslib.graph.RLandscapeBatteryDrawable", lp.classLoader);
        } else if (SETTINGS.equals(lp.packageName)) {
            hookBatteryStyleLabel();
        }
    }

    private static void hookDrawable(String className, ClassLoader loader) {
        try {
            Class<?> drawableClass = XposedHelpers.findClass(className, loader);
            XposedHelpers.findAndHookMethod(drawableClass, "draw", Canvas.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        drawIOS26(param.thisObject, (Canvas) param.args[0]);
                        param.setResult(null); // Replace the original draw() call.
                    } catch (Throwable error) {
                        // Fall through to crDroid's original renderer if this ROM differs.
                        XposedBridge.log("iOS26Battery: renderer fallback: " + error);
                    }
                }
            });
            XposedBridge.log("iOS26Battery: hooked " + className);
        } catch (Throwable error) {
            XposedBridge.log("iOS26Battery: couldn't hook " + className + ": " + error);
        }
    }

    private static void drawIOS26(Object drawable, Canvas canvas) {
        synchronized (DRAW_LOCK) {
            Rect b = (Rect) XposedHelpers.callMethod(drawable, "getBounds");
            if (b == null || b.isEmpty()) return;

            int batteryLevel = clamp(XposedHelpers.getIntField(drawable, "batteryLevel"), 0, 100);
            int fillColor = XposedHelpers.getIntField(drawable, "fillColor");
            int levelColor = XposedHelpers.getIntField(drawable, "levelColor");
            if (Color.alpha(fillColor) == 0) fillColor = Color.WHITE;
            if (Color.alpha(levelColor) == 0) levelColor = fillColor;

            float iconH = b.height();
            float outerInset = Math.max(0.25f, iconH * 0.035f);
            float terminalW = Math.max(1.0f, iconH * 0.13f);
            float terminalH = iconH * 0.34f;

            BODY.set(b.left + outerInset, b.top + outerInset,
                    b.right - terminalW * 0.58f, b.bottom - outerInset);
            float bodyH = BODY.height();
            float radius = bodyH * 0.5f;
            CAP.set(BODY.right - bodyH * 0.035f,
                    BODY.centerY() - terminalH * 0.5f,
                    b.right - outerInset * 0.2f,
                    BODY.centerY() + terminalH * 0.5f);

            OUTER.setStyle(Paint.Style.FILL);
            OUTER.setColor(fillColor);
            EMPTY.setStyle(Paint.Style.FILL);
            EMPTY.setColor(withAlpha(fillColor, 86));
            LEVEL.setStyle(Paint.Style.FILL);
            LEVEL.setColor(levelColor);

            canvas.save();
            canvas.drawRoundRect(BODY, radius, radius, OUTER);
            canvas.drawRoundRect(CAP, CAP.height() * 0.18f, CAP.height() * 0.18f, OUTER);

            float inset = Math.max(0.7f, bodyH * 0.105f);
            INNER.set(BODY.left + inset, BODY.top + inset,
                    BODY.right - inset, BODY.bottom - inset);
            float innerR = Math.max(0f, INNER.height() * 0.5f);
            EMPTY.setColor(withAlpha(fillColor, 78));
            canvas.drawRoundRect(INNER, innerR, innerR, EMPTY);

            // The filled portion is clipped to the capsule so its right edge tracks battery level.
            INNER_PATH.reset();
            INNER_PATH.addRoundRect(INNER, innerR, innerR, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(INNER_PATH);
            float fillRight = INNER.left + INNER.width() * batteryLevel / 100f;
            FILL.set(INNER.left, INNER.top, Math.max(INNER.left, fillRight), INNER.bottom);
            canvas.drawRect(FILL, LEVEL);
            canvas.restore();

            // iOS-style centered percentage. Keep it visible regardless of crDroid's old showPercent flag.
            String percent = Integer.toString(batteryLevel);
            TEXT.setStyle(Paint.Style.FILL);
            TEXT.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            TEXT.setTextAlign(Paint.Align.CENTER);
            TEXT.setTextSize(Math.min(INNER.height() * 0.80f, INNER.width() / (percent.length() * 0.56f)));
            int contrast = luminance(levelColor) > 145 ? Color.rgb(45, 45, 45) : Color.WHITE;
            TEXT.setColor(contrast);
            Paint.FontMetrics fm = TEXT.getFontMetrics();
            float baseline = INNER.centerY() - (fm.ascent + fm.descent) * 0.5f;
            canvas.drawText(percent, BODY.centerX(), baseline, TEXT);
            canvas.restore();
        }
    }

    private static void hookBatteryStyleLabel() {
        try {
            XC_MethodHook rename = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object raw = param.args[0];
                    if (!(raw instanceof CharSequence[])) return;
                    CharSequence[] entries = (CharSequence[]) raw;
                    if (entries.length < 8) return;

                    boolean hasPortrait = false;
                    boolean hasCircle = false;
                    int leftIndex = -1;
                    for (int i = 0; i < entries.length; i++) {
                        String value = entries[i] == null ? "" : entries[i].toString().toLowerCase(Locale.ROOT);
                        if (value.contains("potret") || value.contains("portrait")) hasPortrait = true;
                        if (value.contains("lingkaran") || value.contains("circle")) hasCircle = true;
                        if (value.contains("mendatar") && value.contains("kiri")
                                || value.contains("landscape") && value.contains("left")) leftIndex = i;
                    }
                    // Only touch the expected 8-option battery style dialog.
                    if (hasPortrait && hasCircle && leftIndex >= 0) entries[leftIndex] = "iOS 26";
                }
            };
            XposedHelpers.findAndHookMethod(AlertDialog.Builder.class, "setSingleChoiceItems",
                    CharSequence[].class, int.class, DialogInterface.OnClickListener.class, rename);
        } catch (Throwable error) {
            XposedBridge.log("iOS26Battery: couldn't rename battery style option: " + error);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static int luminance(int color) {
        return (int) (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
