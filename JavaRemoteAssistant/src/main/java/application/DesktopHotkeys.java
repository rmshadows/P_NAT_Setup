package application;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.robot.Robot;

/**
 * 显示桌面 / 切换工作区热键。
 * 短按执行；长按 2 秒打开方案选择（含自定义录制）。
 */
public final class DesktopHotkeys {
	public static final String CONF_SHOW = "hotkey_show_desktop";
	public static final String CONF_SWITCH = "hotkey_switch_desktop";
	public static final String CONF_SWITCH_NEXT = "hotkey_switch_next";
	public static final String CONF_SWITCH_PREV = "hotkey_switch_prev";
	public static final String CONF_SWITCH_NEW = "hotkey_switch_new";
	public static final String CONF_SHOW_CUSTOM = "hotkey_show_custom";

	private DesktopHotkeys() {
	}

	public static final class Preset {
		public final String id;
		public final String label;

		public Preset(String id, String label) {
			this.id = id;
			this.label = label;
		}
	}

	public static List<Preset> showPresets() {
		List<Preset> list = new ArrayList<Preset>();
		list.add(new Preset("auto", "自动（按系统）"));
		list.add(new Preset("win+d", "Win+D"));
		list.add(new Preset("super+d", "Super+D"));
		list.add(new Preset("ctrl+alt+d", "Ctrl+Alt+D"));
		list.add(new Preset("ctrl+super+d", "Ctrl+Super+D"));
		list.add(new Preset("custom", "自定义（录制按键）…"));
		return list;
	}

	public static List<Preset> switchPresets() {
		List<Preset> list = new ArrayList<Preset>();
		list.add(new Preset("auto", "自动（按系统）"));
		list.add(new Preset("ctrl+win", "Ctrl+Win+方向"));
		list.add(new Preset("ctrl+alt", "Ctrl+Alt+方向"));
		list.add(new Preset("super+page", "Super+PageUp/Down"));
		list.add(new Preset("super+alt", "Super+Alt+方向"));
		list.add(new Preset("custom", "自定义（分别录制上/下/新建）…"));
		return list;
	}

	public static String currentShowId() {
		return normalize(AppConf.load().get(CONF_SHOW), "auto");
	}

	public static String currentSwitchId() {
		return normalize(AppConf.load().get(CONF_SWITCH), "auto");
	}

	public static void saveShowId(String id) throws IOException {
		AppConf c = AppConf.load();
		c.set(CONF_SHOW, normalize(id, "auto"));
		c.save();
	}

	public static void saveSwitchId(String id) throws IOException {
		AppConf c = AppConf.load();
		c.set(CONF_SWITCH, normalize(id, "auto"));
		c.save();
	}

	public static String showLabel() {
		String id = currentShowId();
		if ("custom".equals(id)) {
			String c = AppConf.load().get(CONF_SHOW_CUSTOM).trim();
			return c.isEmpty() ? "自定义（未录制）" : "自定义：" + c;
		}
		return labelOf(showPresets(), id);
	}

	public static String switchLabel() {
		String id = currentSwitchId();
		if ("custom".equals(id)) {
			AppConf c = AppConf.load();
			return "自定义：↓" + nz(c.get(CONF_SWITCH_NEXT), "?") + " ↑" + nz(c.get(CONF_SWITCH_PREV), "?")
					+ " 新" + nz(c.get(CONF_SWITCH_NEW), "?");
		}
		return labelOf(switchPresets(), id);
	}

	public static void fireShowDesktop(Robot robot) {
		String id = currentShowId();
		if ("custom".equals(id)) {
			String combo = AppConf.load().get(CONF_SHOW_CUSTOM).trim();
			if (!combo.isEmpty() && AssistHotkeys.fireCombo(robot, combo)) {
				return;
			}
			autoShow(robot);
			return;
		}
		id = effectiveShowId();
		if ("win+d".equals(id)) {
			chordOrXdotool(robot, "super+d", KeyCode.WINDOWS, KeyCode.D);
			return;
		}
		if ("super+d".equals(id)) {
			if (!xdotoolKey("super+d")) {
				chordOrXdotool(robot, "super+d", KeyCode.WINDOWS, KeyCode.D);
			}
			return;
		}
		if ("ctrl+alt+d".equals(id)) {
			if (!xdotoolKey("ctrl+alt+d")) {
				chord(robot, KeyCode.CONTROL, KeyCode.ALT, KeyCode.D);
			}
			return;
		}
		if ("ctrl+super+d".equals(id)) {
			if (!xdotoolKey("ctrl+super+d")) {
				chord(robot, KeyCode.CONTROL, KeyCode.WINDOWS, KeyCode.D);
			}
			return;
		}
		autoShow(robot);
	}

