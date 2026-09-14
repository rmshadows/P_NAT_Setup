package application;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.scene.input.KeyCode;
import javafx.scene.robot.Robot;

/**
 * 系统监视器 / 输入法 / 屏幕键盘：短按执行，长按选方案或录制热键。
 */
public final class AssistHotkeys {
	public static final String CONF_MONITOR = "hotkey_task_manager";
	public static final String CONF_MONITOR_CUSTOM = "hotkey_task_manager_custom";
	public static final String CONF_IME = "hotkey_ime";
	public static final String CONF_IME_CUSTOM = "hotkey_ime_custom";
	public static final String CONF_OSK = "hotkey_osk";
	public static final String CONF_OSK_CUSTOM = "hotkey_osk_custom";

	private AssistHotkeys() {
	}

	public static List<DesktopHotkeys.Preset> monitorPresets() {
		List<DesktopHotkeys.Preset> list = new ArrayList<DesktopHotkeys.Preset>();
		list.add(new DesktopHotkeys.Preset("auto", "自动（按系统）"));
		list.add(new DesktopHotkeys.Preset("ctrl+shift+esc", "Ctrl+Shift+Esc"));
		list.add(new DesktopHotkeys.Preset("custom", "自定义（录制按键）…"));
		return list;
	}

	public static List<DesktopHotkeys.Preset> imePresets() {
		List<DesktopHotkeys.Preset> list = new ArrayList<DesktopHotkeys.Preset>();
		list.add(new DesktopHotkeys.Preset("auto", "自动 Ctrl+Space"));
		list.add(new DesktopHotkeys.Preset("ctrl+space", "Ctrl+Space"));
		list.add(new DesktopHotkeys.Preset("ctrl+shift", "Ctrl+Shift"));
		list.add(new DesktopHotkeys.Preset("super+space", "Super+Space"));
		list.add(new DesktopHotkeys.Preset("custom", "自定义（录制按键）…"));
		return list;
	}

	public static List<DesktopHotkeys.Preset> oskPresets() {
		List<DesktopHotkeys.Preset> list = new ArrayList<DesktopHotkeys.Preset>();
		list.add(new DesktopHotkeys.Preset("auto", "自动（启动/切换屏幕键盘）"));
		list.add(new DesktopHotkeys.Preset("ctrl+win+o", "Ctrl+Win+O（Windows）"));
		list.add(new DesktopHotkeys.Preset("custom", "自定义（录制按键）…"));
		return list;
	}

	public static String monitorLabel() {
		return label(CONF_MONITOR, CONF_MONITOR_CUSTOM, monitorPresets());
	}

	public static String imeLabel() {
		return label(CONF_IME, CONF_IME_CUSTOM, imePresets());
	}

	public static String oskLabel() {
		return label(CONF_OSK, CONF_OSK_CUSTOM, oskPresets());
	}

	public static void pickMonitorHotkey(Runnable after) {
		pick(CONF_MONITOR, CONF_MONITOR_CUSTOM, "系统监视器 · 热键", monitorPresets(), after);
	}

	public static void pickImeHotkey(Runnable after) {
		pick(CONF_IME, CONF_IME_CUSTOM, "切换输入法 · 热键", imePresets(), after);
	}

	public static void pickOskHotkey(Runnable after) {
		pick(CONF_OSK, CONF_OSK_CUSTOM, "屏幕键盘 · 热键", oskPresets(), after);
	}

	public static void fireMonitor(Robot robot) {
		String id = idOf(CONF_MONITOR);
		if ("custom".equals(id) && fireCustom(CONF_MONITOR_CUSTOM, robot)) {
			return;
		}
		if ("ctrl+shift+esc".equals(id)) {
			chordOrXdo(robot, "ctrl+shift+Escape", KeyCode.CONTROL, KeyCode.SHIFT, KeyCode.ESCAPE);
			return;
		}
		// auto
		if (Tools.isWindows()) {
			chordOrXdo(robot, "ctrl+shift+Escape", KeyCode.CONTROL, KeyCode.SHIFT, KeyCode.ESCAPE);
			return;
		}
		HostActions.launchSystemMonitor();
	}

	public static void fireIme(Robot robot) {
		String id = idOf(CONF_IME);
		if ("custom".equals(id) && fireCustom(CONF_IME_CUSTOM, robot)) {
			return;
		}
		if ("ctrl+shift".equals(id)) {
			chordOrXdo(robot, "ctrl+shift", KeyCode.CONTROL, KeyCode.SHIFT);
			return;
		}
		if ("super+space".equals(id)) {
			chordOrXdo(robot, "super+space", KeyCode.WINDOWS, KeyCode.SPACE);
			return;
		}
		// auto / ctrl+space
		chordOrXdo(robot, "ctrl+space", KeyCode.CONTROL, KeyCode.SPACE);
	}

