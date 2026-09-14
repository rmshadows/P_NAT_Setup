package application;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.robot.Robot;

public class PrimaryController implements Initializable {
	// 默认连接地址
	private static String defaultRemoteHostAddress = "";
	// 用于停止执行VNC服务的启动
	private final static String EXIT_CODE = "ERROR";
	private static Robot robot = new Robot();

	private static boolean isCtrlPressed = false;
	private static boolean isShiftPressed = false;
	private static boolean isWindowsPressed = false;
	private static boolean isAltPressed = false;
	/** 松开修饰键时禁止 App 里「松开又按回去」的逻辑。 */
	private static volatile boolean suppressModRepress = false;

	private static Button sCtrlLock = new Button();
	private static Button sShiftLock = new Button();
	private static Button sAltLock = new Button();
	private static Button sWinLock = new Button();
	/** 每个辅助按钮各自延时（秒），滚轮只改当前按钮。 */
	private final Map<Button, Integer> delayByButton = new IdentityHashMap<Button, Integer>();
	private enum DeskPhase { IDLE, STARTING, RUNNING, STOPPING }
	private DeskPhase deskPhase;
	private static Timeline deskWatch;
	@SuppressWarnings("unused")
	private static boolean flag = true;

	@FXML
	private VBox vb;
	@FXML
	private Label info;
	@FXML
	private Label appTitle;
	@FXML
	private Label desktopStatus;
	@FXML
	private Label desktopEngine;
	@FXML
	private ComboBox<LocalIps.Item> localIp;
	/** 避免 IP 下拉初始化时写回配置。 */
	private boolean localIpReady;

	@FXML
	private SplitMenuButton LoadVNC;
	@FXML
	private Button AdvancedDeploy;
	@FXML
	private Button ShutdownVNC;
	@FXML
	private Button SystemMonitor;
	@FXML
	private Button ShowDesktop;
	@FXML
	private Button DesktopsRolling;
	@FXML
	private Button InputmethodSwitch;
	@FXML
	private Button CtrlLock = sCtrlLock;
	@FXML
	private Button ShiftLock = sShiftLock;
	@FXML
	private Button AltLock = sAltLock;
	@FXML
	private Button WinLock = sWinLock;
	@FXML
	private Button AHK;
	@FXML
	private Button Keyboard;
	@FXML
	private Button UnlockMods;
	@FXML
	private Button DesktopSettings;
	@FXML
	private Button linuxDepsBtn;
	@FXML
	private Button About;
	@FXML
	private CheckBox pinTop;
	