	public static void fireSwitchDesktop(Robot robot, HostActions.DesktopNav nav) {
		String id = currentSwitchId();
		if ("custom".equals(id)) {
			AppConf c = AppConf.load();
			String combo;
			if (nav == HostActions.DesktopNav.NEXT) {
				combo = c.get(CONF_SWITCH_NEXT).trim();
			} else if (nav == HostActions.DesktopNav.PREV) {
				combo = c.get(CONF_SWITCH_PREV).trim();
			} else {
				combo = c.get(CONF_SWITCH_NEW).trim();
			}
			if (!combo.isEmpty() && AssistHotkeys.fireCombo(robot, combo)) {
				return;
			}
			autoSwitch(robot, nav);
			return;
		}
		id = effectiveSwitchId();
		if ("ctrl+win".equals(id)) {
			fireCtrlWin(robot, nav);
			return;
		}
		if ("ctrl+alt".equals(id)) {
			fireCtrlAlt(robot, nav);
			return;
		}
		if ("super+page".equals(id)) {
			fireSuperPage(robot, nav);
			return;
		}
		if ("super+alt".equals(id)) {
			fireSuperAlt(robot, nav);
			return;
		}
		autoSwitch(robot, nav);
	}

	/** 长按弹出：选显示桌面热键（含自定义录制）。 */
	public static void pickShowHotkey(Runnable afterChange) {
		String chosen = pickPreset("显示桌面 · 热键", showPresets(), currentShowId());
		if (chosen == null) {
			return;
		}
		try {
			if ("custom".equals(chosen)) {
				String combo = Dialogs.captureHotkey("录制「显示桌面」热键",
						AppConf.load().get(CONF_SHOW_CUSTOM));
				if (combo == null) {
					return;
				}
				AppConf c = AppConf.load();
				c.set(CONF_SHOW, "custom");
				c.set(CONF_SHOW_CUSTOM, combo);
				c.save();
			} else {
				saveShowId(chosen);
			}
			if (afterChange != null) {
				afterChange.run();
			}
		} catch (IOException e) {
			Dialogs.warn("保存失败：\n" + e.getMessage());
		}
	}

	/** 长按弹出：选方案 + 倒计时延时（滚轮留给切桌面）。 */
	public static void pickSwitchHotkey(int currentDelay, Runnable afterChange) {
		SwitchPick pick = pickSwitchDialog(switchPresets(), currentSwitchId(), currentDelay);
		if (pick == null) {
			return;
		}
		try {
			if ("custom".equals(pick.id)) {
				AppConf c = AppConf.load();
				String next = Dialogs.captureHotkey("录制「下一工作区」", c.get(CONF_SWITCH_NEXT));
				if (next == null) {
					return;
				}
				String prev = Dialogs.captureHotkey("录制「上一工作区」", c.get(CONF_SWITCH_PREV));
				if (prev == null) {
					return;
				}
				String neu = Dialogs.captureHotkey("录制「新建/概览」", c.get(CONF_SWITCH_NEW));
				if (neu == null) {
					return;
				}
				c = AppConf.load();
				c.set(CONF_SWITCH, "custom");
				c.set(CONF_SWITCH_NEXT, next);
				c.set(CONF_SWITCH_PREV, prev);
				c.set(CONF_SWITCH_NEW, neu);
				c.set("delay_desktops", String.valueOf(pick.delaySec));
				c.set(HostActions.CONF_SWITCH_BACKEND, pick.backend);
				c.save();
			} else {
				AppConf c = AppConf.load();
				c.set(CONF_SWITCH, normalize(pick.id, "auto"));
				c.set("delay_desktops", String.valueOf(pick.delaySec));
				c.set(HostActions.CONF_SWITCH_BACKEND, pick.backend);
				c.save();
			}
			if (afterChange != null) {
				afterChange.run();
			}
		} catch (IOException e) {
			Dialogs.warn("保存失败：\n" + e.getMessage());
		}
	}

