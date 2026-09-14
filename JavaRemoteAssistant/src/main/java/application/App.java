package application;

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * 主程序入口 JavaFX App Package:mvn clean javafx:jlink
 */
public class App extends Application {
	private static Stage sstage;
	private static Scene scene;
	private static Robot r;
	private static boolean preferTop = true;
	private static TrayIcon trayIcon;
	private static boolean trayQueuePatched;
	private static final AtomicBoolean winSwitching = new AtomicBoolean(false);
	private static final AtomicBoolean linuxSwitching = new AtomicBoolean(false);
	private static double savedStageX = Double.NaN;
	private static double savedStageY = Double.NaN;
	/** Scene 客户区宽高，不是 Stage 外框。 */
	private static double savedStageW = Double.NaN;
	private static double savedStageH = Double.NaN;
	/** Linux 重建窗口用的外框钉住尺寸。只在首次显示和用户拖边时更新，绝不从 GTK 收缩回写。 */
	private static double pinnedOuterW = Double.NaN;
	private static double pinnedOuterH = Double.NaN;
	/** Linux/X11：xdotool 像素几何 [x,y,w,h]，切桌面只用这份。 */
	private static int[] lastLinuxGeom;
	private static boolean ignorePlaceUpdates;
	private static double dragOffsetX;
	private static double dragOffsetY;
	private static boolean windowDragging;
	public static boolean start_up = false;
	public static final String VERSION = "4.2.0";
	public static String version = VERSION;
	private static final double MIN_WIDTH = 240;
	private static final double DEFAULT_WIDTH = 252;