	/**
	 * 初始化
	 */
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		AppConf appConf = AppConf.load();
		if (appConf.file() != null) {
			defaultRemoteHostAddress = appConf.defaultRemoteHost();
			System.out.println("共用配置: " + appConf.file());
			System.out.println("包根: " + appConf.root());
			System.out.println("res: " + PnatPaths.resDir());
			System.out.println("默认连接地址：" + defaultRemoteHostAddress);
		} else {
			File conf_file = new File("jra.properties");
			if (conf_file.exists()) {
				Properties config = new Properties();
				try {
					config.load(new FileInputStream(conf_file));
					defaultRemoteHostAddress = config.getProperty("DefaultIP", "");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			System.out.println("未找到 conf/app.conf（请从仓库根运行或设置 PNAT_ROOT）。回退 DefaultIP=" + defaultRemoteHostAddress);
		}

		bindUi();
		bindLocalIp();
		refreshDesktopSummary();
		if (!App.start_up) {
			try {
				getIPaddr();
			} catch (SocketException e1) {
				System.out.println(String.format("[%s]获取IP出错：%s",
						LocalDateTime.now().toString().replace("T", "|"), e1.toString()));
			}
		}
		App.start_up = true;
	}

	private void bindUi() {
		pinTop.setSelected(App.preferAlwaysOnTop());
		pinTop.setOnAction(e -> App.setPreferTop(pinTop.isSelected()));
		LoadVNC.setMaxWidth(Double.MAX_VALUE);
		LoadVNC.setOnContextMenuRequested(e -> {
			e.consume();
			openDeploySettings(Tools.desktopMenuId());
		});
		bindLoadEngineMenu();
		if (AdvancedDeploy != null && !Tools.isWindows()) {
			AdvancedDeploy.setVisible(false);
			AdvancedDeploy.setManaged(false);
		}
		refreshLinuxDepsButton();
		bindShowDesktopButton();
		bindWorkspaceButton();
		bindAssistHotkeyButtons();
		AHK.setManaged(Session.logicalWindows());
		AHK.setVisible(Session.logicalWindows());
		AppConf delays = AppConf.load();
		initDelay(SystemMonitor, delayFromConf(delays, "delay_system_monitor", 0));
		initDelay(ShowDesktop, delayFromConf(delays, "delay_show_desktop", 0));
		initDelay(DesktopsRolling, delayFromConf(delays, "delay_desktops", 0));
		initDelay(InputmethodSwitch, delayFromConf(delays, "delay_ime", 2));
		initDelay(Keyboard, delayFromConf(delays, "delay_keyboard", 0));
		if (Session.logicalWindows()) {
			initDelay(AHK, delayFromConf(delays, "delay_ahk", 5));
		}
		// 工作区按钮：滚轮改切桌面，不再用来调延时（延时仍可从 conf 改）
		bindWorkspaceScroll();
		refreshTips();
		startDeskWatch();
		App.refreshTitle();
		applyExperienceChrome();
	}

	private void bindLoadEngineMenu() {
		if (LoadVNC == null) {
			return;
		}
		LoadVNC.getItems().clear();
		String cur = Tools.desktopMenuId();
		addEngineItem("Dayon", "dayon", cur);
		addEngineItem("VNC", "vnc", cur);
		if (Session.logicalWindows()) {
			addEngineItem("RDP", "rdp", cur);
		}
	}

	private void addEngineItem(String label, String id, String current) {
		Label name = new Label(label);
		name.getStyleClass().add("engine-menu-name");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		Label mark = new Label(id.equals(current) ? "✓" : "");
		mark.getStyleClass().add("engine-menu-mark");
		HBox row = new HBox(8, name, spacer, mark);
		row.setAlignment(Pos.CENTER_LEFT);
		row.setMinWidth(176);
		row.setPrefWidth(176);
		row.setMaxWidth(Double.MAX_VALUE);
		row.getStyleClass().add("engine-menu-row");
		if (id.equals(current)) {
			row.getStyleClass().add("selected");
		}
		Tooltip.install(row, new Tooltip("左键选用 · 右击打开" + label + "高级设置"));
		CustomMenuItem item = new CustomMenuItem(row, false);
		item.getStyleClass().add("engine-menu-item");
		row.setOnMouseClicked(e -> {
			if (e.getButton() != MouseButton.PRIMARY) {
				e.consume();
				return;
			}
			e.consume();
			LoadVNC.hide();
			selectEngine(id);
		});
		row.setOnContextMenuRequested(e -> {
			e.consume();
			openEngineSettings(id);
		});
		LoadVNC.getItems().add(item);
	}

	private void openEngineSettings(String id) {
		LoadVNC.hide();
		Platform.runLater(() -> openDeploySettings(id));
	}

	private void selectEngine(String id) {
		try {
			AppConf c = AppConf.load();
			c.set("desktop", id);
			c.save();
			bindLoadEngineMenu();
			refreshDesktopSummary();
			refreshTips();
		} catch (IOException ex) {
			Dialogs.warn("保存失败：\n" + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
		}
	}

	private void openDeploySettings(String engineId) {
		DeploySettingsController.showFor(engineId);
		bindLoadEngineMenu();
		refreshDesktopSummary();
		refreshTips();
		App.refreshTitle();
	}

	/** 系统监视器 / 输入法 / 屏幕键盘：短按执行，长按 1s 选/录热键。 */
	private void bindAssistHotkeyButtons() {
		bindShortClickOrLongPress(SystemMonitor, new ShortOrLong() {
			@Override
			public void onShort(MouseButton button) {
				afterDelay(SystemMonitor, new Runnable() {
					@Override
					public void run() {
						if (drySkipHost(buttonLabel(SystemMonitor))) {
							return;
						}
						HostActions.openTaskManager(robot);
					}
				});
			}

			@Override
			public void onLong() {
				info.setText("选择系统监视器热键…");
				AssistHotkeys.pickMonitorHotkey(new Runnable() {
					@Override
					public void run() {
						refreshTips();
						info.setText("系统监视器：" + AssistHotkeys.monitorLabel());
					}
				});
			}
		});
		bindShortClickOrLongPress(InputmethodSwitch, new ShortOrLong() {
			@Override
			public void onShort(MouseButton button) {
				afterDelay(InputmethodSwitch, new Runnable() {
					@Override
					public void run() {
						if (drySkipHost("切换输入法")) {
							return;
						}
						HostActions.toggleIme(robot);
					}
				});
			}

			@Override
			public void onLong() {
				info.setText("选择输入法热键…");
				AssistHotkeys.pickImeHotkey(new Runnable() {
					@Override
					public void run() {
						refreshTips();
						info.setText("输入法热键：" + AssistHotkeys.imeLabel());
					}
				});
			}
		});
		bindShortClickOrLongPress(Keyboard, new ShortOrLong() {
			@Override
			public void onShort(MouseButton button) {
				afterDelay(Keyboard, new Runnable() {
					@Override
					public void run() {
						if (drySkipHost("屏幕键盘")) {
							return;
						}
						HostActions.openOnScreenKeyboard(robot);
					}
				});
			}

			@Override
			public void onLong() {
				info.setText("选择屏幕键盘热键…");
				AssistHotkeys.pickOskHotkey(new Runnable() {
					@Override
					public void run() {
						refreshTips();
						info.setText("屏幕键盘：" + AssistHotkeys.oskLabel());
					}
				});
			}
		});
	}

	/** 显示桌面：短按执行；长按 1s 选/录热键。 */
	private void bindShowDesktopButton() {
		if (ShowDesktop == null) {
			return;
		}
		ShowDesktop.setContextMenu(null);
		bindShortClickOrLongPress(ShowDesktop, new ShortOrLong() {
			@Override
			public void onShort(MouseButton button) {
				runShowDesktop();
			}

			@Override
			public void onLong() {
				info.setText("选择显示桌面热键…");
				DesktopHotkeys.pickShowHotkey(new Runnable() {
					@Override
					public void run() {
						refreshTips();
						info.setText("显示桌面热键：" + DesktopHotkeys.showLabel());
					}
				});
			}
		});
	}

	/**
	 * 切换工作区：滚轮前后；左键下一、右键上一、中键新建；
	 * 长按 1s（任意键）选/录热键；切完后 JRA 跟到当前工作区。
	 */
	private void bindWorkspaceButton() {
		if (DesktopsRolling == null) {
			return;
		}
		DesktopsRolling.setContextMenu(null);
		bindShortClickOrLongPress(DesktopsRolling, new ShortOrLong() {
			@Override
			public void onShort(MouseButton button) {
				HostActions.DesktopNav nav;
				if (button == MouseButton.PRIMARY) {
					nav = HostActions.DesktopNav.NEXT;
				} else if (button == MouseButton.SECONDARY) {
					nav = HostActions.DesktopNav.PREV;
				} else if (button == MouseButton.MIDDLE) {
					nav = HostActions.DesktopNav.NEW;
				} else {
					return;
				}
				runSwitchDesktop(nav);
			}

			@Override
			public void onLong() {
				info.setText("选择切换工作区热键…");
				DesktopHotkeys.pickSwitchHotkey(delayOf(DesktopsRolling), new Runnable() {
					@Override
					public void run() {
						delayByButton.put(DesktopsRolling,
								delayFromConf(AppConf.load(), "delay_desktops", delayOf(DesktopsRolling)));
						refreshTips();
						info.setText("切换工作区：" + DesktopHotkeys.switchLabel()
								+ " · 延时 " + delayOf(DesktopsRolling) + "s");
					}
				});
			}
		});
	}

	private void bindWorkspaceScroll() {
		if (DesktopsRolling == null) {
			return;
		}
		DesktopsRolling.setOnScroll(e -> {
			e.consume();
			if (e.getDeltaY() > 0) {
				runSwitchDesktop(HostActions.DesktopNav.PREV);
			} else if (e.getDeltaY() < 0) {
				runSwitchDesktop(HostActions.DesktopNav.NEXT);
			}
		});
	}

	private void runShowDesktop() {
		afterDelay(ShowDesktop, new Runnable() {
			@Override
			public void run() {
				if (drySkipHost("显示桌面")) {
					App.keepVisibleAfterShowDesktop();
					return;
				}
				HostActions.showDesktop(robot);
				App.keepVisibleAfterShowDesktop();
			}
		});
	}

	private void runSwitchDesktop(final HostActions.DesktopNav nav) {
		afterDelay(DesktopsRolling, new Runnable() {
			@Override
			public void run() {
				if (drySkipHost("切换工作区")) {
					return;
				}
				App.switchAndFollow(robot, nav);
			}
		});
	}

	private interface ShortOrLong {
		void onShort(MouseButton button);

		void onLong();
	}

	/** 短按 onShort；按住 ≥1s 后松开才 onLong。鼠标移出按钮即取消。 */
	private void bindShortClickOrLongPress(final Button button, final ShortOrLong handler) {
		final ProgressBar bar = ensureHoldProgress(button);
		final Timeline[] timer = new Timeline[1];
		final boolean[] longArmed = new boolean[1];
		final boolean[] cancelled = new boolean[1];
		final MouseButton[] which = new MouseButton[1];
		button.setOnMousePressed(e -> {
			e.consume();
			which[0] = e.getButton();
			longArmed[0] = false;
			cancelled[0] = false;
			if (timer[0] != null) {
				timer[0].stop();
			}
			bar.setProgress(0);
			timer[0] = new Timeline(
					new KeyFrame(Duration.ZERO, new KeyValue(bar.progressProperty(), 0)),
					new KeyFrame(Duration.seconds(1), ev -> {
						if (!cancelled[0]) {
							longArmed[0] = true;
							info.setText("松开以打开热键设置");
						}
					}, new KeyValue(bar.progressProperty(), 1, Interpolator.LINEAR)));
			timer[0].play();
		});
		button.setOnMouseReleased(e -> {
			e.consume();
			if (timer[0] != null) {
				timer[0].stop();
				timer[0] = null;
			}
			bar.setProgress(0);
			if (cancelled[0]) {
				longArmed[0] = false;
				cancelled[0] = false;
				return;
			}
			final boolean openSettings = longArmed[0];
			longArmed[0] = false;
			if (openSettings) {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						handler.onLong();
					}
				});
				return;
			}
			handler.onShort(e.getButton() == null ? which[0] : e.getButton());
		});
		button.setOnMouseExited(e -> cancelHold(timer, longArmed, cancelled, bar));
	}

	private void cancelHold(Timeline[] timer, boolean[] longArmed, boolean[] cancelled, ProgressBar bar) {
		cancelled[0] = true;
		longArmed[0] = false;
		if (timer[0] != null) {
			timer[0].stop();
			timer[0] = null;
		}
		bar.setProgress(0);
		info.setText("已取消长按");
	}

	/**
	 * 按钮底下一条细进度条。
	 * 注意：new VBox(button) 会先把 button 从原父节点摘走，索引会错位，
	 * 只能 insert，不能 set，否则会吃掉后面的「显示桌面 / 切换工作区」。
	 */
	private static ProgressBar ensureHoldProgress(Button button) {
		Object existing = button.getProperties().get("holdBar");
		if (existing instanceof ProgressBar) {
			return (ProgressBar) existing;
		}
		ProgressBar bar = new ProgressBar(0);
		bar.getStyleClass().add("hold-progress");
		bar.setMaxWidth(Double.MAX_VALUE);
		bar.setMinHeight(3);
		bar.setPrefHeight(3);
		bar.setMaxHeight(3);
		bar.setMouseTransparent(true);
		bar.setProgress(0);
		if (button.getParent() instanceof VBox) {
			VBox parent = (VBox) button.getParent();
			int idx = parent.getChildren().indexOf(button);
			VBox wrap = new VBox(button, bar);
			wrap.getStyleClass().add("hold-wrap");
			wrap.setMaxWidth(Double.MAX_VALUE);
			VBox.setVgrow(button, Priority.NEVER);
			if (idx >= 0) {
				parent.getChildren().add(idx, wrap);
			}
		}
		button.getProperties().put("holdBar", bar);
		return bar;
	}

	/** 标题 / 底栏：体验版一眼能看出，正式版恢复普通文案。 */
	private void applyExperienceChrome() {
		if (appTitle != null) {
			if (Session.dryRun()) {
				appTitle.setText("远程协助 · 体验");
			} else {
				appTitle.setText("远程协助");
			}
		}
		if (Session.dryRun()) {
			info.setText("体验版 · " + Session.logicalOsName() + " · " + Tools.resolveDesktop()
					+ "（JRA设置可关）");
		}
	}

	private void bindLocalIp() {
		if (localIp == null) {
			return;
		}
		localIpReady = false;
		List<LocalIps.Item> items = LocalIps.allItems();
		localIp.setItems(FXCollections.observableArrayList(items));
		String saved = AppConf.load().get("client_ip").trim();
		LocalIps.Item pick = LocalIps.findItem(items, saved);
		if (pick == null) {
			String guess = LocalIps.guessPrimary(items);
			pick = LocalIps.findItem(items, guess);
		}
		if (pick == null && !items.isEmpty()) {
			pick = items.get(0);
		}
		if (pick != null) {
			localIp.getSelectionModel().select(pick);
		}
		tip(localIp, "全部网卡 IPv4/IPv6。默认猜主 IPv4；选定后记住。\nIPv4 会同步 frpc local_ip；IPv6 只记 client_ip。");
		localIp.setOnAction(e -> {
			if (!localIpReady) {
				return;
			}
			LocalIps.Item it = localIp.getSelectionModel().getSelectedItem();
			if (it == null) {
				return;
			}
			try {
				String note = LocalIps.applyClientIp(it.address());
				info.setText("已记住 IP " + it.address());
				System.out.println("[client_ip]\n" + note);
			} catch (IOException ex) {
				info.setText("写入 IP 失败");
				System.err.println(ex);
			}
		});
		localIpReady = true;
		if (pick != null && (saved.isEmpty() || !saved.equals(pick.address()))) {
			try {
				System.out.println("[client_ip]\n" + LocalIps.applyClientIp(pick.address()));
			} catch (IOException ignored) {
			}
		}
	}

	/** 体验版：走启动中→运行中 / 停止中→未运行，不弹说明框。 */
	private void dryRunDesk(final String action) {
		System.out.println(DryRun.plan(action));
		if ("stop".equals(action)) {
			applyDesk(DeskPhase.STOPPING);
			info.setText("体验·正在停止…");
			new Thread(new Runnable() {
				@Override
				public void run() {
					sleepQuiet(700);
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							applyDesk(DeskPhase.IDLE);
							info.setText("体验·已停止 · " + Tools.resolveDesktop());
							App.restorePreferredTop();
						}
					});
				}
			}, "dry-stop").start();
			return;
		}
		if ("advanced".equals(action)) {
			info.setText("体验·高级部署已模拟 · " + Tools.resolveDesktop());
			return;
		}
		applyDesk(DeskPhase.STARTING);
		info.setText("体验·正在启动 " + Tools.resolveDesktop() + "…");
		App.setTop(false);
		new Thread(new Runnable() {
			@Override
			public void run() {
				sleepQuiet(900);
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						applyDesk(DeskPhase.RUNNING);
						info.setText("体验·运行中 · " + Tools.resolveDesktop()
								+ " · " + Session.logicalOsName());
						App.restorePreferredTop();
					}
				});
			}
		}, "dry-load").start();
	}

	private static void sleepQuiet(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	private void initDelay(Button button, int sec) {
		delayByButton.put(button, clampDelay(sec));
		if (button != DesktopsRolling) {
			bindDelayWheel(button);
		}
	}

	private static int delayFromConf(AppConf c, String key, int fallback) {
		String v = c.get(key).trim();
		if (v.isEmpty()) {
			return fallback;
		}
		try {
			return clampDelay(Integer.parseInt(v));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private String delayConfKey(Button button) {
		if (button == SystemMonitor) {
			return "delay_system_monitor";
		}
		if (button == ShowDesktop) {
			return "delay_show_desktop";
		}
		if (button == DesktopsRolling) {
			return "delay_desktops";
		}
		if (button == InputmethodSwitch) {
			return "delay_ime";
		}
		if (button == Keyboard) {
			return "delay_keyboard";
		}
		if (button == AHK) {
			return "delay_ahk";
		}
		return null;
	}

	private void persistDelay(Button button, int sec) {
		String key = delayConfKey(button);
		if (key == null) {
			return;
		}
		try {
			AppConf c = AppConf.load();
			c.set(key, String.valueOf(sec));
			c.save();
		} catch (IOException e) {
			System.err.println("记住延时失败: " + e);
		}
	}

	private static int clampDelay(int sec) {
		return Math.max(0, Math.min(15, sec));
	}

	private int delayOf(Button button) {
		Integer v = delayByButton.get(button);
		return v == null ? 3 : v;
	}

	private void bindDelayWheel(final Button button) {
		button.setOnScroll(e -> {
			int cur = delayOf(button);
			if (e.getDeltaY() > 0) {
				cur = clampDelay(cur + 1);
			} else if (e.getDeltaY() < 0) {
				cur = clampDelay(cur - 1);
			}
			delayByButton.put(button, cur);
			persistDelay(button, cur);
			e.consume();
			refreshTips();
			info.setText(buttonLabel(button) + " 延时 " + cur + " 秒（已记住）");
		});
	}

	private String buttonLabel(Button button) {
		if (button == SystemMonitor) {
			return Session.logicalWindows() ? "任务管理器" : "系统监视器";
		}
		if (button == ShowDesktop) {
			return "显示桌面";
		}
		if (button == DesktopsRolling) {
			return "切换工作区";
		}
		if (button == InputmethodSwitch) {
			return "切换输入法";
		}
		if (button == Keyboard) {
			return "屏幕键盘";
		}
		if (button == AHK) {
			return "AHK 脚本";
		}
		return "本按钮";
	}

	private void refreshTips() {
		if (deskPhase == DeskPhase.RUNNING) {
			tip(LoadVNC, "远程桌面正在运行。要重连请先停止再加载。\n右箭头选引擎（右击某项=该方式高级设置）\n右击本按钮=当前方式高级设置");
			tip(ShutdownVNC, "停止本次远程桌面");
		} else if (deskPhase == null || deskPhase == DeskPhase.IDLE) {
			String engines = Session.logicalWindows() ? "Dayon / VNC / RDP" : "Dayon / VNC";
			tip(LoadVNC, "左键加载当前引擎。右箭头选 " + engines + "。\n右击本按钮：当前方式高级设置\n下拉里右击某一项：该方式高级设置");
			tip(ShutdownVNC, "当前未运行");
		} else {
			tip(LoadVNC, "正在处理，请稍候");
			tip(ShutdownVNC, "正在处理，请稍候");
		}
		if (AdvancedDeploy != null) {
			tip(AdvancedDeploy, "高级部署：TightVNC 服务 / 密码 / 托盘（也可右键「加载」）。细节稍后在方案里定。");
		}
		tipDelay(SystemMonitor, HostActions.tipTaskManager());
		tipDelay(ShowDesktop, HostActions.tipShowDesktop());
		tipDelay(DesktopsRolling, HostActions.tipSwitchDesktop());
		tipDelay(InputmethodSwitch, HostActions.tipIme());
		tipDelay(Keyboard, HostActions.tipKeyboard());
		if (Tools.isWindows() || Session.logicalWindows()) {
			tipDelay(AHK, HostActions.tipAhk());
			if (AHK.isVisible()) {
				AHK.setText("AHK 脚本 · " + delayOf(AHK) + "s");
			}
		}
		String winName = "Win";
		tip(CtrlLock, HostActions.tipMod("Ctrl"));
		tip(ShiftLock, HostActions.tipMod("Shift"));
		tip(AltLock, HostActions.tipMod("Alt"));
		tip(WinLock, HostActions.tipMod(winName));
		tip(UnlockMods, "松开全部修饰键（按住时也能点；先清状态再释放，避免被重新按住）");
		tip(DesktopSettings, "JRA设置：体验版 / 托盘 / 关窗 / AHK");
		if (linuxDepsBtn != null && linuxDepsBtn.isVisible()) {
			tip(linuxDepsBtn, "查看 Linux 缺哪些包，复制 apt / init 安装命令");
		}
		tip(About, "说明、版本");
		tip(pinTop, "窗口是否始终置顶（默认开）");
		SystemMonitor.setText(buttonLabel(SystemMonitor) + " · " + delayOf(SystemMonitor) + "s");
		ShowDesktop.setText("显示桌面 · " + delayOf(ShowDesktop) + "s");
		DesktopsRolling.setText("切换工作区 · " + delayOf(DesktopsRolling) + "s");
		InputmethodSwitch.setText("切换输入法 · " + delayOf(InputmethodSwitch) + "s");
		Keyboard.setText("屏幕键盘 · " + delayOf(Keyboard) + "s");
		if (!isCtrlPressed) {
			CtrlLock.setText("CTRL");
		}
		if (!isShiftPressed) {
			ShiftLock.setText("SHIFT");
		}
		if (!isAltPressed) {
			AltLock.setText("ALT");
		}
		if (!isWindowsPressed) {
			WinLock.setText("WIN");
		}
		if (Session.dryRun()) {
			info.setText("体验版 · " + Session.logicalOsName() + " · 引擎 " + Tools.resolveDesktop());
		} else {
			info.setText("各按钮独立延时，滚轮只改当前按钮");
		}
		refreshDesktopSummary();
	}

	/** 主界面显示当前引擎 / 角色。 */
	private void refreshDesktopSummary() {
		if (desktopEngine == null) {
			return;
		}
		AppConf c = AppConf.load();
		String extra = "";
		if (Tools.useTightVnc()) {
			extra = " · " + Tools.tightvncMode();
		} else if ("vnc".equals(Tools.resolveDesktop())) {
			extra = " · " + Tools.vncDisplayTag() + " · " + Tools.vncConnectTag() + " · " + Tools.vncImplTag()
					+ " · " + Tools.vncEncryptTag();
		}
		String roleBit = Tools.useDayon() ? " · " + Tools.roleLabel(c.get("role")) : "";
		desktopEngine.setText(Tools.desktopLabel() + roleBit + extra);
	}

	private void tipDelay(Button button, String base) {
		int d = delayOf(button);
		if (button == DesktopsRolling) {
			tip(button, base + "\n倒计时 " + d + " 秒（长按菜单里改，0=立即）");
			return;
		}
		tip(button, base + "\n本按钮倒计时 " + d + " 秒（滚轮单独调节，0=立即）");
	}

	private static void tip(javafx.scene.control.Control node, String text) {
		Tooltip t = new Tooltip(text);
		t.setShowDelay(Duration.millis(220));
		t.setWrapText(true);
		t.setMaxWidth(280);
		node.setTooltip(t);
	}

	private void afterDelay(Button button, final Runnable action) {
		final int start = delayOf(button);
		final String label = buttonLabel(button);
		if (start <= 0) {
			action.run();
			return;
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				for (int left = start; left > 0; left--) {
					final int n = left;
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							info.setText(label + "：" + n + " 秒后执行");
						}
					});
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						return;
					}
				}
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						info.setText(label + " 已执行");
						action.run();
					}
				});
			}
		}, "jra-delay").start();
	}

	private void startDeskWatch() {
		if (deskWatch != null) {
			deskWatch.stop();
		}
		syncDeskFromProcess();
		deskWatch = new Timeline(new KeyFrame(Duration.seconds(2), e -> syncDeskFromProcess()));
		deskWatch.setCycleCount(Timeline.INDEFINITE);
		deskWatch.play();
	}

	private void applyDesk(DeskPhase phase) {
		if (phase == deskPhase) {
			return;
		}
		deskPhase = phase;
		if (desktopStatus == null) {
			return;
		}
		desktopStatus.getStyleClass().removeAll("badge-off", "badge-on", "badge-busy");
		if (phase == DeskPhase.RUNNING) {
			desktopStatus.setText(Session.dryRun() ? "● 体验中" : "● 运行中");
			desktopStatus.getStyleClass().add("badge-on");
			LoadVNC.setDisable(false);
			LoadVNC.setText("远程桌面运行中");
			if (AdvancedDeploy != null) {
				AdvancedDeploy.setDisable(false);
			}
			ShutdownVNC.setDisable(false);
		} else if (phase == DeskPhase.STARTING) {
			desktopStatus.setText("● 启动中…");
			desktopStatus.getStyleClass().add("badge-busy");
			LoadVNC.setDisable(false);
			LoadVNC.setText("正在启动…");
			if (AdvancedDeploy != null) {
				AdvancedDeploy.setDisable(true);
			}
			ShutdownVNC.setDisable(true);
		} else if (phase == DeskPhase.STOPPING) {
			desktopStatus.setText("● 停止中…");
			desktopStatus.getStyleClass().add("badge-busy");
			LoadVNC.setDisable(false);
			LoadVNC.setText("正在停止…");
			if (AdvancedDeploy != null) {
				AdvancedDeploy.setDisable(true);
			}
			ShutdownVNC.setDisable(true);
		} else {
			desktopStatus.setText("○ 未运行");
			desktopStatus.getStyleClass().add("badge-off");
			LoadVNC.setDisable(false);
			LoadVNC.setText("加载远程桌面");
			if (AdvancedDeploy != null) {
				AdvancedDeploy.setDisable(false);
			}
			ShutdownVNC.setDisable(true);
		}
		refreshTips();
	}

	private void syncDeskFromProcess() {
		if (Session.dryRun()) {
			// 体验版用按钮自己切换状态，不查真实进程
			return;
		}
		boolean live = Tools.isDesktopRunning();
		if (deskPhase == DeskPhase.STARTING) {
			if (live) {
				applyDesk(DeskPhase.RUNNING);
			}
		} else if (deskPhase == DeskPhase.STOPPING) {
			if (!live) {
				applyDesk(DeskPhase.IDLE);
			}
		} else {
			applyDesk(live ? DeskPhase.RUNNING : DeskPhase.IDLE);
		}
	}

	/**
	 * 运行VNC服务
	 * 
	 * @param event
	 * @throws InterruptedException
	 */
	@FXML
	void loadVNC(ActionEvent event) throws InterruptedException {
		if (deskPhase == DeskPhase.STARTING || deskPhase == DeskPhase.RUNNING
				|| deskPhase == DeskPhase.STOPPING) {
			return;
		}
		if (Session.dryRun()) {
			dryRunDesk("load");
			return;
		}
		App.setTop(false);
		DeployResult pre = Tools.preflightSimple();
		if (!pre.ok) {
			showDeployFail("预检未通过，未执行部署。\n" + pre.message);
			return;
		}
		if (Tools.useDayon()) {
			System.out.println("调用 Dayon（共用 conf/app.conf）");
			applyDesk(DeskPhase.STARTING);
			new Thread(new Runnable() {
				@Override
				public void run() {
					final DeployResult r = Tools.runDayon();
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							if (!r.ok) {
								applyDesk(Tools.isDesktopRunning() ? DeskPhase.RUNNING : DeskPhase.IDLE);
								showDeployFail("Dayon 启动失败。\n" + r.message);
							} else {
								applyDesk(Tools.isDesktopRunning() ? DeskPhase.RUNNING : DeskPhase.IDLE);
							}
						}
					});
				}
			}, "dayon-start").start();
			App.restorePreferredTop();
			return;
		}
		if (Tools.useLinuxVnc()) {
			System.out.println("调用 Linux VNC（deploy/vnc/install.py）");
			applyDesk(DeskPhase.STARTING);
			new Thread(new Runnable() {
				@Override
				public void run() {
					final DeployResult r = Tools.runLinuxVnc();
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							if (!r.ok) {
								applyDesk(Tools.isDesktopRunning() ? DeskPhase.RUNNING : DeskPhase.IDLE);
								showDeployFail("VNC 启动失败。\n" + r.message);
							} else {
								applyDesk(Tools.isDesktopRunning() ? DeskPhase.RUNNING : DeskPhase.IDLE);
							}
						}
					});
				}
			}, "vnc-start").start();
			App.restorePreferredTop();
			return;
		}
		if (!Tools.useTightVnc()) {
			showDeployFail("当前方式不可加载：" + Tools.desktopLabel()
					+ "\n请用加载按钮右侧箭头改选 Dayon，或右击打开高级设置。");
			return;
		}
		if (Tools.tightvncServiceMode()) {
			Dialogs.info("提示", "当前部署方式为 service",
					"系统服务请用「高级部署…」。\n若要用便携加载，右击加载按钮把 TightVNC 部署方式改为 portable。");
			App.restorePreferredTop();
			return;
		}
		applyDesk(DeskPhase.STARTING);
		setupVNC();
		syncDeskFromProcess();
	}

	/**
	 * 右键 / 高级部署。TightVNC service：install.bat；Dayon：提示无服务档。
	 */
	void loadVNCAdvanced() {
		if (Session.dryRun()) {
			dryRunDesk("advanced");
			return;
		}
		if (Tools.useDayon() || Tools.useLinuxVnc()) {
			Dialogs.info("高级部署", Tools.useLinuxVnc() ? "Linux VNC 无系统服务档" : "Dayon 无系统服务档",
					"请用「加载远程桌面 / 停止远程桌面」。\n或右击加载按钮改高级设置。");
			return;
		}
		DeployResult pre = Tools.preflightAdvanced();
		if (!pre.ok) {
			showDeployFail("高级部署预检未通过。\n" + pre.message);
			return;
		}
		TightVncConf tvn = TightVncConf.load();
		String modeHint = Tools.tightvncServiceMode() ? "" : "\n（当前 tightvnc_mode=portable，仍可装服务；建议高级设置里改为 service）";
		if (!Dialogs.confirm("高级部署", "将按高级设置里的 TightVNC 属性安装系统服务",
				tvn.summary() + modeHint
						+ "\n\n会请求管理员权限。受助端若只要便携连出，请取消，改用左键「加载」。")) {
			return;
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				final DeployResult r = Tools.runTightVncAdvanced();
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						if (r.ok) {
							Dialogs.info("高级部署", "安装脚本已结束",
									"密码和托盘来自高级设置（右击加载按钮）→ conf/tightvnc.conf。\n若刚弹出过 UAC，请确认管理员窗口也已装完。");
						} else {
							showDeployFail("高级部署失败。\n" + r.message);
						}
					}
				});
			}
		}, "tvn-advanced").start();
	}

	@FXML
	void shutdownVNC(ActionEvent event) throws InterruptedException {
		if (Session.dryRun()) {
			dryRunDesk("stop");
			return;
		}
		if (Tools.useDayon()) {
			applyDesk(DeskPhase.STOPPING);
			new Thread(new Runnable() {
				@Override
				public void run() {
					final DeployResult r = Tools.runDayon("--stop");
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							applyDesk(Tools.isDesktopRunning() ? DeskPhase.RUNNING : DeskPhase.IDLE);
							if (!r.ok) {
								showDeployFail("Dayon 停止失败。\n" + r.message);
							}
						}
					});
				}
			}, "dayon-stop").start();
			App.restorePreferredTop();
			return;
		}
		if (Tools.useLinuxVnc()) {
			applyDesk(DeskPhase.STOPPING);
			new Thread(new Runnable() {
				@Override
				public void run() {
					final DeployResult r = Tools.runLinuxVnc("--stop");
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							applyDesk(Tools.isDesktopRunning() ? DeskPhase.RUNNING : DeskPhase.IDLE);
							if (!r.ok) {
								showDeployFail("VNC 停止失败。\n" + r.message);
							}
						}
					});
				}
			}, "vnc-stop").start();
			App.restorePreferredTop();
			return;
		}
		applyDesk(DeskPhase.STOPPING);
		removeVNC();
		syncDeskFromProcess();
		App.restorePreferredTop();
	}

	/** 体验版：本机辅助 / 真按键不落地，只改界面反馈。 */
	private boolean drySkipHost(String name) {
		if (!Session.dryRun()) {
			return false;
		}
		info.setText("体验·" + name + "（未真触发本机）");
		System.out.println("[dry-run] skip host: " + name);
		return true;
	}

	@FXML
	void openSettings(ActionEvent event) throws IOException {
		App.setRoot("settings");
	}

	@FXML
	void openLinuxDeps(ActionEvent event) {
		Dialogs.showLinuxDeps();
		refreshLinuxDepsButton();
	}

	private void refreshLinuxDepsButton() {
		if (linuxDepsBtn == null) {
			return;
		}
		if (Tools.isWindows()) {
			linuxDepsBtn.setVisible(false);
			linuxDepsBtn.setManaged(false);
			return;
		}
		linuxDepsBtn.setVisible(true);
		linuxDepsBtn.setManaged(true);
		linuxDepsBtn.setText(LinuxDeps.buttonLabel());
	}

	@FXML
	void advancedDeploy(ActionEvent event) {
		loadVNCAdvanced();
	}

	@FXML
	void ctrl(MouseEvent event) {
		toggleMod(KeyCode.CONTROL, "CTRL", isCtrlPressed, new Runnable() {
			@Override
			public void run() {
				isCtrlPressed = !isCtrlPressed;
				setLockButton(CtrlLock, isCtrlPressed, "CTRL 按住中", "CTRL");
			}
		});
	}

	@FXML
	void shift(MouseEvent event) {
		toggleMod(KeyCode.SHIFT, "SHIFT", isShiftPressed, new Runnable() {
			@Override
			public void run() {
				isShiftPressed = !isShiftPressed;
				setLockButton(ShiftLock, isShiftPressed, "SHIFT 按住中", "SHIFT");
			}
		});
	}

	@FXML
	void alt(MouseEvent event) {
		toggleMod(KeyCode.ALT, "ALT", isAltPressed, new Runnable() {
			@Override
			public void run() {
				isAltPressed = !isAltPressed;
				setLockButton(AltLock, isAltPressed, "ALT 按住中", "ALT");
			}
		});
	}

	@FXML
	void win(MouseEvent event) {
		toggleMod(KeyCode.WINDOWS, "WIN", isWindowsPressed, new Runnable() {
			@Override
			public void run() {
				isWindowsPressed = !isWindowsPressed;
				setLockButton(WinLock, isWindowsPressed, "WIN 按住中", "WIN");
			}
		});
	}

	@FXML
	void unlockAllMods(MouseEvent event) {
		event.consume();
		releaseAllMods();
		info.setText("已松开全部修饰键");
	}

	private void toggleMod(KeyCode code, String name, boolean currentlyDown, Runnable flip) {
		if (Session.dryRun()) {
			flip.run();
			info.setText("体验·" + name + (currentlyDown ? " 松开" : " 按住") + "（未真按本机键）");
			System.out.println("[dry-run] mod " + name + " → " + (currentlyDown ? "up" : "down"));
			return;
		}
		if (currentlyDown) {
			suppressModRepress = true;
			try {
				flip.run();
				robot.keyRelease(code);
			} finally {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						suppressModRepress = false;
					}
				});
			}
		} else {
			robot.keyPress(code);
			flip.run();
		}
		System.out.println(String.format("[%s]%s 修饰键 → %s", LocalDateTime.now().toString().replace("T", "|"), name,
				currentlyDown ? "松开" : "按住"));
	}

	private void releaseAllMods() {
		suppressModRepress = true;
		boolean any = isCtrlPressed || isShiftPressed || isAltPressed || isWindowsPressed;
		// 先改标志，再释放，避免 App 的 KEY_RELEASED 又把键按回去
		boolean c = isCtrlPressed;
		boolean s = isShiftPressed;
		boolean a = isAltPressed;
		boolean w = isWindowsPressed;
		isCtrlPressed = false;
		isShiftPressed = false;
		isAltPressed = false;
		isWindowsPressed = false;
		setLockButton(CtrlLock, false, "CTRL 按住中", "CTRL");
		setLockButton(ShiftLock, false, "SHIFT 按住中", "SHIFT");
		setLockButton(AltLock, false, "ALT 按住中", "ALT");
		setLockButton(WinLock, false, "WIN 按住中", "WIN");
		if (Session.dryRun()) {
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					suppressModRepress = false;
					info.setText(any ? "体验·已松开全部修饰键" : "当前没有按住的修饰键");
				}
			});
			return;
		}
		try {
			if (c) {
				robot.keyRelease(KeyCode.CONTROL);
			}
			if (s) {
				robot.keyRelease(KeyCode.SHIFT);
			}
			if (a) {
				robot.keyRelease(KeyCode.ALT);
			}
			if (w) {
				robot.keyRelease(KeyCode.WINDOWS);
			}
			// 再多释放几次，防止某些 DE 粘住
			robot.keyRelease(KeyCode.CONTROL);
			robot.keyRelease(KeyCode.SHIFT);
			robot.keyRelease(KeyCode.ALT);
			robot.keyRelease(KeyCode.WINDOWS);
		} finally {
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					suppressModRepress = false;
					if (!any) {
						info.setText("当前没有按住的修饰键");
					}
				}
			});
		}
	}

	private static void setLockButton(Button button, boolean locked, String onText, String offText) {
		button.setText(locked ? onText : offText);
		button.getStyleClass().remove("locked");
		if (locked) {
			button.getStyleClass().add("locked");
		}
	}

	@FXML
	void ahks(MouseEvent event) {
		if (!Session.logicalWindows()) {
			return;
		}
		if (event.getButton() == MouseButton.SECONDARY) {
			event.consume();
			chooseAhkDir();
			return;
		}
		Path dir = AhkPaths.scriptDir();
		if (!Files.isDirectory(dir)) {
			if (!Dialogs.confirm("AHK", "还没有脚本目录",
					"默认应在包根 res/windows/AHK/。\n是否手动选择文件夹？")) {
				return;
			}
			if (!chooseAhkDir()) {
				return;
			}
		}
		String def = AppConf.load().get("ahk_script").trim();
		if (def.isEmpty()) {
			def = "sample";
		}
		String title = Session.dryRun() ? "AHK 脚本（体验·不真跑）" : "AHK 脚本";
		final String filename = Dialogs.pickAhkScript(title, def);
		if (filename == null || filename.isEmpty()) {
			return;
		}
		try {
			AppConf c = AppConf.load();
			c.set("ahk_script", filename);
			c.save();
		} catch (IOException ignored) {
		}
		final Path script = AhkPaths.scriptFile(filename);
		final Path exe = AhkPaths.exe();
		if (Session.dryRun()) {
			afterDelay(AHK, new Runnable() {
				@Override
				public void run() {
					info.setText("体验·AHK " + script.getFileName() + "（未真触发）");
					System.out.println("[dry-run] AHK exe=" + exe + " script=" + script);
				}
			});
			return;
		}
		afterDelay(AHK, new Runnable() {
			@Override
			public void run() {
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							if (!Files.isRegularFile(exe)) {
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										Dialogs.warn("找不到 AHK.exe：\n" + exe
												+ "\n应在 res/windows/AHK/，或设 ahk_exe=");
									}
								});
								return;
							}
							if (!Files.isRegularFile(script)) {
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										Dialogs.warn("找不到脚本：\n" + script);
									}
								});
								return;
							}
							ProcessBuilder pb = new ProcessBuilder(exe.toAbsolutePath().toString(),
									script.toAbsolutePath().toString());
							pb.directory(script.getParent() == null ? null : script.getParent().toFile());
							Process cmd = pb.start();
							System.out.println(String.format("[%s]%s 退出码：%d",
									LocalDateTime.now().toString().replace("T", "|"), script.getFileName(),
									cmd.waitFor()));
						} catch (Exception e) {
							System.out.println(String.format("[%s]AHK 执行错误：%s",
									LocalDateTime.now().toString().replace("T", "|"), e.toString()));
						}
					}
				}, "ahk-run").start();
			}
		});
	}

	/** @return 是否选到了目录 */
	private boolean chooseAhkDir() {
		Path cur = AhkPaths.scriptDir();
		Path picked = Dialogs.chooseDirectory("选择 AHK 脚本文件夹", cur);
		if (picked == null) {
			return false;
		}
		try {
			AhkPaths.saveScriptDir(picked);
			info.setText("AHK 目录：" + picked);
			tipDelay(AHK, HostActions.tipAhk());
			return true;
		} catch (IOException e) {
			Dialogs.warn("保存 ahk_dir 失败：\n" + e.getMessage());
			return false;
		}
	}

	@FXML
	private void switchToSecondary() throws IOException {
		if (isCtrlPressed || isShiftPressed || isWindowsPressed || isAltPressed) {
			releaseAllMods();
		}
		App.setRoot("secondary");
	}

	private void showDeployFail(String message) {
		Dialogs.error("部署失败", message);
	}

	/**
	 * 启动VNC
	 * 
	 * @throws InterruptedException
	 */
	private void setupVNC() throws InterruptedException {
		// 检查VNC服务是否注册
		if (isServiceReg()) {
			System.out.println(String.format("[%s]VNC服务已注册。", LocalDateTime.now().toString().replace("T", "|")));
			// 服务已注册，VNC已启动，直接尝试连接。
			if (isVNCon()) {
				System.out.println(
						String.format("[%s]检测到VNC服务进程，开始直连……", LocalDateTime.now().toString().replace("T", "|")));
				// 连接远程主机
				if (servConnect() == 0) {
					// 成功就返回
					System.out.println(String.format("[%s]====>>>>连接远程主机完成<<<<====",
							LocalDateTime.now().toString().replace("T", "|")));
					return;
				} else {
					// 不成功就等待2秒再次尝试
					System.out.println(String.format("[%s]servConnect返回值不为零，2秒后重试。",
							LocalDateTime.now().toString().replace("T", "|")));
					Thread.sleep(1000);
					// 如果连接返回值不为零，弹出警示框
					if (servConnect() != 0) {
						System.out.println(String.format("[%s]servConnect返回值不为零，请先关闭远程桌面!",
								LocalDateTime.now().toString().replace("T", "|")));
						Dialogs.warn("远程主机连接失败。");
					}
				}
			}
			// 服务已注册，VNC未启动，尝试启动服务，并连接。
			else {
				System.out.println(
						String.format("[%s]未检测到VNC服务进程，启动中……", LocalDateTime.now().toString().replace("T", "|")));
				if (startServ() == 0) {
					System.out.println(String.format("[%s]====>>>>服务启动完成<<<<====",
							LocalDateTime.now().toString().replace("T", "|")));
					if (isVNCon()) {
						System.out.println(String.format("[%s]检测到VNC服务进程，开始连接……",
								LocalDateTime.now().toString().replace("T", "|")));
						setupVNC();
						return;
					}
				} else {
					System.out.println(String.format("[%s]startServ返回值不为零，2秒后进行检测并重试。",
							LocalDateTime.now().toString().replace("T", "|")));
					Thread.sleep(1000);
					if (isVNCon()) {
						System.out.println(String.format("[%s]检测到VNC服务启动，不进行重试。",
								LocalDateTime.now().toString().replace("T", "|")));
						System.out.println(String.format("[%s]检测到VNC服务进程，开始连接……",
								LocalDateTime.now().toString().replace("T", "|")));
						setupVNC();
						return;
					} else {
						System.out.println(
								String.format("[%s]未检测到VNC服务启动，重试。", LocalDateTime.now().toString().replace("T", "|")));
						if (startServ() == 0) {
							System.out.println(
									String.format("[%s]服务启动完成,开始检测", LocalDateTime.now().toString().replace("T", "|")));
							if (isVNCon()) {
								System.out.println(String.format("[%s]检测到VNC服务进程，开始连接……",
										LocalDateTime.now().toString().replace("T", "|")));
								setupVNC();
								return;
							}
						} else {
							System.out.println(
									String.format("[%s]启动失败。", LocalDateTime.now().toString().replace("T", "|")));
							Dialogs.warn("远程桌面启动失败。");
						}
					}
				}
			}
		}
		// 服务未注册
		else {
			System.out.println(String.format("[%s]VNC服务未注册。", LocalDateTime.now().toString().replace("T", "|")));
			// 服务未注册，VNC已启动，结束当前VNC。
			if (isVNCon()) {
				System.out.println(String.format("[%s]猜测可能是以App形式运行的VNC，尝试结束。",
						LocalDateTime.now().toString().replace("T", "|")));
				try {
					Process cmd;
					String cmd_run = Tools.tvnCmd("-controlapp -shutdown");
					cmd = Runtime.getRuntime().exec(cmd_run);
					cmd.waitFor();
				} catch (Exception e) {
					System.out.println(String.format("[%s]以App形式结束尝试结束失败。",
							LocalDateTime.now().toString().replace("T", "|")));
				}
				if(isVNCon()) {
					System.out.println(String.format("[%s]执行强制关闭。",
							LocalDateTime.now().toString().replace("T", "|")));
					removeVNC();
					setupVNC();
					return;
				}else {
					System.out.println(String.format("[%s]以App形式主动结束成功。",
							LocalDateTime.now().toString().replace("T", "|")));
					setupVNC();
					return;
				}
			}
			// 服务未注册，VNC未启动。
			else {
				System.out.println(
						String.format("[%s]未检测到VNC服务进程，重新注册中……", LocalDateTime.now().toString().replace("T", "|")));
				if (reRegServ() == 0) {
					System.out.println(String.format("[%s]====>>>>服务注册完成<<<<====",
							LocalDateTime.now().toString().replace("T", "|")));
					setupVNC();
					return;
				} else {
					System.out.println(String.format("[%s]reRegServ返回值不为零，2秒后重试。",
							LocalDateTime.now().toString().replace("T", "|")));
					Thread.sleep(2000);
					if (isServiceReg()) {
						System.out.println(String.format("[%s]====>>>>检测到服务注册完成<<<<====",
								LocalDateTime.now().toString().replace("T", "|")));
						setupVNC();
						return;
					} else {
						if (reRegServ() != 0) {
							System.out.println(String.format("[%s]reRegServ返回值不为零，请重新加载!",
									LocalDateTime.now().toString().replace("T", "|")));
							Dialogs.warn("远程桌面服务注册失败（已重试）。请再点一次「加载远程桌面」，或右键走高级部署。");
						}
					}
				}
			}
		}
	}

	/**
	 * 注册VNC服务
	 * 
	 * @return
	 */
	private int reRegServ() {
		Process cmd;
		String cmd_run = "";
		String stat = "";// 正常输出流
		String error = "";// 错误输出流
		String exit = "";// 退出执行状态码
		try {
			// 注册TightVNC
			System.out.println(
					String.format("[%s]reRegServ():注册VNC服务。", LocalDateTime.now().toString().replace("T", "|")));
			cmd_run = Tools.tvnCmd("-reinstall");
			cmd = Runtime.getRuntime().exec(cmd_run);
			InputStream IS = cmd.getInputStream();
			InputStreamReader ISR = new InputStreamReader(IS, "gbk");
			BufferedReader BR0 = new BufferedReader(ISR);
			BufferedReader BR1 = new BufferedReader(new InputStreamReader(cmd.getErrorStream(), "gbk"));
			String line = null;
			while ((line = BR0.readLine()) != null) {
				stat = stat + line + "\n";
				System.out.println(String.format("[%s]s: %s", LocalDateTime.now().toString().replace("T", "|"), line));
			}
			while ((line = BR1.readLine()) != null) {
				error = error + line + "\n";
				System.out.println(String.format("[%s]e: %s", LocalDateTime.now().toString().replace("T", "|"), line));
			}
			int exitValue = cmd.waitFor();
			exit = String.valueOf(exitValue);
			if (exitValue != 0) {
				System.out.println(String.format("[%s]reRegServ():VNC服务注册返回值不为零，弹出警示框，返回值：%s",
						LocalDateTime.now().toString().replace("T", "|"), exit));
			} else {
				System.out.println(String.format("[%s]reRegServ():VNC服务注册执行完成，正在启动服务，返回值：%s",
						LocalDateTime.now().toString().replace("T", "|"), exit));
			}
		} catch (Exception e) {
			System.out.println(String.format("[%s]reRegServ():Java Exception:%s",
					LocalDateTime.now().toString().replace("T", "|"), e.toString()));
		}
		return Integer.valueOf(exit);
	}

	/**
	 * 启动VNC服务
	 * 
	 * @return
	 */
	private int startServ() {
		Process cmd;
		String cmd_run = "";
		String stat = "";// 正常输出流
		String error = "";// 错误输出流
		String exit = "";// 退出执行状态码
		try {
			// 启动TightVNC
			System.out.println(
					String.format("[%s]startServ():启动VNC服务。", LocalDateTime.now().toString().replace("T", "|")));
			cmd_run = Tools.tvnCmd("-start");
			cmd = Runtime.getRuntime().exec(cmd_run);
			InputStream IS = cmd.getInputStream();
			InputStreamReader ISR = new InputStreamReader(IS, "gbk");
			BufferedReader BR0 = new BufferedReader(ISR);
			BufferedReader BR1 = new BufferedReader(new InputStreamReader(cmd.getErrorStream(), "gbk"));
			String line = null;
			while ((line = BR0.readLine()) != null) {
				stat = stat + line + "\n";
				System.out.println(String.format("[%s]s: %s", LocalDateTime.now().toString().replace("T", "|"), line));
			}
			while ((line = BR1.readLine()) != null) {
				error = error + line + "\n";
				System.out.println(String.format("[%s]e: %s", LocalDateTime.now().toString().replace("T", "|"), line));
			}
			int exitValue = cmd.waitFor();
			exit = String.valueOf(exitValue);
			if (exitValue != 0) {
				System.out.println(String.format("[%s]startServ():VNC服务启动返回值不为零，弹出警示框，返回值：%s",
						LocalDateTime.now().toString().replace("T", "|"), exit));
			} else {
				System.out.println(String.format("[%s]startServ():VNC服务启动执行完成，返回值：%s",
						LocalDateTime.now().toString().replace("T", "|"), exit));
			}
			Thread.sleep(1000);
		} catch (Exception e) {
			System.out.println(String.format("[%s]startServ():Java Exception:%s",
					LocalDateTime.now().toString().replace("T", "|"), e.toString()));
		}
		return Integer.valueOf(exit);
	}

	/**
	 * 连接远程主机地址
	 * 
	 * @param Host
	 * @return
	 */
	private int servConnect() {
		// 输入远程主机地址的对话框（默认来自部署设置 host:port）
		AppConf conf = AppConf.load();
		defaultRemoteHostAddress = conf.defaultRemoteHost();
		System.out.println(String.format("[%s]显示远程主机对话框。", LocalDateTime.now().toString().replace("T", "|")));
		final String RemoteHost = userInputDialog(
				"确认协助端地址（来自高级设置，可改）。留空则取消连接。\n" + "随后的防火墙联网许可、UAC管理员权限请求【请选择>允许<】谢谢！",
				defaultRemoteHostAddress);
		// 判断是否需要停止执行
		if (RemoteHost.equals(EXIT_CODE)) {
			System.out.println(String.format("[%s]无效参数,启动远程桌面失败。", LocalDateTime.now().toString().replace("T", "|")));
			App.restorePreferredTop();
			return -1;
		}
		rememberRemoteHost(RemoteHost);
		Process cmd;
		String cmd_run = "";
		String stat = "";// 正常输出流
		String error = "";// 错误输出流
		String exit = "0";// 退出执行状态码
		try {
			System.out
					.println(String.format("[%s]servConnect:开始连接……", LocalDateTime.now().toString().replace("T", "|")));
			String Connect = Tools.tvnCmd("-controlservice -connect " + RemoteHost);
			cmd_run = Connect;
			cmd = Runtime.getRuntime().exec(cmd_run);
			InputStream IS = cmd.getInputStream();
			InputStreamReader ISR = new InputStreamReader(IS, "gbk");
			BufferedReader BR0 = new BufferedReader(ISR);
			BufferedReader BR1 = new BufferedReader(new InputStreamReader(cmd.getErrorStream(), "gbk"));
			String line = null;
			line = null;
			while ((line = BR0.readLine()) != null) {
				stat = stat + line + "\n";
				System.out.println(String.format("[%s]s: %s", LocalDateTime.now().toString().replace("T", "|"), line));
			}
			while ((line = BR1.readLine()) != null) {
				error = error + line + "\n";
				System.out.println(String.format("[%s]e: %s", LocalDateTime.now().toString().replace("T", "|"), line));
			}
			int exitValue = cmd.waitFor();
			exitValue = cmd.waitFor();
			exit = String.valueOf(exitValue);
			if (exitValue != 0) {
				System.out.println(String.format("[%s]servConnect:VNC连接返回值不为零,弹出提示框，返回值：%s",
						LocalDateTime.now().toString().replace("T", "|"), exit));
			} else {
				System.out.println(String.format("[%s]servConnect:VNC连接执行完毕，返回值：%s",
						LocalDateTime.now().toString().replace("T", "|"), exit));
			}
			Thread.sleep(1000);
		} catch (Exception e) {
			System.out.println(String.format("[%s]servConnect - JavaException：%s",
					LocalDateTime.now().toString().replace("T", "|"), e.toString()));
			exit = "-1";
		}
		App.restorePreferredTop();
		try {
			return Integer.parseInt(exit);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** 把确认后的地址写回 app.conf host/port。 */
	private void rememberRemoteHost(String remote) {
		if (remote == null) {
			return;
		}
		String t = remote.trim();
		if (t.isEmpty()) {
			return;
		}
		defaultRemoteHostAddress = t;
		String h = t;
		String p = "";
		int colon = t.lastIndexOf(':');
		if (colon > 0 && colon < t.length() - 1) {
			String maybePort = t.substring(colon + 1).trim();
			if (maybePort.matches("\\d{1,5}")) {
				h = t.substring(0, colon).trim();
				p = maybePort;
			}
		}
		try {
			AppConf c = AppConf.load();
			c.set("host", h);
			if (!p.isEmpty()) {
				c.set("port", p);
			}
			c.save();
			refreshDesktopSummary();
		} catch (Exception e) {
			System.err.println("写回 host/port 失败: " + e);
		}
	}

	/**
	 * 停止VNC服务并卸载
	 */
	private void removeVNC() {
		System.out.println(String.format("[%s]开始关闭VNC服务。", LocalDateTime.now().toString().replace("T", "|")));
		App.setTop(false);
		Process cmd;
//		final String cmd_str = "cmd.exe /C start ";
//		System.out.println(cmd_str);
		String cmd_run = "";

		String stat = "";// 正常输出流
		String error = "";// 错误输出流
		String exit = "";// 退出执行状态码

		// 先检测VNC服务是否运行
		if (isVNCon()) {
			try {
				// 关闭TightVNC
				System.out.println(String.format("[%s]正在主动停止VNC服务。", LocalDateTime.now().toString().replace("T", "|")));
				cmd_run = Tools.tvnCmd("-stop");
				cmd = Runtime.getRuntime().exec(cmd_run);
				BufferedReader BR0 = new BufferedReader(new InputStreamReader(cmd.getInputStream(), "gbk"));
				BufferedReader BR1 = new BufferedReader(new InputStreamReader(cmd.getErrorStream(), "gbk"));
				String line = null;
				while ((line = BR0.readLine()) != null) {
					stat = stat + line + "\n";
					System.out.println(
							String.format("[%s]s: %s", LocalDateTime.now().toString().replace("T", "|"), line));
				}
				while ((line = BR1.readLine()) != null) {
					error = error + line + "\n";
					System.out.println(
							String.format("[%s]e: %s", LocalDateTime.now().toString().replace("T", "|"), line));
				}
				int exitValue = cmd.waitFor();
				exit = String.valueOf(exitValue);
				System.out.println(
						String.format("[%s]第一次主动停止服务返回值：%s", LocalDateTime.now().toString().replace("T", "|"), exit));
				if (exitValue != 0) {
					System.out.println(String.format("[%s]第一次主动停止服务返回值不为零：%s",
							LocalDateTime.now().toString().replace("T", "|"), exit));
				} else {
					System.out.println(String.format("[%s]第一次主动停止VNC命令正常运行，返回值：%s。",
							LocalDateTime.now().toString().replace("T", "|"), exit));
				}
				System.out.println(
						String.format("[%s]正在检测VNC服务是否正常停止(2.5s)……", LocalDateTime.now().toString().replace("T", "|")));
				stat = stat + "\n__________我是一条无情的分割线_________\n";
				error = error + "\n__________我是一条无情的分割线_________\n";
				BR0 = null;
				BR1 = null;
				Thread.sleep(2500);
				// 上一步没有正常执行就主动杀死。
				if (exitValue != 0) {
					System.out.println(String.format("[%s]第一次主动关闭失败，检测VNC服务是否运行。",
							LocalDateTime.now().toString().replace("T", "|")));
					if (isVNCon()) {
						System.out.println(String.format("[%s]检测到VNC服务未结束，第二次主动关闭开始。",
								LocalDateTime.now().toString().replace("T", "|")));
						try {
							cmd_run = Tools.tvnCmd("-stop");
							cmd = Runtime.getRuntime().exec(cmd_run);
						} catch (Exception e) {
							System.out.println(String.format("[%s]第二次主动关闭失败：%s",
									LocalDateTime.now().toString().replace("T", "|"), e.toString()));
						}
					}
					Thread.sleep(2400);
					if (isVNCon()) {
						System.out.println(
								String.format("[%s]主动关闭失败，尝试强制关闭。", LocalDateTime.now().toString().replace("T", "|")));
//						String kill = "taskkill /f /t /im start_server.exe";
						System.out.println(
								String.format("[%s]第一次强制关闭开始。", LocalDateTime.now().toString().replace("T", "|")));
						String kill = Tools.tvnKillCmd();
						cmd_run = kill;
						cmd = Runtime.getRuntime().exec(cmd_run);
						BR0 = new BufferedReader(new InputStreamReader(cmd.getInputStream(), "gbk"));
						BR1 = new BufferedReader(new InputStreamReader(cmd.getErrorStream(), "gbk"));
						line = null;
						while ((line = BR0.readLine()) != null) {
							stat = stat + line + "\n";
							System.out.println(
									String.format("[%s]s: %s", LocalDateTime.now().toString().replace("T", "|"), line));
						}
						while ((line = BR1.readLine()) != null) {
							error = error + line + "\n";
							System.out.println(
									String.format("[%s]e: %s", LocalDateTime.now().toString().replace("T", "|"), line));
						}
						exitValue = cmd.waitFor();
						exit = String.valueOf(exitValue);
						System.out.println(String.format("[%s]第一次强制关闭返回值：%s",
								LocalDateTime.now().toString().replace("T", "|"), exit));
						if (exitValue != 0) {
							System.out.println(String.format("[%s]检测到第一次强制关闭返回值不为零：%s",
									LocalDateTime.now().toString().replace("T", "|"), exit));
						} else {
							System.out.println(String.format("[%s]第一次强制关闭执行成功，返回值：%s",
									LocalDateTime.now().toString().replace("T", "|"), exit));
						}
					}
				} else {
					System.out.println(String.format("[%s]执行第一次检查。", LocalDateTime.now().toString().replace("T", "|")));
					if (isVNCon()) {
						System.out.println(String.format("[%s]一次检查，发现残留进程，执行第三次主动停止。",
								LocalDateTime.now().toString().replace("T", "|")));
						try {
//							cmd_run = Tools.tvnCmd("-controlapp -shutdown");
							cmd_run = Tools.tvnCmd("-stop");
							cmd = Runtime.getRuntime().exec(cmd_run);
							cmd.waitFor();
						} catch (Exception e) {
//							System.out.println(e.toString());
							System.out.println(String.format("[%s]第三次主动停止失败：%s",
									LocalDateTime.now().toString().replace("T", "|"), e.toString()));
						}
					}
					// 再次检查
					try {
						System.out.println(String.format("[%s]再次检查。", 
								LocalDateTime.now().toString().replace("T", "|")));
						cmd_run = "tasklist";
						cmd = Runtime.getRuntime().exec(cmd_run);
						BufferedReader readTask = new BufferedReader(
								new InputStreamReader(cmd.getInputStream(), "gbk"));
						LinkedList<String> Tasks = new LinkedList<>();
						boolean isFound = false;
						// 遍历tasklist
						while ((line = readTask.readLine()) != null) {
							Tasks.add(line);
							System.out.println(String.format("[%s] %s", 
									LocalDateTime.now().toString().replace("T", "|"),line));
						}
						for (String task : Tasks) { // 也可以改写 for(int i=0;i<list.size();i++) 这种形式
							if (task.contains("start_server.exe")) {
								isFound = true;
							}
						}
						if (isFound) {
							System.out.println(String.format("[%s]发现残留进程,执行第二次强制关闭。",
									LocalDateTime.now().toString().replace("T", "|")));
							cmd_run = Tools.tvnKillCmd();
							cmd = Runtime.getRuntime().exec(cmd_run);
							exitValue = cmd.waitFor();
						}
					} catch (Exception e) {
						Dialogs.warn("未能停止远程桌面。请打开任务管理器（Ctrl+Shift+Esc），在详细信息中结束 start_server.exe。");
						System.out.println(String.format("[%s]在杀死VNC时遇到了困难[1]: %s",
								LocalDateTime.now().toString().replace("T", "|"), e.toString()));
					}
				}
			} catch (Exception e) {
				System.out.println(String.format("[%s]杀死进程 - Exception : %s",
						LocalDateTime.now().toString().replace("T", "|"), e.toString()));
				Dialogs.error("停止失败", "Java Exception :\n" + e.toString() + "\n\nError Stream:\n" + error);
			} finally {
				System.out.println(String.format("[%s]开始执行二次检查。", LocalDateTime.now().toString().replace("T", "|")));
				// 检查
				try {
					if (isVNCon()) {
						System.out.println(String.format("[%s]发现残留进程，执行第三次强制关闭。",
								LocalDateTime.now().toString().replace("T", "|")));
						cmd_run = Tools.tvnKillCmd();
						cmd = Runtime.getRuntime().exec(cmd_run);
						cmd.wait();
					} else {
						System.out.println(
								String.format("[%s]二次检查未发现VNC进程。", LocalDateTime.now().toString().replace("T", "|")));
					}
				} catch (Exception e) {
					Dialogs.warn("如果未能停止远程桌面，请打开任务管理器（Ctrl+Shift+Esc），在详细信息中结束 start_server.exe。");
					System.out.println(String.format("[%s]在杀死VNC进程时遇到了困难[2]: %s",
							LocalDateTime.now().toString().replace("T", "|"), e.toString()));
					App.restorePreferredTop();
				}
				// 尝试卸载服务
				System.out.println(String.format("[%s]开始卸载VNC服务。", LocalDateTime.now().toString().replace("T", "|")));
				try {
					cmd_run = Tools.tvnCmd("-remove");
					cmd = Runtime.getRuntime().exec(cmd_run);
					cmd.waitFor();
				} catch (Exception e2) {
					System.out.println(String.format("[%s]卸载服务出错: %s", LocalDateTime.now().toString().replace("T", "|"),
							e2.toString()));
					try {
						if (isVNCon()) {
							cmd_run = Tools.tvnCmd("-stop");
							cmd = Runtime.getRuntime().exec(cmd_run);
							cmd.waitFor();
						}
						cmd_run = Tools.tvnCmd("-remove");
						cmd = Runtime.getRuntime().exec(cmd_run);
						cmd.wait();
					} catch (Exception e3) {
						System.out.println(String.format(
								"[%s]二次卸载服务出错，停止尝试。" + "请进入 res/windows/TightVNC/RAServer/ 目录，手动在 powershell 或 cmd 中输入"
										+ "“start_server.exe -remove”卸载",
								LocalDateTime.now().toString().replace("T", "|")));
						App.restorePreferredTop();
					}
				}
//				System.out.println(
//						String.format("[%s]Input Stream: %s", LocalDateTime.now().toString().replace("T", "|"), stat));
//				System.out.println(
//						String.format("[%s]Error Stream: %s", LocalDateTime.now().toString().replace("T", "|"), error));
//				App.restorePreferredTop();
			}
		} else {
			System.out.println(
					String.format("[%s]未检出到VNC服务进程，不进行关闭操作。", LocalDateTime.now().toString().replace("T", "|")));
			App.restorePreferredTop();
		}
		App.restorePreferredTop();
		System.out
				.println(String.format("[%s]====>>>>停止进程结束<<<<====", LocalDateTime.now().toString().replace("T", "|")));
	}
	
	/**
	 * 重命名按钮名字 0,1,2,3分别对应Ctrl、Shift、Alt、Windows
	 * 
	 * @param i
	 * @param string
	 */
	public static void setButtonText(int i, String str) {
		switch (i) {
		case 0:
			sCtrlLock.setText(str);
			break;
		case 1:
			sShiftLock.setText(str);
			break;
		case 2:
			sAltLock.setText(str);
			break;
		case 3:
			sWinLock.setText(str);
			break;
		default:
			System.out.println(String.format("[%s]setButtonText():未定义按键ID：%d 和字符串 %s",
					LocalDateTime.now().toString().replace("T", "|"), i, str));
		}
	}

	/**
	 * 检查VNC是否注册为服务
	 * 
	 * @return
	 */
	private static boolean isServiceReg() {
		boolean hasReg = false;
		try {
			Process cmd;
			String cmd_run = "sc.exe query tvnserver";
			cmd = Runtime.getRuntime().exec(cmd_run);
			BufferedReader readServ = new BufferedReader(new InputStreamReader(cmd.getInputStream(), "gbk"));
			LinkedList<String> Services = new LinkedList<>();
			String line = null;
			// 遍历service
			while ((line = readServ.readLine()) != null) {
				Services.add(line);
				System.out.println(String.format("[%s]read: %s", LocalDateTime.now().toString().replace("T", "|"), line));
			}
			for (String task : Services) { // 也可以改写 for(int i=0;i<list.size();i++) 这种形式
				if (task.contains("tvnserver")) {
					hasReg = true;
				}
			}
			Thread.sleep(1000);
		} catch (Exception e) {
			System.out.println(String.format("[%s]isServiceReg()出现Exception : %s",
					LocalDateTime.now().toString().replace("T", "|"), e.toString()));
		}
		if (hasReg) {
			flag = false;
			System.out.println(
					String.format("[%s]isServiceReg()：VNC服务已注册。", LocalDateTime.now().toString().replace("T", "|")));
			return true;
		}
		return false;
	}

	/**
	 * 检查VNC是否启动 
	 * 
	 * @return
	 */
	private static boolean isVNCon() {
		boolean isFound = false;
		try {
			Process cmd;
			String cmd_run = "tasklist";
			cmd = Runtime.getRuntime().exec(cmd_run);
			BufferedReader readTask = new BufferedReader(new InputStreamReader(cmd.getInputStream(), "gbk"));
			LinkedList<String> Tasks = new LinkedList<>();
			String line = null;
			// 遍历tasklist
			while ((line = readTask.readLine()) != null) {
				Tasks.add(line);
				System.out.println(String.format("[%s]read: %s", LocalDateTime.now().toString().replace("T", "|"), line));
			}
			for (String task : Tasks) { // 也可以改写 for(int i=0;i<list.size();i++) 这种形式
				if (task.contains("start_server.exe")) {
					isFound = true;
				}
			}
			Thread.sleep(1000);
		} catch (Exception e) {
			System.out.println(String.format("[%s]isVNCon()出现Exception : %s",
					LocalDateTime.now().toString().replace("T", "|"), e.toString()));
		}
		if (isFound) {
			flag = false;
			System.out.println(
					String.format("[%s]isVNCon()：发现VNC服务正在运行。", LocalDateTime.now().toString().replace("T", "|")));
			return true;
		}
		return false;
	}

	/**
	 * 显示远程主机地址对话框
	 * 
	 * @param message
	 * @param defaultValue
	 * @return
	 */
	private static String userInputDialog(String message, String defaultValue) {
		System.out.println(String.format("[%s]等待用户输入远程主机地址。", LocalDateTime.now().toString().replace("T", "|")));
		String input = Dialogs.input(message, defaultValue);
		if (input == null) {
			System.out.println(String.format("[%s]用户取消输入。", LocalDateTime.now().toString().replace("T", "|")));
			return EXIT_CODE;
		}
		System.out.println(String.format("[%s]获取到用户自定义远程主机参数：%s",
				LocalDateTime.now().toString().replace("T", "|"), input));
		return input;
	}

	/**
	 * 获取局域网IP
	 * 
	 * @throws SocketException
	 */
	static void getIPaddr() throws SocketException {// get all local ips
		Enumeration<NetworkInterface> interfs = NetworkInterface.getNetworkInterfaces();
		System.out.println(String.format("[%s]正在獲取电脑本地IP地址....\n\n", LocalDateTime.now().toString().replace("T", "|")));
		int n = 1;
		boolean getStatus = false;
		while (interfs.hasMoreElements()) {
			NetworkInterface interf = interfs.nextElement();
			Enumeration<InetAddress> addres = interf.getInetAddresses();
			if (n == 1 | getStatus) {
				System.out.println("<------第" + n + "组网卡------>");
				getStatus = false;
			}
			while (addres.hasMoreElements()) {
				InetAddress in = addres.nextElement();
				if (in instanceof Inet4Address) {
					System.out.println(" - IPv4地址:" + in.getHostAddress());
					getStatus = true;
				} else if (in instanceof Inet6Address) {
					System.out.println(" - IPv6地址:" + in.getHostAddress());
					getStatus = true;
				}
			}
			if (getStatus) {
				n += 1;
			}
		}
		System.out.println("<--没有第" + n + "组网卡，如果以上结果没有显示出你所在局域网的IP地址。请手动查看您的IPv4地址谢谢-->");
		System.out.println(String.format("\n\n[%s]网卡信息输出完毕。", LocalDateTime.now().toString().replace("T", "|")));
		try {
			System.out.println(String.format("[%s]请稍等片刻……", LocalDateTime.now().toString().replace("T", "|")));
			Thread.sleep(200);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.err.println("\n======>>>>>> 准备就绪！<<<<<<======\n");
	}


	/**
	 * 返回Ctrl、Shift、Windows、Alt的锁定状态
	 * 
	 * @return
	 */
	public static boolean getCtrl() {
		return isCtrlPressed;
	}

	public static boolean getShift() {
		return isShiftPressed;
	}

	public static boolean getAlt() {
		return isAltPressed;
	}

	public static boolean getWindows() {
		return isWindowsPressed;
	}

	/** 正在主动松开时，不要把修饰键再按回去。 */
	public static boolean suppressModRepress() {
		return suppressModRepress;
	}
}
