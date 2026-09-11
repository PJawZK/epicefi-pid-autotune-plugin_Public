package se.anders.tunerstudio.pidautotune.ui;

import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * Keeps plugin scroll panes stable while live Swing content changes underneath them.
 *
 * <p>The ownership rule is intentionally event-scoped, not time-scoped:</p>
 * <ul>
 *   <li>native mouse-wheel, scrollbar mouse/thumb and keyboard scrolling is accepted only while
 *       Swing is dispatching the actual {@link InputEvent};</li>
 *   <li>explicit product navigation/history-follow moves use {@link #setVerticalPosition} or
 *       {@link #scrollToBottomLater};</li>
 *   <li>timer, caret, focus, table/document model and layout activity cannot inherit permission
 *       from an earlier user gesture.</li>
 * </ul>
 *
 * <p>This is deliberately stricter than the former grace-window design. A wheel event is not a
 * 400 ms lease on the viewport: once that input event finishes, the user's new position is again
 * the protected anchor.</p>
 */
final class StableScrollSupport {
    private StableScrollSupport() { }

    static void installRecursively(Component root) {
        if (root == null) return;
        if (root instanceof JScrollPane) install((JScrollPane) root);
        if (root instanceof Container) {
            Component[] children = ((Container) root).getComponents();
            for (Component child : children) installRecursively(child);
        }
    }

    static void install(JScrollPane scroll) {
        if (scroll == null) return;
        JViewport old = scroll.getViewport();
        StableViewport viewport;
        if (old instanceof StableViewport) {
            viewport = (StableViewport) old;
        } else {
            Component view = old == null ? null : old.getView();
            Point position = old == null ? new Point(0, 0) : old.getViewPosition();
            viewport = new StableViewport();
            viewport.setOwner(scroll);
            if (old != null) {
                viewport.setOpaque(old.isOpaque());
                viewport.setBackground(old.getBackground());
            }
            scroll.setViewport(viewport);
            if (view != null) viewport.setView(view);
            viewport.setExplicitPosition(position);
        }
        viewport.setOwner(scroll);
    }

    static void setVerticalPosition(JScrollPane scroll, int value) {
        if (scroll == null) return;
        install(scroll);
        JViewport viewport = scroll.getViewport();
        if (viewport instanceof StableViewport) {
            StableViewport stable = (StableViewport) viewport;
            Point p = stable.getViewPosition();
            stable.setExplicitPosition(new Point(p.x, Math.max(0, value)));
        } else {
            scroll.getVerticalScrollBar().setValue(Math.max(0, value));
        }
    }

    static int verticalPosition(JScrollPane scroll) {
        return scroll == null || scroll.getViewport() == null ? 0 : scroll.getViewport().getViewPosition().y;
    }

    static boolean isAtBottom(JScrollPane scroll, int tolerance) {
        if (scroll == null) return true;
        JScrollBar bar = scroll.getVerticalScrollBar();
        if (bar == null) return true;
        int bottom = bar.getMaximum() - bar.getVisibleAmount();
        return bar.getValue() >= bottom - Math.max(0, tolerance);
    }

    static void scrollToBottomLater(final JScrollPane scroll) {
        scrollToBottomLater(scroll, verticalPosition(scroll));
    }

    static void scrollToBottomLater(final JScrollPane scroll, final int expectedPosition) {
        if (scroll == null) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                install(scroll);
                // A user input that happened after the append owns the viewport. Never let this
                // queued history-follow action override the newer user position.
                if (verticalPosition(scroll) != expectedPosition) return;
                JViewport vp = scroll.getViewport();
                JScrollBar bar = scroll.getVerticalScrollBar();
                int y = bar == null ? Math.max(0, vp.getViewSize().height - vp.getExtentSize().height)
                        : Math.max(0, bar.getMaximum() - bar.getVisibleAmount());
                if (vp instanceof StableViewport) {
                    Point p = vp.getViewPosition();
                    ((StableViewport) vp).setExplicitPosition(new Point(p.x, y));
                } else if (bar != null) {
                    bar.setValue(bar.getMaximum());
                }
            }
        });
    }

    static final class StableViewport extends JViewport {
        private boolean explicitMove;
        private JScrollPane owner;
        private Point anchoredPosition = new Point(0, 0);
        private boolean restoringAnchor;

        StableViewport() {
            addChangeListener(e -> enforceAnchorAfterViewportChange());
        }

        void setOwner(JScrollPane value) { owner = value; }

        void setExplicitPosition(Point p) {
            Point target = p == null ? new Point(0, 0) : new Point(p);
            explicitMove = true;
            try {
                super.setViewPosition(target);
                anchoredPosition = new Point(super.getViewPosition());
            } finally {
                explicitMove = false;
            }
        }

        @Override public void scrollRectToVisible(Rectangle contentRect) {
            if (allowMoveForCurrentEvent()) super.scrollRectToVisible(contentRect);
        }

        @Override public void doLayout() {
            Point before = super.getViewPosition();
            boolean protect = !allowMoveForCurrentEvent() && before != null;
            super.doLayout();
            if (protect) {
                // Layout is the right point to apply a real bounds clamp: transient model/caret
                // requests are ignored before here, while the final laid-out view/extent sizes are
                // now available. This preserves the anchor unless content genuinely became shorter.
                Point legal = clampToCurrentBounds(before);
                Point after = super.getViewPosition();
                if (!legal.equals(after)) setExplicitPosition(legal);
                else anchoredPosition = new Point(legal);
            }
        }

        @Override public void setViewPosition(Point p) {
            Point current = super.getViewPosition();
            if (p == null || p.equals(current)) return;

            if (allowMoveForCurrentEvent() || getView() == null || !isDisplayable()) {
                super.setViewPosition(p);
                anchoredPosition = new Point(super.getViewPosition());
            }
            // Otherwise ignore programmatic viewport chasing. Typical sources are caret/focus
            // changes, live table/document refreshes and layout/model activity from Swing timers.
        }

        private void enforceAnchorAfterViewportChange() {
            if (restoringAnchor || explicitMove) return;
            Point current = super.getViewPosition();
            if (allowMoveForCurrentEvent()) {
                anchoredPosition = new Point(current);
                return;
            }
            if (anchoredPosition != null && !anchoredPosition.equals(current)) {
                restoringAnchor = true;
                try { super.setViewPosition(new Point(anchoredPosition)); }
                finally { restoringAnchor = false; }
            }
        }

        private boolean allowMoveForCurrentEvent() {
            if (explicitMove) return true;
            AWTEvent event = EventQueue.getCurrentEvent();
            if (!(event instanceof InputEvent) || owner == null) return false;
            Object sourceObject = event.getSource();
            if (!(sourceObject instanceof Component)) return false;
            Component source = (Component) sourceObject;

            if (event instanceof MouseWheelEvent) {
                // A wheel event belongs only to the nearest scroll pane under the pointer. This
                // prevents a nested evidence/history pane from implicitly authorizing its parent.
                return nearestScrollPane(source) == owner;
            }
            if (event instanceof MouseEvent) {
                // Ordinary clicks (Start Capture, mode buttons, table selection, theme controls)
                // are NOT scrolling permission. Only the scrollbar/thumb/button component tree is.
                return isInScrollBarTree(source, owner.getVerticalScrollBar())
                        || isInScrollBarTree(source, owner.getHorizontalScrollBar());
            }
            if (event instanceof KeyEvent) {
                KeyEvent key = (KeyEvent) event;
                return isScrollKey(key.getKeyCode()) && nearestScrollPane(source) == owner;
            }
            return false;
        }

        private JScrollPane nearestScrollPane(Component source) {
            if (source == owner) return owner;
            Component ancestor = SwingUtilities.getAncestorOfClass(JScrollPane.class, source);
            return ancestor instanceof JScrollPane ? (JScrollPane) ancestor : null;
        }

        private static boolean isInScrollBarTree(Component source, JScrollBar bar) {
            return source != null && bar != null && (source == bar || SwingUtilities.isDescendingFrom(source, bar));
        }

        private static boolean isScrollKey(int code) {
            return code == KeyEvent.VK_PAGE_UP || code == KeyEvent.VK_PAGE_DOWN
                    || code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN
                    || code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT
                    || code == KeyEvent.VK_HOME || code == KeyEvent.VK_END;
        }

        private Point clampToCurrentBounds(Point requested) {
            if (requested == null) return new Point(0, 0);
            Dimension view = getViewSize();
            Dimension extent = getExtentSize();
            int maxX = Math.max(0, view.width - extent.width);
            int maxY = Math.max(0, view.height - extent.height);
            int x = Math.max(0, Math.min(requested.x, maxX));
            int y = Math.max(0, Math.min(requested.y, maxY));
            return new Point(x, y);
        }
    }
}