	private static final class SwitchPick {
		final String id;
		final int delaySec;
		final String backend;

		SwitchPick(String id, int delaySec, String backend) {
			this.id = id;
			this.delaySec = delaySec;
			this.backend = backend;
		}
	}

	private static SwitchPick pickSwitchDialog(List<Preset> presets, String current, int delaySec) {
		Dialog<SwitchPick> d = new Dialog<SwitchPick>();
		d.setTitle("切换工作区 · 设置");
		d.setHeaderText(null);
		ButtonType ok = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
		d.getDialogPane().getButtonTypes().addAll(ok, cancel);
		ToggleGroup group = new ToggleGroup();
		VBox box = new VBox(8);
		box.setPadding(new Insets(10));
		box.getChildren().add(new Label("热键方案（可自定义录制）："));
		RadioButton selected = null;
		for (Preset p : presets) {
			RadioButton rb = new RadioButton(p.label);
			rb.setToggleGroup(group);
			rb.setUserData(p.id);
			if (p.id.equals(current)) {
				rb.setSelected(true);
				selected = rb;
			}
			box.getChildren().add(rb);
		}
		if (selected == null && !presets.isEmpty()) {
			((RadioButton) box.getChildren().get(1)).setSelected(true);
		}
		int delay = Math.max(0, Math.min(15, delaySec));
		final Spinner<Integer> spinner = new Spinner<Integer>();
		spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 15, delay));
		spinner.setEditable(true);
		spinner.setMaxWidth(Double.MAX_VALUE);
		box.getChildren().add(new Label("执行延时（秒，0=立即；滚轮用来切桌面，在这里改）："));
		box.getChildren().add(spinner);
		ToggleGroup backendGroup = new ToggleGroup();
		RadioButton javaRb = new RadioButton("热键用 Java Robot（跟随仍走 linux_follow_desktop.sh）");
		javaRb.setToggleGroup(backendGroup);
		javaRb.setUserData("java");
		RadioButton xdoRb = new RadioButton("连切换也用 xdotool 编号（可选）");
		xdoRb.setToggleGroup(backendGroup);
		xdoRb.setUserData("xdotool");
		if ("xdotool".equals(HostActions.switchBackend())) {
			xdoRb.setSelected(true);
		} else {
			javaRb.setSelected(true);
		}
		if (!Tools.isWindows()) {
			box.getChildren().add(new Label("执行后端："));
			box.getChildren().add(javaRb);
			box.getChildren().add(xdoRb);
		}
		d.getDialogPane().setContent(box);
		Dialogs.preparePublic(d);
		d.setResultConverter(btn -> {
			if (btn != ok || group.getSelectedToggle() == null) {
				return null;
			}
			Object u = group.getSelectedToggle().getUserData();
			int sec = spinner.getValue() == null ? delay : spinner.getValue();
			try {
				sec = Integer.parseInt(spinner.getEditor().getText().trim());
			} catch (Exception ignored) {
			}
			sec = Math.max(0, Math.min(15, sec));
			String backend = "java";
			if (backendGroup.getSelectedToggle() != null
					&& backendGroup.getSelectedToggle().getUserData() != null) {
				backend = backendGroup.getSelectedToggle().getUserData().toString();
			}
			return new SwitchPick(u == null ? "auto" : u.toString(), sec, backend);
		});
		Optional<SwitchPick> r = d.showAndWait();
		return r.orElse(null);
	}

	/** 统一方案选择对话框（给 AssistHotkeys 等复用）。 */
	public static String pickPreset(String title, List<Preset> presets, String current) {
		Dialog<String> d = new Dialog<String>();
		d.setTitle(title);
		d.setHeaderText(null);
		ButtonType ok = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
		d.getDialogPane().getButtonTypes().addAll(ok, cancel);
		ToggleGroup group = new ToggleGroup();
		VBox box = new VBox(8);
		box.setPadding(new Insets(10));
		box.getChildren().add(new Label("选择热键方案（可自定义录制）："));
		RadioButton selected = null;
		for (Preset p : presets) {
			RadioButton rb = new RadioButton(p.label);
			rb.setToggleGroup(group);
			rb.setUserData(p.id);
			if (p.id.equals(current)) {
				rb.setSelected(true);
				selected = rb;
			}
			box.getChildren().add(rb);
		}
		if (selected == null && !presets.isEmpty()) {
			((RadioButton) box.getChildren().get(1)).setSelected(true);
		}
		d.getDialogPane().setContent(box);
		Dialogs.preparePublic(d);
		d.setResultConverter(btn -> {
			if (btn != ok || group.getSelectedToggle() == null) {
				return null;
			}
			Object u = group.getSelectedToggle().getUserData();
			return u == null ? null : u.toString();
		});
		Optional<String> r = d.showAndWait();
		return r.orElse(null);
	}

	private static void autoShow(Robot robot) {
		if (Tools.isWindows()) {
			chord(robot, KeyCode.WINDOWS, KeyCode.D);
			return;
		}
		if (xdotoolKey("super+d") || xdotoolKey("ctrl+alt+d") || xdotoolKey("ctrl+super+d")) {
			return;
		}
		chord(robot, KeyCode.WINDOWS, KeyCode.D);
	}

	private static void autoSwitch(Robot robot, HostActions.DesktopNav nav) {
		if (Tools.isWindows()) {
			fireCtrlWin(robot, nav);
			return;
		}
		if (nav == HostActions.DesktopNav.NEW) {
			if (switchXdo("super") || switchXdo("super+s") || switchXdo("super+w")) {
				return;
			}
			chord(robot, KeyCode.WINDOWS);
			return;
		}
		String arrow = nav == HostActions.DesktopNav.NEXT ? "Right" : "Left";
		String page = nav == HostActions.DesktopNav.NEXT ? "Down" : "Up";
		if (switchXdo("ctrl+alt+" + arrow) || switchXdo("super+Page_" + page)
				|| switchXdo("super+alt+" + arrow)) {
			return;
		}
		chord(robot, KeyCode.CONTROL, KeyCode.ALT,
				nav == HostActions.DesktopNav.NEXT ? KeyCode.RIGHT : KeyCode.LEFT);
	}

	/** 原版 JRA 按键顺序：先松开 Ctrl，再 Win，再方向。 */
	private static void fireCtrlWin(Robot robot, HostActions.DesktopNav nav) {
		KeyCode last = KeyCode.D;
		if (nav == HostActions.DesktopNav.NEXT) {
			last = KeyCode.RIGHT;
		} else if (nav == HostActions.DesktopNav.PREV) {
			last = KeyCode.LEFT;
		}
		robot.keyPress(KeyCode.CONTROL);
		robot.keyPress(KeyCode.WINDOWS);
		robot.keyPress(last);
		robot.keyRelease(KeyCode.CONTROL);
		robot.keyRelease(KeyCode.WINDOWS);
		robot.keyRelease(last);
	}

	private static void fireCtrlAlt(Robot robot, HostActions.DesktopNav nav) {
		if (nav == HostActions.DesktopNav.NEW) {
			if (!switchXdo("super")) {
				chord(robot, KeyCode.WINDOWS);
			}
			return;
		}
		String arrow = nav == HostActions.DesktopNav.NEXT ? "Right" : "Left";
		if (!switchXdo("ctrl+alt+" + arrow)) {
			chord(robot, KeyCode.CONTROL, KeyCode.ALT,
					nav == HostActions.DesktopNav.NEXT ? KeyCode.RIGHT : KeyCode.LEFT);
		}
	}

	private static void fireSuperPage(Robot robot, HostActions.DesktopNav nav) {
		if (nav == HostActions.DesktopNav.NEW) {
			if (!switchXdo("super")) {
				chord(robot, KeyCode.WINDOWS);
			}
			return;
		}
		String page = nav == HostActions.DesktopNav.NEXT ? "Down" : "Up";
		if (!switchXdo("super+Page_" + page)) {
			fireCtrlAlt(robot, nav);
		}
	}

	private static void fireSuperAlt(Robot robot, HostActions.DesktopNav nav) {
		if (nav == HostActions.DesktopNav.NEW) {
			if (!switchXdo("super")) {
				chord(robot, KeyCode.WINDOWS);
			}
			return;
		}
		String arrow = nav == HostActions.DesktopNav.NEXT ? "Right" : "Left";
		if (!switchXdo("super+alt+" + arrow)) {
			fireCtrlAlt(robot, nav);
		}
	}

	private static boolean switchXdo(String combo) {
		return HostActions.xdotoolSwitchEnabled() && xdotoolKey(combo);
	}

	private static String effectiveShowId() {
		String id = currentShowId();
		if ("auto".equals(id) || "custom".equals(id)) {
			return Tools.isWindows() ? "win+d" : "super+d";
		}
		return id;
	}

	private static String effectiveSwitchId() {
		String id = currentSwitchId();
		if ("auto".equals(id) || "custom".equals(id)) {
			return Tools.isWindows() ? "ctrl+win" : "ctrl+alt";
		}
		return id;
	}

	private static void chordOrXdotool(Robot robot, String xdo, KeyCode... keys) {
		if (Tools.isWindows()) {
			chord(robot, keys);
			return;
		}
		if (!xdotoolKey(xdo)) {
			chord(robot, keys);
		}
	}

	private static void chord(Robot robot, KeyCode... keys) {
		for (KeyCode k : keys) {
			robot.keyPress(k);
		}
		for (int i = keys.length - 1; i >= 0; i--) {
			robot.keyRelease(keys[i]);
		}
	}

	private static boolean xdotoolKey(String combo) {
		return HostActions.xdotoolKeyPublic(combo);
	}

	private static String normalize(String id, String fallback) {
		if (id == null || id.trim().isEmpty()) {
			return fallback;
		}
		return id.trim().toLowerCase(Locale.ROOT);
	}

	private static String labelOf(List<Preset> presets, String id) {
		for (Preset p : presets) {
			if (p.id.equals(id)) {
				return p.label;
			}
		}
		return id;
	}

	private static String nz(String v, String d) {
		return v == null || v.trim().isEmpty() ? d : v.trim();
	}

	/** 把 KeyCode 录成 xdotool 风格：ctrl+alt+Right */
	public static String formatCombo(Set<KeyCode> mods, KeyCode key) {
		List<String> parts = new ArrayList<String>();
		if (mods.contains(KeyCode.CONTROL)) {
			parts.add("ctrl");
		}
		if (mods.contains(KeyCode.ALT) || mods.contains(KeyCode.ALT_GRAPH)) {
			parts.add("alt");
		}
		if (mods.contains(KeyCode.SHIFT)) {
			parts.add("shift");
		}
		if (mods.contains(KeyCode.WINDOWS) || mods.contains(KeyCode.COMMAND)
				|| mods.contains(KeyCode.META)) {
			parts.add("super");
		}
		parts.add(xdoKeyName(key));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				sb.append('+');
			}
			sb.append(parts.get(i));
		}
		return sb.toString();
	}

	public static boolean isModifier(KeyCode c) {
		return c == KeyCode.CONTROL || c == KeyCode.ALT || c == KeyCode.ALT_GRAPH || c == KeyCode.SHIFT
				|| c == KeyCode.WINDOWS || c == KeyCode.COMMAND || c == KeyCode.META;
	}

	private static String xdoKeyName(KeyCode c) {
		if (c == KeyCode.RIGHT) {
			return "Right";
		}
		if (c == KeyCode.LEFT) {
			return "Left";
		}
		if (c == KeyCode.UP) {
			return "Up";
		}
		if (c == KeyCode.DOWN) {
			return "Down";
		}
		if (c == KeyCode.PAGE_UP) {
			return "Page_Up";
		}
		if (c == KeyCode.PAGE_DOWN) {
			return "Page_Down";
		}
		if (c == KeyCode.ESCAPE) {
			return "Escape";
		}
		if (c == KeyCode.TAB) {
			return "Tab";
		}
		if (c == KeyCode.SPACE) {
			return "space";
		}
		if (c == KeyCode.ENTER) {
			return "Return";
		}
		String n = c.getName();
		if (n != null && n.length() == 1) {
			return n.toLowerCase(Locale.ROOT);
		}
		return n == null ? "space" : n;
	}
}