	public static void fireOsk(Robot robot) {
		String id = idOf(CONF_OSK);
		if ("custom".equals(id) && fireCustom(CONF_OSK_CUSTOM, robot)) {
			return;
		}
		if ("ctrl+win+o".equals(id)) {
			chordOrXdo(robot, "ctrl+super+o", KeyCode.CONTROL, KeyCode.WINDOWS, KeyCode.O);
			return;
		}
		if (Tools.isWindows()) {
			chordOrXdo(robot, "ctrl+super+o", KeyCode.CONTROL, KeyCode.WINDOWS, KeyCode.O);
			return;
		}
		HostActions.launchOnScreenKeyboard();
	}

	private static void pick(String confId, String confCustom, String title,
			List<DesktopHotkeys.Preset> presets, Runnable after) {
		String chosen = DesktopHotkeys.pickPreset(title, presets, idOf(confId));
		if (chosen == null) {
			return;
		}
		try {
			if ("custom".equals(chosen)) {
				String combo = Dialogs.captureHotkey("录制热键", AppConf.load().get(confCustom));
				if (combo == null) {
					return;
				}
				AppConf c = AppConf.load();
				c.set(confId, "custom");
				c.set(confCustom, combo);
				c.save();
			} else {
				AppConf c = AppConf.load();
				c.set(confId, chosen);
				c.save();
			}
			if (after != null) {
				after.run();
			}
		} catch (IOException e) {
			Dialogs.warn("保存失败：\n" + e.getMessage());
		}
	}

	public static boolean fireCombo(Robot robot, String combo) {
		if (combo == null || combo.trim().isEmpty()) {
			return false;
		}
		if (HostActions.xdotoolKeyPublic(combo.trim())) {
			return true;
		}
		return chordFromCombo(robot, combo.trim());
	}

	private static boolean fireCustom(String confCustom, Robot robot) {
		return fireCombo(robot, AppConf.load().get(confCustom));
	}

	private static boolean chordFromCombo(Robot robot, String combo) {
		String[] parts = combo.split("\\+");
		List<KeyCode> keys = new ArrayList<KeyCode>();
		for (String p : parts) {
			KeyCode k = toKeyCode(p.trim());
			if (k == null) {
				return false;
			}
			keys.add(k);
		}
		if (keys.isEmpty()) {
			return false;
		}
		for (KeyCode k : keys) {
			robot.keyPress(k);
		}
		for (int i = keys.size() - 1; i >= 0; i--) {
			robot.keyRelease(keys.get(i));
		}
		return true;
	}

	private static KeyCode toKeyCode(String p) {
		String s = p.toLowerCase(Locale.ROOT);
		if ("ctrl".equals(s) || "control".equals(s)) {
			return KeyCode.CONTROL;
		}
		if ("alt".equals(s)) {
			return KeyCode.ALT;
		}
		if ("shift".equals(s)) {
			return KeyCode.SHIFT;
		}
		if ("super".equals(s) || "win".equals(s) || "meta".equals(s) || "cmd".equals(s)) {
			return KeyCode.WINDOWS;
		}
		if ("space".equals(s)) {
			return KeyCode.SPACE;
		}
		if ("escape".equals(s) || "esc".equals(s)) {
			return KeyCode.ESCAPE;
		}
		if ("return".equals(s) || "enter".equals(s)) {
			return KeyCode.ENTER;
		}
		if ("tab".equals(s)) {
			return KeyCode.TAB;
		}
		if (s.length() == 1) {
			char c = Character.toUpperCase(s.charAt(0));
			if (c >= 'A' && c <= 'Z') {
				return KeyCode.getKeyCode(String.valueOf(c));
			}
			if (c >= '0' && c <= '9') {
				return KeyCode.getKeyCode(String.valueOf(c));
			}
		}
		try {
			return KeyCode.valueOf(p.toUpperCase(Locale.ROOT).replace(' ', '_'));
		} catch (Exception e) {
			return null;
		}
	}

	private static void chordOrXdo(Robot robot, String xdo, KeyCode... keys) {
		if (!Tools.isWindows() && HostActions.xdotoolKeyPublic(xdo)) {
			return;
		}
		for (KeyCode k : keys) {
			robot.keyPress(k);
		}
		for (int i = keys.length - 1; i >= 0; i--) {
			robot.keyRelease(keys[i]);
		}
	}

	private static String idOf(String conf) {
		String v = AppConf.load().get(conf);
		if (v == null || v.trim().isEmpty()) {
			return "auto";
		}
		return v.trim().toLowerCase(Locale.ROOT);
	}

	private static String label(String confId, String confCustom, List<DesktopHotkeys.Preset> presets) {
		String id = idOf(confId);
		if ("custom".equals(id)) {
			String c = AppConf.load().get(confCustom).trim();
			return c.isEmpty() ? "自定义（未录制）" : "自定义：" + c;
		}
		for (DesktopHotkeys.Preset p : presets) {
			if (p.id.equals(id)) {
				return p.label;
			}
		}
		return id;
	}
}
