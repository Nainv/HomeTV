package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/** Remote navigation for the firmware power dialog, only after the user explicitly opens it. */
final class PowerMenuRemote {
    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AccessibilityNodeInfo> actions = new ArrayList<>();
    private final Runnable expiry = this::close;
    private long until;
    private int selected;
    private View ring;
    private boolean shown;

    PowerMenuRemote(AccessibilityService service) { this.service = service; }
    void arm() { close(); until = SystemClock.elapsedRealtime() + 30000; handler.postDelayed(expiry, 30000); }
    void refresh() {
        if (until == 0) return;
        if (SystemClock.elapsedRealtime() >= until) { close(); return; }
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return;
        String pkg = String.valueOf(root.getPackageName());
        // Never navigate an underlying app while the system dialog is not present.
        boolean system = pkg.equals("android") || pkg.equals("com.android.systemui");
        if (!system) {
            root.recycle();
            if (shown && !pkg.equals(service.getPackageName())) close();
            return;
        }
        List<AccessibilityNodeInfo> next = new ArrayList<>();
        collect(root, next, 0); root.recycle();
        if (next.isEmpty()) { if (shown) close(); return; }
        for (AccessibilityNodeInfo node : actions) node.recycle();
        actions.clear(); actions.addAll(next);
        selected = Math.min(selected, actions.size() - 1);
        shown = true; showRing();
    }
    private void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> result, int depth) {
        if (depth > 20 || result.size() >= 24 || !node.isVisibleToUser()) return;
        if (node.isEnabled() && node.isClickable() && hasLabel(node, 0)) {
            Rect rect = new Rect(); node.getBoundsInScreen(rect);
            // Skip a full-screen dismiss surface; use the labelled action buttons within it.
            if (!rect.isEmpty() && rect.height() < service.getResources().getDisplayMetrics().heightPixels * .8f) {
                result.add(AccessibilityNodeInfo.obtain(node)); return;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { collect(child, result, depth + 1); child.recycle(); }
        }
    }
    private boolean hasLabel(AccessibilityNodeInfo node, int depth) {
        if (node.getText() != null || node.getContentDescription() != null) return true;
        if (depth >= 4) return false;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            boolean labelled = hasLabel(child, depth + 1); child.recycle();
            if (labelled) return true;
        }
        return false;
    }
    boolean handle(KeyEvent event) {
        if (!shown || actions.isEmpty()) return false;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_HOME) { close(); return false; }
        boolean arrow = code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_LEFT;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (!arrow && !ok) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return true;
        if (ok) {
            AccessibilityNodeInfo node = actions.get(selected);
            if (node.refresh() && node.isVisibleToUser()) node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            close();
        } else {
            int delta = code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            selected = (selected + delta + actions.size()) % actions.size(); showRing();
        }
        return true;
    }
    private void showRing() {
        Rect rect = new Rect(); actions.get(selected).getBoundsInScreen(rect);
        WindowManager wm = service.getSystemService(WindowManager.class);
        if (wm == null || rect.isEmpty()) return;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(rect.width(), rect.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT; params.x = rect.left; params.y = rect.top;
        try {
            if (ring == null) {
                ring = new View(service);
                ring.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                GradientDrawable border = new GradientDrawable(); border.setColor(Color.TRANSPARENT);
                border.setCornerRadius(16); border.setStroke(4, Color.rgb(121, 201, 178)); ring.setBackground(border);
                wm.addView(ring, params);
            } else wm.updateViewLayout(ring, params);
        } catch (RuntimeException unavailable) { close(); }
    }
    void close() {
        handler.removeCallbacks(expiry); until = 0; shown = false; selected = 0;
        for (AccessibilityNodeInfo node : actions) node.recycle(); actions.clear();
        if (ring != null) {
            WindowManager wm = service.getSystemService(WindowManager.class);
            try { if (wm != null) wm.removeView(ring); } catch (RuntimeException ignored) { }
            ring = null;
        }
    }
}
