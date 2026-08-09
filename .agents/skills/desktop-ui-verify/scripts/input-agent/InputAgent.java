import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.instrument.Instrumentation;

/**
 * Java agent that injects synthetic AWT input events into any AWT/Swing/Compose-Desktop
 * app via a loopback socket, without OS-level events: no cursor movement, no focus steal.
 *
 * Launch: java -javaagent:inputagent.jar=7788 ...
 * Protocol (one command per line, coordinates in window-CONTENT points of the
 * frontmost visible Frame):
 *   click <x> <y>
 *   rightclick <x> <y>  (BUTTON3 with popup trigger, e.g. for context menus)
 *   press <x> <y>     (mouse down only)
 *   release <x> <y>
 *   move <x> <y>
 *   type <text>
 *   key <awt-keycode>
 *   info              (frame bounds, content-area screen origin and size)
 * Replies "ok <detail>" or "err <detail>" per line.
 */
public final class InputAgent {
    public static void premain(String args, Instrumentation inst) {
        int port = 7788;
        try { port = Integer.parseInt(args.trim()); } catch (Exception ignored) {}
        final int p = port;
        Thread t = new Thread(() -> serve(p), "input-agent");
        t.setDaemon(true);
        t.start();
    }

    static void serve(int port) {
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getLoopbackAddress())) {
            System.err.println("[input-agent] listening on 127.0.0.1:" + port);
            while (true) {
                try (java.net.Socket s = server.accept();
                     java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream()));
                     java.io.PrintWriter out = new java.io.PrintWriter(s.getOutputStream(), true)) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        try {
                            out.println(handle(line.trim()));
                        } catch (Exception e) {
                            out.println("err " + e);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            System.err.println("[input-agent] failed: " + e);
        }
    }

    static String handle(String line) throws Exception {
        String[] a = line.split("\\s+", 2);
        if (a.length == 0 || a[0].isEmpty()) return "err empty";
        Frame frame = targetFrame();
        if (frame == null) return "err no visible frame";
        switch (a[0]) {
            case "click": case "rightclick": case "press": case "release": case "move": {
                String[] xy = a[1].split("\\s+");
                int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]);
                return mouse(frame, a[0], x, y);
            }
            case "type":
                return type(frame, a.length > 1 ? a[1] : "");
            case "key":
                return key(frame, Integer.parseInt(a[1]));
            case "info": {
                final String[] r = new String[1];
                EventQueue.invokeAndWait(() -> {
                    Rectangle b = frame.getBounds();
                    Container content = contentOf(frame);
                    Point cs = content.isShowing() ? content.getLocationOnScreen() : new Point(-1, -1);
                    r[0] = "ok frame=" + b.x + "," + b.y + " " + b.width + "x" + b.height
                            + " content=" + cs.x + "," + cs.y + " " + content.getWidth() + "x" + content.getHeight();
                });
                return r[0];
            }
            default:
                return "err unknown command: " + a[0];
        }
    }

    private static volatile Component lastMouseTarget;

    static Frame targetFrame() {
        Frame focused = null, visible = null;
        for (Frame f : Frame.getFrames()) {
            if (!f.isVisible()) continue;
            if (visible == null) visible = f;
            if (f.isFocused()) focused = f;
        }
        return focused != null ? focused : visible;
    }

    static Container contentOf(Frame f) {
        if (f instanceof javax.swing.JFrame) return ((javax.swing.JFrame) f).getContentPane();
        return f;
    }

    static String mouse(Frame frame, String kind, int x, int y) throws Exception {
        final String[] result = new String[1];
        EventQueue.invokeAndWait(() -> {
            Container content = contentOf(frame);
            Component target = javax.swing.SwingUtilities.getDeepestComponentAt(content, x, y);
            if (target == null) { result[0] = "err nothing at " + x + "," + y; return; }
            Point p = javax.swing.SwingUtilities.convertPoint(content, x, y, target);
            long when = System.currentTimeMillis();
            EventQueue q = Toolkit.getDefaultToolkit().getSystemEventQueue();
            if (kind.equals("move")) {
                q.postEvent(new MouseEvent(target, MouseEvent.MOUSE_MOVED, when, 0, p.x, p.y, 0, false));
            } else if (kind.equals("rightclick")) {
                // macOS convention: popup trigger fires on press
                q.postEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, when, MouseEvent.BUTTON3_DOWN_MASK, p.x, p.y, 1, true, MouseEvent.BUTTON3));
                q.postEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, when + 20, 0, p.x, p.y, 1, false, MouseEvent.BUTTON3));
                q.postEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, when + 20, 0, p.x, p.y, 1, false, MouseEvent.BUTTON3));
            } else {
                if (!kind.equals("release"))
                    q.postEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, when, 0, p.x, p.y, 1, false, MouseEvent.BUTTON1));
                if (!kind.equals("press")) {
                    q.postEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, when + 20, 0, p.x, p.y, 1, false, MouseEvent.BUTTON1));
                    q.postEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, when + 20, 0, p.x, p.y, 1, false, MouseEvent.BUTTON1));
                }
            }
            lastMouseTarget = target;
            result[0] = "ok " + kind + " " + x + "," + y + " -> " + target.getClass().getName();
        });
        return result[0];
    }

    // Native focus transfer does not happen for injected clicks while the app is in the
    // background, so fall back to the last clicked component (for Compose Desktop this is
    // the single Skia canvas, which does its own internal focus routing).
    static Component focusTarget(Frame frame) {
        Component focus = frame.getFocusOwner();
        if (focus != null) return focus;
        Component last = lastMouseTarget;
        if (last != null && last.isShowing()) return last;
        return frame;
    }

    // KeyEvents posted to the EventQueue are filtered by KeyboardFocusManager against the
    // REAL focus owner (none while the app is in the background), so use redispatchEvent,
    // which bypasses the focus manager and delivers straight to the target component.
    static String type(Frame frame, String text) throws Exception {
        final String[] result = new String[1];
        EventQueue.invokeAndWait(() -> {
            Component target = focusTarget(frame);
            KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            long when = System.currentTimeMillis();
            for (char c : text.toCharArray()) {
                kfm.redispatchEvent(target, new KeyEvent(target, KeyEvent.KEY_TYPED, when, 0, KeyEvent.VK_UNDEFINED, c));
            }
            result[0] = "ok typed " + text.length() + " chars -> " + target.getClass().getName();
        });
        return result[0];
    }

    static String key(Frame frame, int keyCode) throws Exception {
        final String[] result = new String[1];
        EventQueue.invokeAndWait(() -> {
            Component target = focusTarget(frame);
            KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            long when = System.currentTimeMillis();
            kfm.redispatchEvent(target, new KeyEvent(target, KeyEvent.KEY_PRESSED, when, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
            kfm.redispatchEvent(target, new KeyEvent(target, KeyEvent.KEY_RELEASED, when + 20, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
            result[0] = "ok key " + keyCode + " -> " + target.getClass().getName();
        });
        return result[0];
    }
}