	@Override
	public void start(Stage stage) throws IOException {
		System.out.println("欢迎使用Java Remote Assistant远程协助助手。");
		System.out.println("Ryan Yim -2021-09-13-");
		System.out.println("版本：" + version);
		System.out.println("\n====>>>>正在初始化<<<<====");
		if (!Tools.isWindows()) {
			System.out.println("显示协议: " + Tools.linuxDisplayKind() + " · " + linuxDesktopToolStatus());
		}
		// hide() 切桌面时不能让 FX 当成「最后一个窗口关了」而退出
		Platform.setImplicitExit(false);
		sstage = stage;
		preferTop = !"0".equals(AppConf.load().get("always_on_top"));
		scene = new Scene(loadFXML("primary"));
		scene.getStylesheets().add(getClass().getResource("sample.css").toExternalForm());
		stage.setScene(scene);
		bindWindowDrag(scene);
		refreshTitle();
		stage.setResizable(true);
		stage.setMinWidth(MIN_WIDTH);
		stage.setMinHeight(360);
		try {
			stage.getIcons().add(new Image(App.class.getResourceAsStream("/application/icon.png")));
		} catch (Exception e) {
			System.err.println("找不到图标：" + e.toString());
		}
		r = new Robot();
		stage.setAlwaysOnTop(preferTop);
		stage.setWidth(DEFAULT_WIDTH);
		stage.show();
		fitStageInScreen();
		ScrollBars.installAutoHide(scene.getRoot());
		snapshotSceneSize();
		rememberStagePlace();
		rememberLinuxGeom();
		capturePinnedFromStage();
		bindStagePlace(stage);
		bindSceneSize(scene);
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				capturePinnedFromStage();
			}
		});

		bindExitOnClose(stage);

		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				setupTray();
			}
		});

		scene.setOnKeyReleased(new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				if (PrimaryController.suppressModRepress()) {
					return;
				}
				String Key = event.getCode().getName();
				if (PrimaryController.getCtrl() && "Ctrl".equals(Key)) {
					r.keyPress(KeyCode.CONTROL);
				} else if (PrimaryController.getAlt() && "Alt".equals(Key)) {
					r.keyPress(KeyCode.ALT);
				} else if (PrimaryController.getShift() && "Shift".equals(Key)) {
					r.keyPress(KeyCode.SHIFT);
				} else if (PrimaryController.getWindows() && ("Windows".equals(Key) || "Super".equals(Key))) {
					r.keyPress(KeyCode.WINDOWS);
				}
			}
		});
		stage.setAlwaysOnTop(preferTop);
	}

	/** 托盘：双平台都挂；不弹 displayMessage（Linux 上关不掉）。GNOME/X11 上 AWT 托盘常半残，失败则软退。 */
	private void setupTray() {
		if ("0".equals(AppConf.load().get("tray"))) {
			System.out.println("托盘已在 conf 关闭（tray=0）");
			return;
		}
		try {
			if (!SystemTray.isSupported()) {
				System.out.println("本系统不支持 SystemTray（可设 tray=0）");
				return;
			}
			SystemTray tray = SystemTray.getSystemTray();
			URL resource = this.getClass().getResource("icon.png");
			ImageIcon icon = new ImageIcon(resource);
			if (Tools.isWindows()) {
				setupWindowsTray(tray, icon);
			} else {
				setupAwtTray(tray, icon);
			}
			patchTrayEventQueue();
			System.out.println("系统托盘已启用（点击/菜单→显示主界面）");
		} catch (UnsupportedOperationException e) {
			removeTrayQuiet();
			System.out.println("托盘实际不可用（可设 tray=0）：" + e.getMessage());
		} catch (AWTException e1) {
			removeTrayQuiet();
			System.out.println(String.format("[%s]系统托盘AWTException：%s",
					LocalDateTime.now().toString().replace("T", "|"), e1.toString()));
		} catch (Exception e) {
			removeTrayQuiet();
			System.out.println(String.format("[%s]系统托盘设置出错！%s",
					LocalDateTime.now().toString().replace("T", "|"), e.toString()));
		}
	}

	/** Linux：AWT 原生菜单。 */
	private void setupAwtTray(SystemTray tray, ImageIcon icon) throws AWTException {
		Font zh = trayMenuFont();
		PopupMenu menu = new PopupMenu();
		menu.setFont(zh);
		MenuItem show = new MenuItem("显示主界面");
		MenuItem quit = new MenuItem("退出");
		show.setFont(zh);
		quit.setFont(zh);
		show.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						showFront();
					}
				});
			}
		});
		quit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						System.exit(0);
					}
				});
			}
		});
		menu.add(show);
		menu.add(quit);
		trayIcon = new TrayIcon(icon.getImage(), "远程协助 v" + version, menu);
		trayIcon.setImageAutoSize(true);
		trayIcon.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						showFront();
					}
				});
			}
		});
		tray.add(trayIcon);
	}

	/**
	 * Windows：AWT 原生菜单不走字体回退，英文系统上中文会变成方框。
	 * 改用 Swing 弹出菜单，并指定微软雅黑/宋体。
	 */
	private void setupWindowsTray(SystemTray tray, ImageIcon icon) throws AWTException {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignored) {
		}
		final Font zh = trayMenuFont();
		UIManager.put("MenuItem.font", zh);
		UIManager.put("PopupMenu.font", zh);
		final JPopupMenu popup = new JPopupMenu();
		popup.setFont(zh);
		JMenuItem show = new JMenuItem("显示主界面");
		JMenuItem quit = new JMenuItem("退出");
		show.setFont(zh);
		quit.setFont(zh);
		show.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						showFront();
					}
				});
			}
		});
		quit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						System.exit(0);
					}
				});
			}
		});
		popup.add(show);
		popup.add(quit);
		final JDialog hidden = new JDialog((Frame) null);
		hidden.setUndecorated(true);
		hidden.setAlwaysOnTop(true);
		hidden.setType(java.awt.Window.Type.UTILITY);
		hidden.setSize(1, 1);
		popup.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
				hidden.setVisible(false);
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
				hidden.setVisible(false);
			}
		});
		trayIcon = new TrayIcon(icon.getImage(), "远程协助 v" + version);
		trayIcon.setImageAutoSize(true);
		trayIcon.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						showFront();
					}
				});
			}
		});
		trayIcon.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				showWinTrayMenu(e, hidden, popup);
			}

			@Override
			public void mousePressed(MouseEvent e) {
				showWinTrayMenu(e, hidden, popup);
			}
		});
		tray.add(trayIcon);
	}

	private static void showWinTrayMenu(MouseEvent e, JDialog hidden, JPopupMenu popup) {
		if (!e.isPopupTrigger()) {
			return;
		}
		hidden.setLocation(e.getXOnScreen(), e.getYOnScreen());
		hidden.setVisible(true);
		popup.show(hidden, 0, 0);
	}

	/** 选能画出中文的字体。英文 Windows 默认 Dialog 没有汉字，托盘菜单会变成方框。 */
	private static Font trayMenuFont() {
		String sample = "显示主界面退出远程协助";
		String[] names = new String[] { "Microsoft YaHei UI", "Microsoft YaHei", "微软雅黑", "NSimSun", "SimSun",
				"宋体", Font.SANS_SERIF, Font.DIALOG };
		for (int i = 0; i < names.length; i++) {
			Font f = new Font(names[i], Font.PLAIN, 13);
			if (f.canDisplayUpTo(sample) == -1) {
				return f;
			}
		}
		return new Font(Font.DIALOG, Font.PLAIN, 13);
	}

	/** 吞掉托盘 peer 后续抛的 UnsupportedOperationException，避免刷屏。 */
	private static void patchTrayEventQueue() {
		if (trayQueuePatched) {
			return;
		}
		trayQueuePatched = true;
		try {
			Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
				@Override
				protected void dispatchEvent(AWTEvent event) {
					try {
						super.dispatchEvent(event);
					} catch (UnsupportedOperationException ex) {
						String m = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
						if (m.contains("system tray")) {
							System.out.println("托盘后续事件失败，已忽略（可设 tray=0）");
							return;
						}
						throw ex;
					}
				}
			});
		} catch (Exception ignored) {
		}
	}

	private static void removeTrayQuiet() {
		TrayIcon icon = trayIcon;
		trayIcon = null;
		if (icon == null) {
			return;
		}
		try {
			if (SystemTray.isSupported()) {
				SystemTray.getSystemTray().remove(icon);
			}
		} catch (Throwable ignored) {
		}
	}

	static void refreshTitle() {
		if (sstage == null) {
			return;
		}
		String mode = Session.dryRun() ? " · 体验版/" + Session.logicalOsName() : "";
		sstage.setTitle("远程协助" + mode);
	}

	static void setRoot(String fxml) throws IOException {
		int[] geom = HostActions.linuxWindowGeom();
		scene.setRoot(loadFXML(fxml));
		if (sstage != null) {
			if (geom != null) {
				HostActions.linuxApplyWindowGeom(geom);
			} else {
				restoreStagePlace();
			}
			ScrollBars.installAutoHide(scene.getRoot());
		}
		refreshTitle();
	}

	/** 允许拉伸；高度不超过可视区域约 90%，避免设置页把窗口撑出屏幕。 */
	static void fitStageInScreen() {
		if (sstage == null) {
			return;
		}
		Rectangle2D vb = Screen.getPrimary().getVisualBounds();
		double maxW = Math.max(sstage.getMinWidth(), vb.getWidth() * 0.92);
		double maxH = Math.max(sstage.getMinHeight(), vb.getHeight() * 0.90);
		double w = sstage.getWidth();
		double h = sstage.getHeight();
		if (!(w > 1) && savedStageW > 1) {
			w = savedStageW;
		}
		if (!(h > 1) && savedStageH > 1) {
			h = savedStageH;
		}
		if (!(w > 1)) {
			w = DEFAULT_WIDTH;
		}
		if (!(h > 1)) {
			h = Math.min(640, maxH);
		}
		w = Math.min(Math.max(w, sstage.getMinWidth()), maxW);
		h = Math.min(Math.max(h, sstage.getMinHeight()), maxH);
		sstage.setWidth(w);
		sstage.setHeight(h);
		double x = sstage.getX();
		double y = sstage.getY();
		if (!Double.isFinite(x)) {
			x = vb.getMinX() + (vb.getWidth() - w) / 2;
		}
		if (!Double.isFinite(y)) {
			y = vb.getMinY() + Math.max(0, (vb.getHeight() - h) / 2);
		}
		if (x + w > vb.getMaxX()) {
			x = vb.getMaxX() - w;
		}
		if (y + h > vb.getMaxY()) {
			y = vb.getMaxY() - h;
		}
		if (x < vb.getMinX()) {
			x = vb.getMinX();
		}
		if (y < vb.getMinY()) {
			y = vb.getMinY();
		}
		sstage.setX(x);
		sstage.setY(y);
	}

	static Stage getStage() {
		return sstage;
	}

	static boolean isAlwaysOnTop() {
		return sstage != null && sstage.isAlwaysOnTop();
	}

	static boolean preferAlwaysOnTop() {
		return preferTop;
	}

	static void setPreferTop(boolean on) {
		preferTop = on;
		setTop(on);
	}

	static void restorePreferredTop() {
		setTop(preferTop);
	}

	static void setTop(boolean setTop) {
		if (sstage != null) {
			sstage.setAlwaysOnTop(setTop);
		}
	}

	public static void setStageX(double d) {
		if (sstage != null) {
			sstage.setX(d);
		}
		if (Double.isFinite(d)) {
			savedStageX = d;
		}
	}

	public static void setStageY(double i) {
		if (sstage != null) {
			sstage.setY(i);
		}
		if (Double.isFinite(i)) {
			savedStageY = i;
		}
	}

	/** 只记坐标。尺寸绝不能用 Stage.getWidth() 回写，边框/标题栏会被吃掉、越切越小。 */
	static void rememberStagePlace() {
		if (sstage == null || !sstage.isShowing()) {
			return;
		}
		double x = sstage.getX();
		double y = sstage.getY();
		if (Double.isFinite(x) && Double.isFinite(y)) {
			savedStageX = x;
			savedStageY = y;
		}
	}

	static void rememberLinuxGeom() {
		if (Tools.isWindows() || sstage == null || !sstage.isShowing()) {
			return;
		}
		int[] g = HostActions.linuxWindowGeom();
		if (g != null) {
			lastLinuxGeom = g;
		}
	}

	private static void applyLinuxGeomSoon(final int[] geom) {
		final int[] g = geom != null ? geom : lastLinuxGeom;
		if (g == null) {
			return;
		}
		HostActions.linuxApplyWindowGeom(g);
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				HostActions.linuxApplyWindowGeom(g);
			}
		});
	}

	/** 记下 Scene 客户区。用户拉伸时走这里；切桌面禁止重采样 Stage 外框。 */
	static void snapshotSceneSize() {
		if (scene == null) {
			return;
		}
		double w = scene.getWidth();
		double h = scene.getHeight();
		if (w > 1 && h > 1) {
			savedStageW = w;
			savedStageH = h;
		}
	}

	static void restoreStagePlace() {
		if (sstage == null) {
			return;
		}
		boolean prev = ignorePlaceUpdates;
		ignorePlaceUpdates = true;
		try {
			if (Tools.isWindows()) {
				applySavedSceneSize();
			}
			if (Double.isFinite(savedStageX) && Double.isFinite(savedStageY)) {
				sstage.setX(savedStageX);
				sstage.setY(savedStageY);
			}
		} finally {
			ignorePlaceUpdates = prev;
		}
	}

	/**
	 * 按客户区还原。用「外框 − 客户区」补装饰，避免 setWidth(getWidth()) 把边框吃掉。
	 * 客户区已经对上则不动，防止 GTK 上无意义的 setWidth 再缩一圈。
	 */
	private static void applySavedSceneSize() {
		applySavedSceneSizeTo(savedStageW, savedStageH);
	}

	private static void applySavedSceneSizeTo(double clientW, double clientH) {
		if (sstage == null || scene == null || !(clientW > 1) || !(clientH > 1)) {
			return;
		}
		double curW = scene.getWidth();
		double curH = scene.getHeight();
		if (!(curW > 1) || !(curH > 1)) {
			return;
		}
		if (Math.abs(curW - clientW) < 2 && Math.abs(curH - clientH) < 2) {
			return;
		}
		double outerW = sstage.getWidth();
		double outerH = sstage.getHeight();
		if (!(outerW > 1) || !(outerH > 1)) {
			return;
		}
		double nw = outerW + (clientW - curW);
		double nh = outerH + (clientH - curH);
		if (nw >= sstage.getMinWidth()) {
			sstage.setWidth(nw);
		}
		if (nh >= sstage.getMinHeight()) {
			sstage.setHeight(nh);
		}
	}

	/**
	 * 钉住当前外框。仅启动和用户拖边时调用。
	 * 切桌面 / 重建窗口之后禁止再读 getWidth()，否则会把 GTK 吃掉的边框存进去。
	 */
	private static void capturePinnedFromStage() {
		if (sstage == null || !sstage.isShowing()) {
			return;
		}
		double w = sstage.getWidth();
		double h = sstage.getHeight();
		if (w > 50 && h > 50) {
			pinnedOuterW = w;
			pinnedOuterH = h;
		}
	}

	private static void capturePinnedOnce() {
		if (!(pinnedOuterW > 50) || !(pinnedOuterH > 50)) {
			capturePinnedFromStage();
		}
	}

	private static boolean pointerNearStageEdge() {
		if (sstage == null || !sstage.isShowing()) {
			return false;
		}
		try {
			if (MouseInfo.getPointerInfo() == null) {
				return false;
			}
			Point p = MouseInfo.getPointerInfo().getLocation();
			double x = sstage.getX();
			double y = sstage.getY();
			double w = sstage.getWidth();
			double h = sstage.getHeight();
			if (!Double.isFinite(x) || !Double.isFinite(y) || !(w > 1) || !(h > 1)) {
				return false;
			}
			int m = 16;
			if (p.x < x - m || p.x > x + w + m || p.y < y - m || p.y > y + h + m) {
				return false;
			}
			return p.x <= x + m || p.x >= x + w - m || p.y <= y + m || p.y >= y + h - m;
		} catch (Exception e) {
			return false;
		}
	}

	private static void onLinuxStageSizeChanged() {
		if (Tools.isWindows() || ignorePlaceUpdates || sstage == null || !sstage.isShowing()) {
			return;
		}
		if (pointerNearStageEdge()) {
			capturePinnedFromStage();
			sstage.setMinWidth(MIN_WIDTH);
			sstage.setMinHeight(360);
			return;
		}
		if (pinnedOuterW > 50 && sstage.getWidth() < pinnedOuterW - 1) {
			ignorePlaceUpdates = true;
			try {
				sstage.setMinWidth(pinnedOuterW);
				sstage.setMinHeight(pinnedOuterH);
			} finally {
				ignorePlaceUpdates = false;
			}
		}
	}

	/** show() 后下一拍可能再改几何，补一次后再允许记下新的拖拽。 */
	private static void reapplyStagePlaceLater() {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				try {
					restoreStagePlace();
				} finally {
					ignorePlaceUpdates = false;
				}
			}
		});
	}

	private static void bindWindowDrag(Scene sc) {
		sc.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, new EventHandler<javafx.scene.input.MouseEvent>() {
			@Override
			public void handle(javafx.scene.input.MouseEvent e) {
				if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY || sstage == null
						|| isInteractiveTarget(e.getTarget())) {
					windowDragging = false;
					return;
				}
				dragOffsetX = e.getScreenX() - sstage.getX();
				dragOffsetY = e.getScreenY() - sstage.getY();
				windowDragging = Double.isFinite(dragOffsetX) && Double.isFinite(dragOffsetY);
			}
		});
		sc.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, new EventHandler<javafx.scene.input.MouseEvent>() {
			@Override
			public void handle(javafx.scene.input.MouseEvent e) {
				if (!windowDragging || sstage == null) {
					return;
				}
				sstage.setX(e.getScreenX() - dragOffsetX);
				double y = e.getScreenY() - dragOffsetY;
				sstage.setY(y < 0 ? 0 : y);
			}
		});
		sc.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, new EventHandler<javafx.scene.input.MouseEvent>() {
			@Override
			public void handle(javafx.scene.input.MouseEvent e) {
				windowDragging = false;
			}
		});
	}

	private static boolean isInteractiveTarget(Object target) {
		javafx.scene.Node n = target instanceof javafx.scene.Node ? (javafx.scene.Node) target : null;
		while (n != null) {
			if (n instanceof javafx.scene.control.Button || n instanceof javafx.scene.control.CheckBox
					|| n instanceof javafx.scene.control.ComboBoxBase
					|| n instanceof javafx.scene.control.TextInputControl) {
				return true;
			}
			n = n.getParent();
		}
		return false;
	}

	private static void bindStagePlace(Stage stage) {
		stage.xProperty().addListener((obs, oldV, newV) -> {
			if (ignorePlaceUpdates || !stage.isShowing() || newV == null || !Double.isFinite(newV.doubleValue())) {
				return;
			}
			savedStageX = newV.doubleValue();
		});
		stage.yProperty().addListener((obs, oldV, newV) -> {
			if (ignorePlaceUpdates || !stage.isShowing() || newV == null || !Double.isFinite(newV.doubleValue())) {
				return;
			}
			savedStageY = newV.doubleValue();
		});
		if (!Tools.isWindows()) {
			stage.widthProperty().addListener((obs, oldV, newV) -> onLinuxStageSizeChanged());
			stage.heightProperty().addListener((obs, oldV, newV) -> onLinuxStageSizeChanged());
		}
	}

	private static void bindSceneSize(Scene sc) {
		if (!Tools.isWindows()) {
			return;
		}
		sc.widthProperty().addListener((obs, oldV, newV) -> {
			if (ignorePlaceUpdates || sstage == null || !sstage.isShowing() || newV == null) {
				return;
			}
			double w = newV.doubleValue();
			if (w > 1) {
				savedStageW = w;
			}
		});
		sc.heightProperty().addListener((obs, oldV, newV) -> {
			if (ignorePlaceUpdates || sstage == null || !sstage.isShowing() || newV == null) {
				return;
			}
			double h = newV.doubleValue();
			if (h > 1) {
				savedStageH = h;
			}
		});
	}

	/**
	 * 出现在「当前」桌面。托盘 / 切桌面 / 显示桌面后都走这里。
	 * Windows：原版 hide()+show()（虚拟桌面会跟到当前）。
	 * Linux X11：只迁窗口，禁止 hide/show 和 setWidth（GTK 会越切越小）。
	 * Wayland / 没有 xdotool、wmctrl：新建 Stage。不要只 toFront()，窗还在旧工作区等于没显示。
	 */
	public static void showFront() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					showFront();
				}
			});
			return;
		}
		try {
			if (sstage == null) {
				return;
			}
			sstage.setIconified(false);
			rememberStagePlace();
			rememberLinuxGeom();
			ignorePlaceUpdates = true;
			try {
				if (Tools.isWindows()) {
					sstage.hide();
					sstage.show();
					restoreStagePlace();
					restorePreferredTop();
					reapplyStagePlaceLater();
					return;
				}
				if (!Tools.linuxX11DesktopToolsOk()) {
					remountOnCurrentDesktop();
					restorePreferredTop();
					ignorePlaceUpdates = false;
					return;
				}
				boolean wasShowing = sstage.isShowing();
				int[] geom = lastLinuxGeom;
				if (HostActions.moveWindowToCurrentDesktop(windowTitleHint())) {
					if (!wasShowing) {
						sstage.show();
						applyLinuxGeomSoon(geom);
					}
					sstage.toFront();
					restorePreferredTop();
					ignorePlaceUpdates = false;
					return;
				}
				remountOnCurrentDesktop();
				applyLinuxGeomSoon(geom);
				restorePreferredTop();
				ignorePlaceUpdates = false;
			} catch (RuntimeException e) {
				ignorePlaceUpdates = false;
				throw e;
			}
		} catch (Exception e) {
			System.out.println(String.format("[%s]界面前置出错。",
					LocalDateTime.now().toString().replace("T", "|")));
		}
	}

	/**
	 * 同一扇窗换不了工作区时：把 Scene 搬到新 Stage，系统映射到当前桌面。
	 * 必须在 FX 线程。托盘点「显示」在 Wayland / 无 xdotool 时靠这个。
	 * 外框只用钉住的宽高做 min，禁止 setWidth/sizeToScene/回读 getWidth。
	 */
	private static void remountOnCurrentDesktop() {
		Stage old = sstage;
		if (old == null || scene == null) {
			return;
		}
		capturePinnedOnce();
		if (old.isShowing()) {
			rememberStagePlace();
		}
		double x = Double.isFinite(savedStageX) ? savedStageX : old.getX();
		double y = Double.isFinite(savedStageY) ? savedStageY : old.getY();
		double pinW = pinnedOuterW;
		double pinH = pinnedOuterH;
		String title = old.getTitle();
		List<Image> icons = new ArrayList<Image>(old.getIcons());
		old.setOnCloseRequest(null);
		try {
			old.setAlwaysOnTop(false);
			old.setScene(null);
			old.hide();
			Stage neu = new Stage();
			neu.setScene(scene);
			neu.setTitle(title == null ? "远程协助" : title);
			neu.setResizable(true);
			neu.setMinWidth(MIN_WIDTH);
			neu.setMinHeight(360);
			if (pinW > 50 && pinH > 50) {
				neu.setMinWidth(pinW);
				neu.setMinHeight(pinH);
			}
			neu.getIcons().setAll(icons);
			neu.setX(x);
			neu.setY(y);
			neu.setAlwaysOnTop(preferTop);
			bindExitOnClose(neu);
			sstage = neu;
			neu.show();
			bindStagePlace(neu);
			try {
				old.hide();
			} catch (Exception ignored) {
			}
			System.out.println("主界面已在当前工作区显示");
		} catch (Exception e) {
			if (old.getScene() == null) {
				old.setScene(scene);
			}
			sstage = old;
			bindExitOnClose(old);
			old.setAlwaysOnTop(preferTop);
			old.show();
			restoreStagePlace();
			System.err.println("当前桌面显示失败，已退回原窗口: " + e);
		}
	}

	static boolean closeToTray() {
		return !"0".equals(AppConf.load().get("close_to_tray"));
	}

	private static void bindExitOnClose(Stage stage) {
		stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent event) {
				if (closeToTray()) {
					event.consume();
					if (sstage != null) {
						rememberStagePlace();
						rememberLinuxGeom();
						sstage.hide();
					}
					System.out.println("已缩到托盘（JRA设置可改为关闭即退出）");
					return;
				}
				System.out.println(String.format("[%s]感谢使用Java Remote Assistant！",
						LocalDateTime.now().toString().replace("T", "|")));
				System.exit(0);
			}
		});
	}

	/**
	 * 显示桌面后把 JRA 再抬起来（其它窗口保持收起）。
	 * 短暂强制置顶，避免跟系统热键抢焦点失败。
	 */
	public static void keepVisibleAfterShowDesktop() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				for (int i = 0; i < 4; i++) {
					sleepQuiet(i == 0 ? 120 : 100);
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							if (sstage == null) {
								return;
							}
							ignorePlaceUpdates = true;
							try {
								sstage.setIconified(false);
								sstage.setAlwaysOnTop(true);
								sstage.show();
								if (Tools.isWindows()) {
									restoreStagePlace();
								} else {
									applyLinuxGeomSoon(lastLinuxGeom);
								}
								sstage.toFront();
							} finally {
								ignorePlaceUpdates = false;
							}
						}
					});
				}
				sleepQuiet(350);
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						restorePreferredTop();
						showFront();
					}
				});
			}
		}, "jra-keep-after-desktop").start();
	}

	/**
	 * Linux：切完后用 xdotool 迁窗口。Windows 不要走这里（会 showFront 拽回旧桌面）。
	 */
	public static void followCurrentDesktop() {
		if (Tools.isWindows()) {
			return;
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				sleepQuiet(120);
				boolean moved = HostActions.moveWindowToCurrentDesktop(windowTitleHint());
				infoFollow(moved);
			}
		}, "jra-follow-desktop").start();
	}

	/**
	 * Windows：原版 JRA —— Ctrl+Win 发键后 hide()+show()。
	 * Linux：上一版能用的 —— xdotool 按编号 set_desktop，并先迁本窗。
	 */
	public static void switchAndFollow(final Robot robot, final HostActions.DesktopNav nav) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (Tools.isWindows()) {
					if (!winSwitching.compareAndSet(false, true)) {
						return;
					}
					try {
						switchWindowsDesktop(robot, nav);
					} finally {
						winSwitching.set(false);
					}
					return;
				}
				if (!linuxSwitching.compareAndSet(false, true)) {
					return;
				}
				try {
					switchLinuxDesktop(robot, nav);
				} finally {
					linuxSwitching.set(false);
				}
			}
		}, Tools.isWindows() ? "jra-win-switch" : "jra-linux-switch").start();
	}

	/**
	 * Linux：X11 有 xdotool/wmctrl 则按编号切并迁窗；否则 Robot 发热键，等 DE 切完再新建 Stage。
	 * 只发热键不迁窗时，GNOME 会把视图拽回旧工作区，看起来像切不了。
	 */
	private static void switchLinuxDesktop(final Robot robot, final HostActions.DesktopNav nav) {
		HostActions.SwitchResult linux = HostActions.switchByDesktopIndex(nav);
		if (linux == HostActions.SwitchResult.AT_EDGE) {
			return;
		}
		if (linux == HostActions.SwitchResult.SWITCHED) {
			final int[] pos = HostActions.lastLinuxDesktopPos();
			fxWait(new Runnable() {
				@Override
				public void run() {
					showFront();
					if (pos != null) {
						HostActions.toastDesktopPos(pos[0], pos[1]);
					}
				}
			});
			return;
		}
		System.out.println(Tools.isWayland()
				? "Wayland：xdotool/wmctrl 不能切真工作区，改用热键 + 重建窗口"
				: "无 xdotool/wmctrl：改用热键 + 重建窗口");
		fxWait(new Runnable() {
			@Override
			public void run() {
				DesktopHotkeys.fireSwitchDesktop(robot, nav);
			}
		});
		sleepQuiet(250);
		fxWait(new Runnable() {
			@Override
			public void run() {
				showFront();
				restorePreferredTop();
				Dialogs.toast(nav == HostActions.DesktopNav.NEW ? "已新建桌面" : "已切换桌面");
			}
		});
	}

	private static void switchWindowsDesktop(final Robot robot, final HostActions.DesktopNav nav) {
		int[] before = HostActions.windowsPosForNav();
		if (nav != HostActions.DesktopNav.NEW && before != null && before[0] > 0) {
			if (nav == HostActions.DesktopNav.PREV && before[0] <= 1) {
				System.out.println("已在第一个桌面，不切换 (" + before[0] + "/" + before[1] + ")");
				HostActions.toastAtDesktopEdge(nav, before[0], before[1]);
				return;
			}
			if (nav == HostActions.DesktopNav.NEXT && before[0] >= before[1]) {
				System.out.println("已在最后一个桌面，不切换 (" + before[0] + "/" + before[1] + ")");
				HostActions.toastAtDesktopEdge(nav, before[0], before[1]);
				return;
			}
		}
		fxWait(new Runnable() {
			@Override
			public void run() {
				DesktopHotkeys.fireSwitchDesktop(robot, nav);
			}
		});
		sleepQuiet(250);
		fxWait(new Runnable() {
			@Override
			public void run() {
				showFront();
			}
		});
		sleepQuiet(80);
		int[] after = HostActions.windowsDesktopPos();
		if (after != null && after[0] > 0) {
			HostActions.rememberWindowsPos(after);
			System.out.println("Windows 桌面 " + after[0] + "/" + after[1]);
		} else if (after != null && after[1] > 0 && before != null && before[0] > 0) {
			int guess = before[0];
			if (nav == HostActions.DesktopNav.NEXT) {
				guess = Math.min(before[1], before[0] + 1);
			} else if (nav == HostActions.DesktopNav.PREV) {
				guess = Math.max(1, before[0] - 1);
			} else {
				guess = before[1] + 1;
			}
			after = new int[] { guess, nav == HostActions.DesktopNav.NEW ? before[1] + 1 : after[1] };
			HostActions.rememberWindowsPos(after);
			System.out.println("Windows 桌面 " + after[0] + "/" + after[1] + "（编号按切向推算）");
		}
		final int[] toastPos = after;
		final int[] beforeSnap = before;
		fxWait(new Runnable() {
			@Override
			public void run() {
				if (toastPos != null && toastPos[1] > 0) {
					boolean noMove = nav != HostActions.DesktopNav.NEW && beforeSnap != null
							&& beforeSnap[0] > 0 && toastPos[0] > 0
							&& toastPos[0] == beforeSnap[0] && toastPos[1] == beforeSnap[1];
					boolean wrappedPrev = nav == HostActions.DesktopNav.PREV && beforeSnap != null
							&& beforeSnap[0] <= 1 && toastPos[0] > beforeSnap[0];
					boolean wrappedNext = nav == HostActions.DesktopNav.NEXT && beforeSnap != null
							&& beforeSnap[0] >= beforeSnap[1] && toastPos[0] < beforeSnap[0];
					if (noMove || wrappedPrev || wrappedNext) {
						HostActions.toastAtDesktopEdge(nav,
								toastPos[0] > 0 ? toastPos[0] : beforeSnap[0], toastPos[1]);
					} else {
						HostActions.toastDesktopPos(toastPos[0], toastPos[1]);
					}
					return;
				}
				Dialogs.toast(nav == HostActions.DesktopNav.NEW ? "已新建桌面" : "已切换桌面");
			}
		});
	}

	public static void switchAndFollowByHide(final Robot robot, final HostActions.DesktopNav nav) {
		switchAndFollow(robot, nav);
	}

	public static void switchAndFollowWindows(final Robot robot, final HostActions.DesktopNav nav) {
		switchAndFollow(robot, nav);
	}

	private static void fxWait(final Runnable action) {
		final CountDownLatch done = new CountDownLatch(1);
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				try {
					action.run();
				} finally {
					done.countDown();
				}
			}
		});
		try {
			done.await(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static String windowTitleHint() {
		if (sstage != null && sstage.getTitle() != null && !sstage.getTitle().isEmpty()) {
			return sstage.getTitle();
		}
		return "远程协助";
	}

	private static void infoFollow(boolean moved) {
		// 仅日志；主界面 info 由调用方可另设
		System.out.println(moved ? "JRA 已跟到当前工作区" : "未能迁移工作区（仍尝试前置）");
	}

	private static String linuxDesktopToolStatus() {
		boolean xdo = Tools.which("xdotool") != null;
		boolean wm = Tools.which("wmctrl") != null;
		if (Tools.isWayland()) {
			return "Wayland 用系统热键切工作区（xdotool/wmctrl 无效，不用装）";
		}
		if (xdo || wm) {
			return "工作区工具: " + (xdo ? "xdotool" : "") + (xdo && wm ? "+" : "") + (wm ? "wmctrl" : "")
					+ "（两个装一个即可）";
		}
		return "未安装 xdotool/wmctrl：切工作区用热键并重建窗口跟随。可选：python3 init/install.py  或  sudo apt install xdotool wmctrl";
	}

	private static void sleepQuiet(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static Parent loadFXML(String fxml) throws IOException {
		FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
		return fxmlLoader.load();
	}

	public static void main(String[] args) {
		launch(args);
	}
}
