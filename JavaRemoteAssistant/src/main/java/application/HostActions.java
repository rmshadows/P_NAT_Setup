package application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javafx.scene.input.KeyCode;
import javafx.scene.robot.Robot;

/**
 * 本机辅助按键：Windows / Linux 快捷键与启动命令不同。
 * Linux 优先按桌面环境自动匹配程序，再回退通用命令 / 快捷键。
 */
public final class HostActions {
	private HostActions() {
	}

	public enum DesktopNav {
		NEXT, PREV, NEW
	}

	public enum SwitchResult {
		SWITCHED, AT_EDGE, FAILED
	}

	private static String cachedMonitor;
	private static String cachedOsk;

	/** 打开系统监视器 / 任务管理器。 */
	public static void openTaskManager(Robot robot) {
		AssistHotkeys.fireMonitor(robot);
	}

	/** Linux：启动监视器进程（无热键时）。 */
	static void launchSystemMonitor() {
		String cmd = resolveMonitor();
		if (cmd != null && run(cmd)) {
			System.out.println("系统监视器: " + cmd);
			return;
		}
		Dialogs.warn("未找到系统监视器。\n可安装：gnome-system-monitor / plasma-systemmonitor / xfce4-taskmanager 等。");
	}

	/** 显示桌面（热键见 conf，长按可选/录制）。 */
	public static void showDesktop(Robot robot) {
		DesktopHotkeys.fireShowDesktop(robot);
	}

	public static final String CONF_SWITCH_BACKEND = "hotkey_switch_backend";

	/** java（默认，只靠 Robot）或 xdotool（长按菜单可选）。Windows 永不走 xdotool。 */
	public static boolean xdotoolSwitchEnabled() {
		if (Tools.isWindows()) {
			return false;
		}
		return "xdotool".equalsIgnoreCase(AppConf.load().get(CONF_SWITCH_BACKEND).trim());
	}

	public static String switchBackend() {
		if (Tools.isWindows()) {
			return "java";
		}
		String b = AppConf.load().get(CONF_SWITCH_BACKEND).trim().toLowerCase(Locale.ROOT);
		return "xdotool".equals(b) ? "xdotool" : "java";
	}

	/** 切换 / 新建工作区。Linux 上一/下一走编号（上一版能用的做法）。 */
	public static void switchDesktop(Robot robot, DesktopNav nav) {
		if (!Tools.isWindows() && nav != DesktopNav.NEW) {
			if (switchByDesktopIndex(nav) != SwitchResult.FAILED) {
				return;
			}
		}
		DesktopHotkeys.fireSwitchDesktop(robot, nav);
	}

	public static void toastAtDesktopEdge(DesktopNav nav) {
		toastAtDesktopEdge(nav, 0, 0);
	}

	public static void toastAtDesktopEdge(DesktopNav nav, int cur1, int num) {
		String extra = num > 0 ? "（" + Math.max(1, cur1) + "/" + num + "）" : "";
		if (nav == DesktopNav.PREV) {
			Dialogs.toast("已经是第一个桌面" + extra);
		} else if (nav == DesktopNav.NEXT) {
			Dialogs.toast("已经是最后一个桌面" + extra);
		}
	}

	public static void toastDesktopPos(int cur1, int num) {
		if (num <= 0) {
			return;
		}
		if (cur1 <= 0) {
			Dialogs.toast("共 " + num + " 个桌面");
			return;
		}
		Dialogs.toast("当前第 " + cur1 + " / " + num + " 个桌面");
	}

	/**
	 * Linux 上一版能用的做法：xdotool 按编号切工作区，并先把本窗迁过去。
	 * 不模拟 Ctrl+Alt（避免只 hide/show 或只发热键被 GNOME 拽回）。
	 */
	static SwitchResult switchByDesktopIndex(DesktopNav nav) {
		if (Tools.isWindows() || nav == DesktopNav.NEW || !Tools.linuxX11DesktopToolsOk()) {
			return SwitchResult.FAILED;
		}
		int cur = linuxDesktopQuery("current");
		if (cur < 0) {
			cur = parseIntSafe(xdotoolStdout("get_desktop"), -1);
		}
		int num = linuxDesktopQuery("count");
		if (num <= 0) {
			num = parseIntSafe(xdotoolStdout("get_num_desktops"), -1);
		}
		if (cur < 0 || num <= 0) {
			return SwitchResult.FAILED;
		}
		int target = nav == DesktopNav.NEXT ? cur + 1 : cur - 1;
		if (target < 0) {
			System.out.println("切换工作区: 已在第一个 (1/" + num + ")");
			toastAtDesktopEdge(nav, 1, num);
			return SwitchResult.AT_EDGE;
		}
		if (target >= num) {
			System.out.println("切换工作区: 已在最后一个 (" + num + "/" + num + ")");
			toastAtDesktopEdge(nav, num, num);
			return SwitchResult.AT_EDGE;
		}
		moveOwnWindowsToDesktop(String.valueOf(target));
		boolean ok = linuxFollowRun("goto", String.valueOf(target)) == 0
				|| xdotool("set_desktop", String.valueOf(target));
		if (!ok) {
			return SwitchResult.FAILED;
		}
		System.out.println("切换工作区: " + (cur + 1) + " → " + (target + 1) + "/" + num);
		lastLinuxPos[0] = target + 1;
		lastLinuxPos[1] = num;
		return SwitchResult.SWITCHED;
	}

	private static final int[] lastLinuxPos = new int[] { 0, 0 };

	static int[] lastLinuxDesktopPos() {
		return lastLinuxPos[0] > 0 ? new int[] { lastLinuxPos[0], lastLinuxPos[1] } : null;
	}

	/**
	 * 先把 JRA 迁到目标桌面，再发 Ctrl+Alt+左/右。
	 * 只发热键、窗口还留在旧桌面时，GNOME 会把视图拽回去，看起来像「只能到 2」。
	 */
	static boolean switchLinuxByCtrlAlt(DesktopNav nav) {
		if (Tools.which("xdotool") == null) {
			return false;
		}
		int cur = parseIntSafe(xdotoolStdout("get_desktop"), -1);
		int num = parseIntSafe(xdotoolStdout("get_num_desktops"), -1);
		if (cur < 0 || num <= 0) {
			return false;
		}
		int target = nav == DesktopNav.NEXT ? cur + 1 : cur - 1;
		if (target < 0) {
			System.out.println("切换工作区: 已在第一个 (1/" + num + ")");
			return true;
		}
		if (target >= num) {
			System.out.println("切换工作区: 已在最后一个 (" + num + "/" + num + ")");
			return true;
		}
		moveOwnWindowsToDesktop(String.valueOf(target));
		String arrow = nav == DesktopNav.NEXT ? "Right" : "Left";
		boolean keyed = xdotoolKeyPublic("ctrl+alt+" + arrow);
		sleepQuiet(80);
		int now = parseIntSafe(xdotoolStdout("get_desktop"), cur);
		if (now != target) {
			xdotool("set_desktop", String.valueOf(target));
			now = target;
		}
		System.out.println("切换工作区: Ctrl+Alt+" + arrow + "  "
				+ (cur + 1) + " → " + (now + 1) + "/" + num
				+ (keyed ? "" : "（热键发送失败，已对齐编号）"));
		return true;
	}

	/** 把本进程窗口迁到指定工作区（0 起算），避免 toFront 把视图拉回去。 */
	static boolean moveOwnWindowsToDesktop(String desk) {
		int n = parseIntSafe(desk, -1);
		if (n >= 0 && linuxMoveOwnToDesktop(n)) {
			return true;
		}
		List<String> ids = ownWindowIds();
		if (ids.isEmpty()) {
			return moveWindowToCurrentDesktop("远程协助");
		}
		boolean ok = false;
		for (String id : ids) {
			if (xdotool("set_desktop_for_window", id, desk)) {
				ok = true;
			}
		}
		return ok;
	}

	private static int linuxDesktopQuery(String action) {
		return parseIntSafe(linuxFollowStdout(action), -1);
	}

	private static boolean linuxMoveOwnToDesktop(int desk) {
		if (desk < 0) {
			return false;
		}
		String title = "远程协助";
		if (App.getStage() != null && App.getStage().getTitle() != null
				&& !App.getStage().getTitle().isEmpty()) {
			title = App.getStage().getTitle();
		}
		if (linuxFollowRun("move", String.valueOf(ProcessHandle.current().pid()),
				title, String.valueOf(desk)) == 0) {
			return true;
		}
		List<String> ids = ownWindowIds();
		boolean ok = false;
		for (String id : ids) {
			if (xdotool("set_desktop_for_window", id, String.valueOf(desk))) {
				ok = true;
			}
		}
		return ok;
	}

	private static Path linuxFollowScript() {
		Path root = PnatPaths.root();
		if (root == null) {
			return null;
		}
		Path p = root.resolve("tool").resolve("linux_follow_desktop.sh");
		return Files.isRegularFile(p) ? p : null;
	}

	private static String linuxFollowStdout(String... args) {
		List<String> lines = linuxFollowLines(args);
		return lines.isEmpty() ? "" : lines.get(0);
	}

	private static int linuxFollowRun(String... args) {
		Path sh = linuxFollowScript();
		if (sh == null) {
			return -1;
		}
		List<String> cmd = new ArrayList<String>();
		cmd.add("sh");
		cmd.add(sh.toAbsolutePath().toString());
		cmd.addAll(Arrays.asList(args));
		try {
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			return p.waitFor();
		} catch (Exception e) {
			System.err.println("linux_follow_desktop: " + e);
			return -1;
		}
	}

	private static List<String> linuxFollowLines(String... args) {
		List<String> out = new ArrayList<String>();
		Path sh = linuxFollowScript();
		if (sh == null) {
			return out;
		}
		List<String> cmd = new ArrayList<String>();
		cmd.add("sh");
		cmd.add(sh.toAbsolutePath().toString());
		cmd.addAll(Arrays.asList(args));
		try {
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			try (BufferedReader r = new BufferedReader(
					new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					line = line.trim();
					if (!line.isEmpty()) {
						out.add(line);
					}
				}
			}
			p.waitFor();
		} catch (Exception e) {
			System.err.println("linux_follow_desktop 读取失败: " + e);
		}
		return out;
	}

	private static List<String> ownWindowIds() {
		long pid = ProcessHandle.current().pid();
		return xdotoolStdoutLines("search", "--pid", String.valueOf(pid));
	}

	private static int parseIntSafe(String s, int fallback) {
		if (s == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static void sleepQuiet(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** 切换输入法。 */
	public static void toggleIme(Robot robot) {
		AssistHotkeys.fireIme(robot);
	}

	/** 屏幕键盘。 */
	public static void openOnScreenKeyboard(Robot robot) {
		AssistHotkeys.fireOsk(robot);
	}

	/**
	 * Linux：切换屏幕键盘（再按一次关闭）。
	 * onboard 优先走 D-Bus ToggleVisible；其它程序则 pkill / 再启动。
	 */
	static void launchOnScreenKeyboard() {
		cachedOsk = null;
		String cmd = resolveOsk();
		if (cmd != null) {
			String base = cmd.contains("/") ? Paths.get(cmd).getFileName().toString() : cmd;
			if ("onboard".equals(base) || cmd.endsWith("/onboard")) {
				if (toggleOnboardDbus()) {
					System.out.println("屏幕键盘: onboard ToggleVisible");
					return;
				}
				if (isProcessRunning("onboard")) {
					run("pkill", "-x", "onboard");
					System.out.println("屏幕键盘: 已关闭 onboard");
					return;
				}
				if (run(cmd)) {
					System.out.println("屏幕键盘: 启动 onboard");
					return;
				}
			}
			if (isProcessRunning(base)) {
				run("pkill", "-x", base);
				System.out.println("屏幕键盘: 已关闭 " + base);
				return;
			}
			if (run(cmd)) {
				System.out.println("屏幕键盘: " + cmd);
				return;
			}
		}
		if (desktopEnv().contains("GNOME")) {
			run("gsettings", "set", "org.gnome.desktop.a11y.applications", "screen-keyboard-enabled", "true");
			run("gdbus", "call", "--session", "--dest", "org.gnome.Shell",
					"--object-path", "/org/gnome/Shell", "--method", "org.gnome.Shell.Eval",
					"Main.keyboard.toggle();");
		}
		Dialogs.warn("未找到可用的屏幕键盘程序。\n"
				+ "请打开「Linux 依赖」复制安装命令，或：\n"
				+ "  python3 init/install.py\n"
				+ "  sudo apt install onboard");
	}

	/** onboard D-Bus：Show/Hide/ToggleVisible。 */
	private static boolean toggleOnboardDbus() {
		// 未启动时 Toggle 可能失败，先尝试拉起再 Toggle
		if (!isProcessRunning("onboard")) {
			if (!run("onboard")) {
				return false;
			}
			try {
				Thread.sleep(400);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return true; // 刚启动即视为已显示
		}
		return run("gdbus", "call", "--session",
				"--dest", "org.onboard.Onboard",
				"--object-path", "/org/onboard/Onboard/Keyboard",
				"--method", "org.onboard.Onboard.Keyboard.ToggleVisible");
	}

	private static boolean isProcessRunning(String name) {
		if (name == null || name.isEmpty() || Tools.which("pgrep") == null) {
			return false;
		}
		try {
			Process p = new ProcessBuilder("pgrep", "-x", name).start();
			return p.waitFor() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	public static String tipTaskManager() {
		return "短按：打开系统监视器/任务管理器\n"
				+ "长按 1 秒后松开：选择/自定义热键\n当前：" + AssistHotkeys.monitorLabel();
	}

	public static String tipShowDesktop() {
		return "短按：显示桌面（JRA 会留在前台）\n"
				+ "长按 1 秒后松开：选择/自定义热键\n当前：" + DesktopHotkeys.showLabel();
	}

	public static String tipSwitchDesktop() {
		return "滚轮：上一/下一工作区\n"
				+ "左键下一 · 右键上一 · 中键新建\n"
				+ "Windows：Ctrl+Win 后 hide/show\n"
				+ "Linux：有 xdotool/wmctrl 则按编号切；没有则热键 + 重建窗口\n"
				+ "长按可改热键/延时\n当前：" + DesktopHotkeys.switchLabel();
	}

	public static String tipIme() {
		return "短按：切换输入法\n"
				+ "长按 1 秒后松开：选择/自定义热键\n当前：" + AssistHotkeys.imeLabel();
	}

	public static String tipKeyboard() {
		return "短按：开/关屏幕键盘（onboard 可切换）\n"
				+ "长按 1 秒后松开：选择/自定义热键\n当前：" + AssistHotkeys.oskLabel();
	}

	public static String tipAhk() {
		return "Windows AutoHotkey（资源在包根共用 res/windows/AHK/）\n"
				+ "左键：下拉选脚本运行\n"
				+ "右键 / 对话框内：更换脚本目录（写入 ahk_dir）\n"
				+ "当前：" + AhkPaths.displayDir() + "\n"
				+ "AHK.exe：" + AhkPaths.exe().getFileName();
	}

	public static String tipMod(String name) {
		return "点一下按住「" + name + "」不放，再点松开。\n"
				+ "远程协助时 VNC/Dayon 有时传不好修饰键，\n"
				+ "在被控端用这个代替键盘按住。\n"
				+ "修饰键本身跨桌面通用。";
	}

	/** 按 DE 优先 + PATH + .desktop 扫描，缓存结果。 */
	static String resolveMonitor() {
		if (cachedMonitor != null) {
			return cachedMonitor.isEmpty() ? null : cachedMonitor;
		}
		String de = desktopEnv();
		List<String> prefer = new ArrayList<String>();
		if (de.contains("KDE") || de.contains("PLASMA")) {
			prefer.addAll(Arrays.asList("plasma-systemmonitor", "ksysguard", "ksystemstats"));
		} else if (de.contains("XFCE")) {
			prefer.addAll(Arrays.asList("xfce4-taskmanager", "gnome-system-monitor"));
		} else if (de.contains("MATE")) {
			prefer.addAll(Arrays.asList("mate-system-monitor", "gnome-system-monitor"));
		} else if (de.contains("CINNAMON")) {
			prefer.addAll(Arrays.asList("cinnamon-system-monitor", "gnome-system-monitor"));
		} else if (de.contains("DEEPIN") || de.contains("DDE")) {
			prefer.addAll(Arrays.asList("deepin-system-monitor", "gnome-system-monitor"));
		} else if (de.contains("LXQT") || de.contains("LXDE")) {
			prefer.addAll(Arrays.asList("lxqt-task-manager", "qps", "lxtask", "gnome-system-monitor"));
		} else if (de.contains("PANTHEON")) {
			prefer.addAll(Arrays.asList("gnome-system-monitor"));
		} else {
			// GNOME / Unity / Budgie / 未知：GNOME 监视器最常见
			prefer.addAll(Arrays.asList("gnome-system-monitor", "plasma-systemmonitor", "xfce4-taskmanager"));
		}
		prefer.addAll(Arrays.asList("gnome-system-monitor", "plasma-systemmonitor", "xfce4-taskmanager",
				"mate-system-monitor", "cinnamon-system-monitor", "deepin-system-monitor", "lxqt-task-manager",
				"lxtask", "qps", "ksysguard", "htop"));
		String hit = firstOnPath(prefer);
		if (hit == null) {
			hit = findDesktopExec(new String[] { "system-monitor", "task-manager", "taskmanager", "ksysguard",
					"plasma-systemmonitor" }, new String[] { "System;Monitor", "Monitor", "System" });
		}
		cachedMonitor = hit == null ? "" : hit;
		return hit;
	}

	static String resolveOsk() {
		if (cachedOsk != null) {
			return cachedOsk.isEmpty() ? null : cachedOsk;
		}
		String hit = firstOnPath(Arrays.asList("onboard", "florence", "kvkbd", "matchbox-keyboard",
				"cellwriter", "maliit-keyboard", "wvkbd-mobintl"));
		if (hit == null) {
			hit = findDesktopExec(new String[] { "onboard", "florence", "osk", "screen-keyboard", "maliit" },
					new String[] { "Accessibility", "Keyboard" });
		}
		cachedOsk = hit == null ? "" : hit;
		return hit;
	}

	private static String desktopEnv() {
		String a = System.getenv("XDG_CURRENT_DESKTOP");
		String b = System.getenv("DESKTOP_SESSION");
		String s = ((a == null ? "" : a) + ":" + (b == null ? "" : b)).toUpperCase(Locale.ROOT);
		return s;
	}

	private static String firstOnPath(List<String> names) {
		Set<String> seen = new LinkedHashSet<String>();
		for (String n : names) {
			if (!seen.add(n)) {
				continue;
			}
			String p = Tools.which(n);
			if (p != null) {
				return n;
			}
		}
		return null;
	}

	/**
	 * 扫 applications/*.desktop：文件名关键词或 Categories 命中后，取 Exec 第一段命令。
	 */
	private static String findDesktopExec(String[] nameHints, String[] categoryHints) {
		String[] roots = new String[] {
				System.getProperty("user.home") + "/.local/share/applications",
				"/usr/local/share/applications",
				"/usr/share/applications"
		};
		for (String root : roots) {
			Path dir = Paths.get(root);
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.desktop")) {
				for (Path desk : ds) {
					String file = desk.getFileName().toString().toLowerCase(Locale.ROOT);
					boolean nameOk = false;
					for (String h : nameHints) {
						if (file.contains(h)) {
							nameOk = true;
							break;
						}
					}
					String exec = null;
					String cats = "";
					try (BufferedReader r = Files.newBufferedReader(desk, StandardCharsets.UTF_8)) {
						String line;
						while ((line = r.readLine()) != null) {
							if (line.startsWith("Exec=")) {
								exec = line.substring(5).trim();
							} else if (line.startsWith("Categories=")) {
								cats = line.substring(11);
							}
						}
					} catch (IOException ignored) {
						continue;
					}
					if (exec == null || exec.isEmpty()) {
						continue;
					}
					boolean catOk = false;
					String catsUp = cats.toUpperCase(Locale.ROOT);
					for (String h : categoryHints) {
						if (catsUp.contains(h.toUpperCase(Locale.ROOT))) {
							catOk = true;
							break;
						}
					}
					if (!nameOk && !catOk) {
						continue;
					}
					String bin = execToken(exec);
					if (bin != null && (bin.contains("/") ? Files.isExecutable(Paths.get(bin)) : Tools.which(bin) != null)) {
						return bin.contains("/") ? bin : bin;
					}
				}
			} catch (IOException ignored) {
			}
		}
		return null;
	}

	private static String execToken(String exec) {
		// 去掉引号与 %u %f 等
		String s = exec.replace("\"", "").trim();
		if (s.startsWith("env ")) {
			// env VAR=x cmd → 粗略取最后一个非赋值参数
			String[] p = s.split("\\s+");
			for (int i = 1; i < p.length; i++) {
				if (p[i].indexOf('=') < 0 && !p[i].startsWith("-")) {
					return stripPercent(p[i]);
				}
			}
		}
		int sp = s.indexOf(' ');
		String first = sp < 0 ? s : s.substring(0, sp);
		return stripPercent(first);
	}

	private static String stripPercent(String s) {
		int p = s.indexOf('%');
		return p >= 0 ? s.substring(0, p) : s;
	}

	private static void chord(Robot robot, KeyCode... keys) {
		for (KeyCode k : keys) {
			robot.keyPress(k);
		}
		for (int i = keys.length - 1; i >= 0; i--) {
			robot.keyRelease(keys[i]);
		}
	}

	private static boolean xdotool(String... args) {
		if (Tools.which("xdotool") == null) {
			return false;
		}
		List<String> all = new ArrayList<String>();
		all.add("xdotool");
		all.addAll(Arrays.asList(args));
		return run(all.toArray(new String[0]));
	}

	/** 供 DesktopHotkeys 注入组合键：xdotool key --clearmodifiers &lt;combo&gt; */
	static boolean xdotoolKeyPublic(String combo) {
		if (combo == null || combo.isEmpty()) {
			return false;
		}
		return xdotool("key", "--clearmodifiers", combo);
	}

	/**
	 * 把标题包含 hint 的窗口迁到「当前」工作区（Linux/xdotool）。
	 * 用于切换工作区后让 JRA 跟过去，而不是激活后跳回原桌面。
	 */
	public static boolean moveWindowToCurrentDesktop(String titleHint) {
		if (Tools.isWindows()) {
			return moveOwnWindowsWin();
		}
		if (!Tools.linuxX11DesktopToolsOk()) {
			return false;
		}
		String hint = titleHint == null || titleHint.isEmpty() ? "远程协助" : titleHint;
		if (linuxFollowRun("move", String.valueOf(ProcessHandle.current().pid()), hint) == 0) {
			return true;
		}
		if (Tools.which("xdotool") == null) {
			return false;
		}
		String desk = xdotoolStdout("get_desktop");
		if (desk == null || desk.isEmpty()) {
			return false;
		}
		desk = desk.trim();
		List<String> ids = ownWindowIds();
		if (ids.isEmpty()) {
			ids = xdotoolStdoutLines("search", "--name", hint);
			if (ids.isEmpty()) {
				ids = xdotoolStdoutLines("search", "--name", "远程协助");
			}
		}
		boolean ok = false;
		for (String id : ids) {
			if (xdotool("set_desktop_for_window", id, desk)) {
				ok = true;
			}
		}
		return ok;
	}

	/**
	 * X11 窗口几何 [x, y, w, h]。优先 xdotool，没有则 wmctrl。
	 * 不要用 JavaFX Stage.getWidth：GTK 上会把边框吃掉、越切越小。
	 */
	public static int[] linuxWindowGeom() {
		if (Tools.isWindows()) {
			return null;
		}
		int[] g = linuxWindowGeomXdotool();
		return g != null ? g : linuxWindowGeomWmctrl();
	}

	public static boolean linuxApplyWindowGeom(int[] geom) {
		if (geom == null || geom.length < 4 || geom[2] < 50 || geom[3] < 50) {
			return false;
		}
		return linuxApplyGeomXdotool(geom) || linuxApplyGeomWmctrl(geom);
	}

	private static int[] linuxWindowGeomXdotool() {
		if (Tools.which("xdotool") == null) {
			return null;
		}
		List<String> ids = ownWindowIds();
		if (ids.isEmpty()) {
			return null;
		}
		return parseXdotoolGeom(ids.get(0));
	}

	private static boolean linuxApplyGeomXdotool(int[] geom) {
		if (Tools.which("xdotool") == null) {
			return false;
		}
		List<String> ids = ownWindowIds();
		if (ids.isEmpty()) {
			return false;
		}
		String x = String.valueOf(geom[0]);
		String y = String.valueOf(geom[1]);
		String w = String.valueOf(geom[2]);
		String h = String.valueOf(geom[3]);
		boolean ok = false;
		for (String id : ids) {
			boolean sized = xdotool("windowsize", id, w, h);
			boolean moved = xdotool("windowmove", id, x, y);
			ok = ok || sized || moved;
		}
		return ok;
	}

	private static int[] linuxWindowGeomWmctrl() {
		List<String> ids = ownWindowIdsWmctrl();
		if (ids.isEmpty()) {
			return null;
		}
		String want = ids.get(0);
		for (String line : wmctrlStdoutLines("-lG")) {
			String[] p = line.split("\\s+", 8);
			if (p.length < 6 || !idEquals(p[0], want)) {
				continue;
			}
			int x = parseIntSafe(p[2], Integer.MIN_VALUE);
			int y = parseIntSafe(p[3], Integer.MIN_VALUE);
			int w = parseIntSafe(p[4], 0);
			int h = parseIntSafe(p[5], 0);
			if (w < 50 || h < 50) {
				return null;
			}
			return new int[] { x, y, w, h };
		}
		return null;
	}

	private static boolean linuxApplyGeomWmctrl(int[] geom) {
		List<String> ids = ownWindowIdsWmctrl();
		if (ids.isEmpty()) {
			return false;
		}
		String spec = "0," + geom[0] + "," + geom[1] + "," + geom[2] + "," + geom[3];
		boolean ok = false;
		for (String id : ids) {
			if (run("wmctrl", "-i", "-r", id, "-e", spec)) {
				ok = true;
			}
		}
		return ok;
	}

	private static List<String> ownWindowIdsWmctrl() {
		List<String> ids = new ArrayList<String>();
		if (Tools.which("wmctrl") == null) {
			return ids;
		}
		String pid = String.valueOf(ProcessHandle.current().pid());
		String title = "远程协助";
		if (App.getStage() != null && App.getStage().getTitle() != null
				&& !App.getStage().getTitle().isEmpty()) {
			title = App.getStage().getTitle();
		}
		for (String line : wmctrlStdoutLines("-lp")) {
			String[] p = line.split("\\s+", 5);
			if (p.length < 5) {
				continue;
			}
			if (pid.equals(p[2]) || p[4].contains(title) || p[4].contains("远程协助")) {
				ids.add(p[0]);
			}
		}
		return ids;
	}

	private static boolean idEquals(String a, String b) {
		if (a == null || b == null) {
			return false;
		}
		try {
			return Long.decode(a.trim()) == Long.decode(b.trim());
		} catch (NumberFormatException e) {
			return a.trim().equalsIgnoreCase(b.trim());
		}
	}

	private static List<String> wmctrlStdoutLines(String... args) {
		List<String> out = new ArrayList<String>();
		if (Tools.which("wmctrl") == null) {
			return out;
		}
		List<String> cmd = new ArrayList<String>();
		cmd.add("wmctrl");
		cmd.addAll(Arrays.asList(args));
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			Process p = pb.start();
			try (BufferedReader r = new BufferedReader(
					new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					line = line.trim();
					if (!line.isEmpty()) {
						out.add(line);
					}
				}
			}
			p.waitFor();
		} catch (Exception e) {
			System.err.println("wmctrl 读取失败: " + e);
		}
		return out;
	}

	private static int[] parseXdotoolGeom(String id) {
		List<String> lines = xdotoolStdoutLines("getwindowgeometry", "--shell", id);
		int x = Integer.MIN_VALUE;
		int y = Integer.MIN_VALUE;
		int w = 0;
		int h = 0;
		for (String line : lines) {
			int eq = line.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = line.substring(0, eq).trim();
			int val = parseIntSafe(line.substring(eq + 1), Integer.MIN_VALUE);
			if ("X".equals(key)) {
				x = val;
			} else if ("Y".equals(key)) {
				y = val;
			} else if ("WIDTH".equals(key)) {
				w = val;
			} else if ("HEIGHT".equals(key)) {
				h = val;
			}
		}
		if (w < 50 || h < 50) {
			return null;
		}
		if (x == Integer.MIN_VALUE) {
			x = 0;
		}
		if (y == Integer.MIN_VALUE) {
			y = 0;
		}
		return new int[] { x, y, w, h };
	}

	private static final String WIN_VD_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VirtualDesktops";
	private static final String WIN_SESSION_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\SessionInfo";
	private static boolean winPosDiagLogged;
	private static int[] lastKnownWinPos;

	/** 切完或读到有效编号后记下，避免注册表滞后把「已在第 1」又读成 2。 */
	static void rememberWindowsPos(int[] pos) {
		if (pos != null && pos[0] > 0 && pos[1] > 0) {
			lastKnownWinPos = new int[] { pos[0], pos[1] };
		}
	}

	/**
	 * 切之前用的位置：优先内存里上次确认的编号。
	 */
	static int[] windowsPosForNav() {
		int[] known = lastKnownWinPos == null ? null : new int[] { lastKnownWinPos[0], lastKnownWinPos[1] };
		int[] read = windowsDesktopPos();
		if (known != null && known[0] > 0) {
			if (read != null && read[1] > known[1] && read[0] > 0) {
				rememberWindowsPos(read);
				return new int[] { read[0], read[1] };
			}
			lastKnownWinPos = known;
			return known;
		}
		rememberWindowsPos(read);
		return read;
	}

	/**
	 * Windows 当前桌面位置：{第几个(1起), 总数}；index 为 0 表示只读到总数。
	 * 优先 reg export（.reg 是 UTF-16，不踩 query 编码），再 query，再无文件 PowerShell。
	 */
	static int[] windowsDesktopPos() {
		if (!Tools.isWindows()) {
			return null;
		}
		int[] best = pickWinPos(windowsDesktopPosFromExport(), windowsDesktopPosFromReg());
		if (best != null && best[0] > 0) {
			return best;
		}
		int[] fromPs = pickWinPos(windowsDesktopPosFromEncodedPs(), windowsDesktopPosFromPs1());
		if (fromPs != null) {
			return fromPs;
		}
		if (best != null) {
			return best;
		}
		logWinPosFailOnce();
		return null;
	}

	private static int[] pickWinPos(int[] a, int[] b) {
		int[] x = betterWinPos(a, b);
		return x;
	}

	private static int[] betterWinPos(int[] a, int[] b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		if (a[0] > 0 && b[0] <= 0) {
			return a;
		}
		if (b[0] > 0 && a[0] <= 0) {
			return b;
		}
		if (b[1] > a[1]) {
			return b;
		}
		return a;
	}

	private static int[] windowsDesktopPosFromExport() {
		String vd = regExport(WIN_VD_KEY);
		int[] pos = posFromRegText(vd, true);
		if (pos != null && pos[0] > 0) {
			return pos;
		}
		String sess = regExport(WIN_SESSION_KEY);
		int[] sessPos = posFromRegText(sess, true);
		if (sessPos != null && sessPos[0] > 0) {
			return sessPos;
		}
		byte[] cur = firstNonNull(parseRegExportHex(vd, "CurrentVirtualDesktop"),
				parseRegExportSzGuid(vd, "CurrentVirtualDesktop"),
				parseRegExportHex(sess, "CurrentVirtualDesktop"),
				parseRegExportSzGuid(sess, "CurrentVirtualDesktop"));
		byte[] ids = firstNonNull(parseRegExportHex(vd, "VirtualDesktopIDs"),
				parseRegExportHex(sess, "VirtualDesktopIDs"));
		int[] mixed = finishWinPos(cur, ids, vd + "\n" + sess);
		return betterWinPos(betterWinPos(pos, sessPos), mixed);
	}

	private static int[] windowsDesktopPosFromReg() {
		String parent = runRegQuery(WIN_VD_KEY);
		int[] pos = posFromRegText(parent, false);
		if (pos != null && pos[0] > 0) {
			return pos;
		}
		byte[] parentCur = parseRegValueBytes(parent, "CurrentVirtualDesktop");
		String sessions = runRegQuery(WIN_SESSION_KEY);
		if (sessions == null || sessions.isEmpty()) {
			return pos;
		}
		int[] best = pos;
		String[] lines = sessions.split("\\r?\\n");
		for (int i = 0; i < lines.length; i++) {
			String t = lines[i].trim();
			if (t.toUpperCase(Locale.ROOT).indexOf("SESSIONINFO\\") < 0) {
				continue;
			}
			String blob = runRegQuery(t + "\\VirtualDesktops");
			best = betterWinPos(best, posFromRegText(blob, false));
			if (best != null && best[0] > 0) {
				return best;
			}
			if (parentCur != null) {
				best = betterWinPos(best, finishWinPos(parentCur, parseRegValueBytes(blob, "VirtualDesktopIDs"), blob));
				if (best != null && best[0] > 0) {
					return best;
				}
			}
		}
		return best;
	}

	private static int[] posFromRegText(String blob, boolean exportFormat) {
		if (blob == null || blob.isEmpty()) {
			return null;
		}
		byte[] cur = exportFormat
				? firstNonNull(parseRegExportHex(blob, "CurrentVirtualDesktop"),
						parseRegExportSzGuid(blob, "CurrentVirtualDesktop"))
				: parseRegValueBytes(blob, "CurrentVirtualDesktop");
		byte[] ids = exportFormat ? parseRegExportHex(blob, "VirtualDesktopIDs")
				: parseRegValueBytes(blob, "VirtualDesktopIDs");
		return finishWinPos(cur, ids, blob);
	}

	private static int[] finishWinPos(byte[] cur, byte[] ids, String blob) {
		int[] matched = indexInDesktopIds(cur, ids);
		if (matched != null) {
			return matched;
		}
		int num = ids != null && ids.length >= 16 ? ids.length / 16 : 0;
		List<String> names = collectDesktopSubkeys(blob);
		if (!names.isEmpty()) {
			int idx = indexInDesktopNames(cur, names);
			int n = Math.max(num, names.size());
			return new int[] { idx >= 0 ? idx + 1 : 0, n };
		}
		if (num > 0) {
			return new int[] { 0, num };
		}
		return null;
	}

	private static byte[] firstNonNull(byte[] a, byte[] b, byte[] c, byte[] d) {
		if (a != null && a.length >= 16) {
			return a;
		}
		if (b != null && b.length >= 16) {
			return b;
		}
		if (c != null && c.length >= 16) {
			return c;
		}
		if (d != null && d.length >= 16) {
			return d;
		}
		return null;
	}

	private static byte[] firstNonNull(byte[] a, byte[] b) {
		if (a != null && a.length >= 16) {
			return a;
		}
		if (b != null && b.length >= 16) {
			return b;
		}
		return null;
	}

	private static int[] indexInDesktopIds(byte[] cur, byte[] ids) {
		if (cur == null || cur.length < 16 || ids == null || ids.length < 16) {
			return null;
		}
		int num = ids.length / 16;
		byte[] c16 = Arrays.copyOf(cur, 16);
		for (int i = 0; i < num; i++) {
			byte[] slice = Arrays.copyOfRange(ids, i * 16, i * 16 + 16);
			if (sameDesktopGuid(c16, slice)) {
				return new int[] { i + 1, num };
			}
		}
		return null;
	}

	private static boolean sameDesktopGuid(byte[] a, byte[] b) {
		if (a == null || b == null || a.length < 16 || b.length < 16) {
			return false;
		}
		if (equal16(a, b)) {
			return true;
		}
		return equal16(a, guidEndianSwap(Arrays.copyOf(b, 16)))
				|| equal16(guidEndianSwap(Arrays.copyOf(a, 16)), b);
	}

	private static boolean equal16(byte[] a, byte[] b) {
		for (int i = 0; i < 16; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	/** Windows GUID 与 UUID 字节序：前 3 段小端，后 8 字节原样。 */
	private static byte[] guidEndianSwap(byte[] u) {
		byte[] w = new byte[16];
		w[0] = u[3];
		w[1] = u[2];
		w[2] = u[1];
		w[3] = u[0];
		w[4] = u[5];
		w[5] = u[4];
		w[6] = u[7];
		w[7] = u[6];
		System.arraycopy(u, 8, w, 8, 8);
		return w;
	}

	private static byte[] parseRegValueBytes(String blob, String valueName) {
		if (blob == null || valueName == null) {
			return null;
		}
		String want = valueName.toUpperCase(Locale.ROOT);
		String[] lines = blob.split("\\r?\\n");
		for (int i = 0; i < lines.length; i++) {
			String raw = lines[i].trim();
			if (!raw.isEmpty() && raw.charAt(0) == '\uFEFF') {
				raw = raw.substring(1).trim();
			}
			if (raw.isEmpty()) {
				continue;
			}
			String upper = raw.toUpperCase(Locale.ROOT);
			if (!upper.startsWith(want)) {
				int at = upper.indexOf(want);
				if (at < 0 || at > 2) {
					continue;
				}
				raw = raw.substring(at);
				upper = raw.toUpperCase(Locale.ROOT);
			}
			String rest = raw.substring(valueName.length()).trim();
			int sp = indexOfWs(rest);
			if (sp < 0) {
				continue;
			}
			String type = rest.substring(0, sp).trim().toUpperCase(Locale.ROOT);
			String data = rest.substring(sp).trim();
			if (type.indexOf("BINARY") >= 0) {
				StringBuilder hex = new StringBuilder(data);
				for (int k = i + 1; k < lines.length; k++) {
					String nxt = lines[k].trim();
					if (nxt.isEmpty() || !looksLikeHexLine(nxt)) {
						break;
					}
					hex.append(nxt);
				}
				return hexToBytes(hex.toString());
			}
			if (type.indexOf("SZ") >= 0) {
				return guidStringToWindowsBytes(data);
			}
		}
		return null;
	}

	private static boolean looksLikeHexLine(String s) {
		if (s == null || s.isEmpty()) {
			return false;
		}
		String u = s.toUpperCase(Locale.ROOT);
		if (u.startsWith("HKEY_") || u.indexOf("REG_") >= 0) {
			return false;
		}
		int hex = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (Character.isWhitespace(c) || c == ',' || c == '\\') {
				continue;
			}
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
				hex++;
				continue;
			}
			return false;
		}
		return hex >= 2;
	}

	private static int indexOfWs(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (Character.isWhitespace(s.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	private static byte[] guidStringToWindowsBytes(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		if (t.startsWith("{") && t.endsWith("}") && t.length() >= 2) {
			t = t.substring(1, t.length() - 1);
		}
		byte[] uuid = hexToBytes(t);
		if (uuid == null || uuid.length != 16) {
			return null;
		}
		return guidEndianSwap(uuid);
	}

	private static byte[] hexToBytes(String hex) {
		if (hex == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder(hex.length());
		for (int i = 0; i < hex.length(); i++) {
			char c = hex.charAt(i);
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
				sb.append(c);
			}
		}
		if (sb.length() < 2 || (sb.length() & 1) != 0) {
			return null;
		}
		int n = sb.length() / 2;
		byte[] out = new byte[n];
		for (int i = 0; i < n; i++) {
			out[i] = (byte) Integer.parseInt(sb.substring(i * 2, i * 2 + 2), 16);
		}
		return out;
	}

	private static String hexUpper16(byte[] b) {
		StringBuilder sb = new StringBuilder(32);
		for (int i = 0; i < 16; i++) {
			sb.append(String.format("%02X", b[i] & 0xff));
		}
		return sb.toString();
	}

	private static String runRegQuery(String key) {
		if (key == null || key.isEmpty()) {
			return "";
		}
		try {
			ProcessBuilder pb = new ProcessBuilder(regExe(), "query", key);
			pb.redirectErrorStream(true);
			Process p = pb.start();
			byte[] raw = p.getInputStream().readAllBytes();
			if (!p.waitFor(6, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return "";
			}
			return decodeWindowsBytes(raw);
		} catch (Exception e) {
			return "";
		}
	}

	private static String regExport(String key) {
		if (key == null || key.isEmpty()) {
			return "";
		}
		Path tmp = null;
		try {
			tmp = Files.createTempFile("jra-vd-", ".reg");
			ProcessBuilder pb = new ProcessBuilder(regExe(), "export", key, tmp.toAbsolutePath().toString(), "/y");
			pb.redirectErrorStream(true);
			Process p = pb.start();
			p.getInputStream().readAllBytes();
			if (!p.waitFor(8, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return "";
			}
			if (p.exitValue() != 0 || !Files.isRegularFile(tmp) || Files.size(tmp) < 8) {
				return "";
			}
			return decodeWindowsBytes(Files.readAllBytes(tmp));
		} catch (Exception e) {
			return "";
		} finally {
			if (tmp != null) {
				try {
					Files.deleteIfExists(tmp);
				} catch (Exception ignored) {
				}
			}
		}
	}

	private static String decodeWindowsBytes(byte[] raw) {
		if (raw == null || raw.length == 0) {
			return "";
		}
		if (raw.length >= 2 && raw[0] == (byte) 0xFF && raw[1] == (byte) 0xFE) {
			return new String(raw, StandardCharsets.UTF_16LE);
		}
		if (raw.length >= 2 && raw[0] == (byte) 0xFE && raw[1] == (byte) 0xFF) {
			return new String(raw, StandardCharsets.UTF_16BE);
		}
		int zeros = 0;
		int sample = Math.min(raw.length, 120);
		for (int i = 1; i < sample; i += 2) {
			if (raw[i] == 0) {
				zeros++;
			}
		}
		if (sample >= 16 && zeros >= sample / 6) {
			return new String(raw, StandardCharsets.UTF_16LE);
		}
		try {
			return new String(raw, Charset.forName("GBK"));
		} catch (Exception e) {
			return new String(raw, StandardCharsets.ISO_8859_1);
		}
	}

	private static byte[] parseRegExportHex(String content, String valueName) {
		if (content == null || valueName == null) {
			return null;
		}
		String lower = content.toLowerCase(Locale.ROOT);
		String needle = "\"" + valueName.toLowerCase(Locale.ROOT) + "\"=hex:";
		int at = lower.indexOf(needle);
		if (at < 0) {
			return null;
		}
		int i = at + needle.length();
		StringBuilder hex = new StringBuilder();
		while (i < content.length()) {
			char c = content.charAt(i);
			if (c == '\\' || c == ',' || Character.isWhitespace(c)) {
				i++;
				continue;
			}
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
				hex.append(c);
				i++;
				continue;
			}
			break;
		}
		return hexToBytes(hex.toString());
	}

	private static byte[] parseRegExportSzGuid(String content, String valueName) {
		if (content == null || valueName == null) {
			return null;
		}
		String lower = content.toLowerCase(Locale.ROOT);
		String needle = "\"" + valueName.toLowerCase(Locale.ROOT) + "\"=\"";
		int at = lower.indexOf(needle);
		if (at < 0) {
			return null;
		}
		int i = at + needle.length();
		int end = content.indexOf('"', i);
		if (end < 0) {
			return null;
		}
		return guidStringToWindowsBytes(content.substring(i, end));
	}

	private static List<String> collectDesktopSubkeys(String content) {
		List<String> out = new ArrayList<String>();
		if (content == null || content.isEmpty()) {
			return out;
		}
		String[] lines = content.split("\\r?\\n");
		for (int i = 0; i < lines.length; i++) {
			String t = lines[i].trim();
			if (t.startsWith("[")) {
				t = t.substring(1);
			}
			String u = t.toLowerCase(Locale.ROOT);
			int at = u.lastIndexOf("\\desktops\\");
			if (at < 0) {
				continue;
			}
			int start = at + "\\desktops\\".length();
			int end = t.indexOf(']', start);
			if (end < 0) {
				end = t.length();
			}
			if (start >= end) {
				continue;
			}
			String id = t.substring(start, end).trim();
			if (!id.isEmpty() && id.indexOf('\\') < 0) {
				out.add(id);
			}
		}
		return out;
	}

	private static int indexInDesktopNames(byte[] cur, List<String> names) {
		if (cur == null || cur.length < 16 || names == null || names.isEmpty()) {
			return -1;
		}
		String g1 = guidBytesToString(Arrays.copyOf(cur, 16));
		String g2 = guidBytesToString(guidEndianSwap(Arrays.copyOf(cur, 16)));
		for (int i = 0; i < names.size(); i++) {
			String n = names.get(i).replace("{", "").replace("}", "").toLowerCase(Locale.ROOT);
			if (n.equals(g1) || n.equals(g2)) {
				return i;
			}
		}
		return -1;
	}

	private static String guidBytesToString(byte[] b) {
		int d1 = (b[0] & 0xff) | ((b[1] & 0xff) << 8) | ((b[2] & 0xff) << 16) | ((b[3] & 0xff) << 24);
		int d2 = (b[4] & 0xff) | ((b[5] & 0xff) << 8);
		int d3 = (b[6] & 0xff) | ((b[7] & 0xff) << 8);
		return String.format(Locale.ROOT, "%08x-%04x-%04x-%02x%02x-%02x%02x%02x%02x%02x%02x", Integer.valueOf(d1),
				Integer.valueOf(d2), Integer.valueOf(d3), Integer.valueOf(b[8] & 0xff), Integer.valueOf(b[9] & 0xff),
				Integer.valueOf(b[10] & 0xff), Integer.valueOf(b[11] & 0xff), Integer.valueOf(b[12] & 0xff),
				Integer.valueOf(b[13] & 0xff), Integer.valueOf(b[14] & 0xff), Integer.valueOf(b[15] & 0xff));
	}

	private static String regExe() {
		return windowsSystem32("reg.exe");
	}

	private static String powershellExe() {
		String windir = windowsDir();
		Path p = Paths.get(windir, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
		return Files.isRegularFile(p) ? p.toString() : "powershell.exe";
	}

	private static String windowsSystem32(String exe) {
		Path p = Paths.get(windowsDir(), "System32", exe);
		return Files.isRegularFile(p) ? p.toString() : exe;
	}

	private static String windowsDir() {
		String windir = System.getenv("WINDIR");
		return windir == null || windir.isEmpty() ? "C:\\Windows" : windir;
	}

	private static final String PS_QUERY_POS = "$ErrorActionPreference='SilentlyContinue';"
			+ "function Emit($ids,$cur){ if($null -eq $ids -or $ids -isnot [byte[]] -or $ids.Length -lt 16){return $false};"
			+ "$num=[int]($ids.Length/16); $idx=-1;"
			+ "if($cur -is [byte[]] -and $cur.Length -ge 16){ for($i=0;$i -lt $num;$i++){ $ok=$true; for($j=0;$j -lt 16;$j++){ if($ids[$i*16+$j] -ne $cur[$j]){ $ok=$false; break } }; if($ok){ $idx=$i; break } } }"
			+ "elseif($cur -is [string] -and $cur){ try{ $g=[guid]$cur; for($i=0;$i -lt $num;$i++){ $sl=New-Object byte[] 16; [Array]::Copy($ids,$i*16,$sl,0,16); if((New-Object Guid (,$sl)) -eq $g){ $idx=$i; break } } }catch{} };"
			+ "if($idx -lt 0){ $idx=0 }; Write-Output ('{0} {1}' -f ($idx+1),$num); return $true };"
			+ "$paths=@('HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VirtualDesktops');"
			+ "$sid=[Diagnostics.Process]::GetCurrentProcess().SessionId;"
			+ "$paths += ('HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\SessionInfo\\'+$sid+'\\VirtualDesktops');"
			+ "foreach($p in $paths){ if(Test-Path $p){ $pr=Get-ItemProperty $p; if(Emit $pr.VirtualDesktopIDs $pr.CurrentVirtualDesktop){ exit 0 } } };"
			+ "$root='HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VirtualDesktops\\Desktops';"
			+ "if(Test-Path $root){ $ks=@(Get-ChildItem $root); if($ks.Count -gt 0){ $idx=0; $pr=Get-ItemProperty 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VirtualDesktops';"
			+ "$cur=$pr.CurrentVirtualDesktop; if($cur -is [byte[]] -and $cur.Length -ge 16){ try{ $g=New-Object Guid (,$cur); for($i=0;$i -lt $ks.Count;$i++){ try{ if([guid]$ks[$i].PSChildName -eq $g){ $idx=$i; break } }catch{} } }catch{} };"
			+ "Write-Output ('{0} {1}' -f ($idx+1),$ks.Count); exit 0 } }; exit 2";

	private static int[] windowsDesktopPosFromEncodedPs() {
		try {
			ProcessBuilder pb = new ProcessBuilder(powershellExe(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
					"Bypass", "-EncodedCommand",
					Base64.getEncoder().encodeToString(PS_QUERY_POS.getBytes(StandardCharsets.UTF_16LE)));
			pb.redirectErrorStream(true);
			Process p = pb.start();
			String line = "";
			try (BufferedReader r = new BufferedReader(
					new InputStreamReader(p.getInputStream(), StandardCharsets.ISO_8859_1))) {
				String s;
				while ((s = r.readLine()) != null) {
					s = s.trim();
					if (s.matches("\\d+\\s+\\d+")) {
						line = s;
					}
				}
			}
			if (!p.waitFor(20, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return null;
			}
			return parsePosLine(line);
		} catch (Exception e) {
			return null;
		}
	}

	private static int[] parsePosLine(String line) {
		if (line == null || line.isEmpty() || !line.matches("\\d+\\s+\\d+")) {
			return null;
		}
		String[] p2 = line.split("\\s+");
		int cur = Integer.parseInt(p2[0]);
		int num = Integer.parseInt(p2[1]);
		if (num < 1) {
			return null;
		}
		return new int[] { cur, num };
	}

	private static void logWinPosFailOnce() {
		if (winPosDiagLogged) {
			return;
		}
		winPosDiagLogged = true;
		String vd = regExport(WIN_VD_KEY);
		String q = runRegQuery(WIN_VD_KEY);
		Path script = winFollowScript();
		System.out.println("Windows 读不到桌面编号（注册表/脚本）");
		System.out.println("  export=" + vd.length() + "B query=" + q.length() + "B script="
				+ (script == null ? "无" : script.toString()));
		String preview = q.isEmpty() ? vd : q;
		if (preview.length() > 360) {
			preview = preview.substring(0, 360);
		}
		System.out.println("  preview=" + preview.replace('\n', '|').replace('\r', ' '));
	}

	private static int[] windowsDesktopPosFromPs1() {
		Path script = winFollowScript();
		if (script == null) {
			return null;
		}
		try {
			ProcessBuilder pb = new ProcessBuilder(powershellExe(), "-NoProfile", "-ExecutionPolicy", "Bypass",
					"-File", script.toAbsolutePath().toString(), "-QueryPos");
			pb.redirectErrorStream(true);
			Process p = pb.start();
			String line = "";
			try (BufferedReader r = new BufferedReader(
					new InputStreamReader(p.getInputStream(), StandardCharsets.ISO_8859_1))) {
				String s;
				while ((s = r.readLine()) != null) {
					s = s.trim();
					if (s.matches("\\d+\\s+\\d+")) {
						line = s;
					}
				}
			}
			if (!p.waitFor(12, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return null;
			}
			return parsePosLine(line);
		} catch (Exception e) {
			return null;
		}
	}

	private static Path winFollowScript() {
		Path root = PnatPaths.root();
		if (root == null) {
			return null;
		}
		Path script = root.resolve("tool").resolve("win_follow_desktop.ps1");
		return Files.isRegularFile(script) ? script : null;
	}

	/** Windows 当前虚拟桌面 GUID；失败返回空。用于判断切到头没有。 */
	static String windowsDesktopId() {
		if (!Tools.isWindows()) {
			return "";
		}
		String vd = regExport(WIN_VD_KEY);
		byte[] cur = firstNonNull(parseRegExportHex(vd, "CurrentVirtualDesktop"),
				parseRegExportSzGuid(vd, "CurrentVirtualDesktop"));
		if (cur == null || cur.length < 16) {
			String parent = runRegQuery(WIN_VD_KEY);
			cur = parseRegValueBytes(parent, "CurrentVirtualDesktop");
		}
		if (cur == null || cur.length < 16) {
			String sessions = runRegQuery(WIN_SESSION_KEY);
			if (sessions != null && !sessions.isEmpty()) {
				String[] lines = sessions.split("\\r?\\n");
				for (int i = 0; i < lines.length; i++) {
					String t = lines[i].trim();
					if (t.toUpperCase(Locale.ROOT).indexOf("SESSIONINFO\\") < 0) {
						continue;
					}
					cur = parseRegValueBytes(runRegQuery(t + "\\VirtualDesktops"), "CurrentVirtualDesktop");
					if (cur != null && cur.length >= 16) {
						break;
					}
				}
			}
		}
		if (cur != null && cur.length >= 16) {
			return hexUpper16(Arrays.copyOf(cur, 16));
		}
		return windowsDesktopIdFromPs1();
	}

	private static String windowsDesktopIdFromPs1() {
		Path script = winFollowScript();
		if (script == null) {
			return "";
		}
		try {
			ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
					"-File", script.toAbsolutePath().toString(), "-QueryId");
			Process p = pb.start();
			String id = "";
			try (BufferedReader r = new BufferedReader(
					new InputStreamReader(p.getInputStream(), Charset.forName("ISO-8859-1")))) {
				String line;
				while ((line = r.readLine()) != null) {
					line = line.trim();
					if (!line.isEmpty() && line.indexOf('-') >= 0) {
						id = line;
					}
				}
			}
			if (!p.waitFor(8, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return "";
			}
			return id;
		} catch (Exception e) {
			return "";
		}
	}

	private static boolean moveOwnWindowsWin() {
		Path root = PnatPaths.root();
		if (root == null) {
			return false;
		}
		Path script = root.resolve("tool").resolve("win_follow_desktop.ps1");
		if (!Files.isRegularFile(script)) {
			System.err.println("缺少 " + script);
			return false;
		}
		try {
			ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
					"-File", script.toAbsolutePath().toString(), "-ProcId",
					String.valueOf(ProcessHandle.current().pid()), "-Title", "远程协助");
			pb.redirectErrorStream(true);
			Process p = pb.start();
			int code = p.waitFor();
			System.out.println(code == 0 ? "JRA 已跟到当前 Windows 桌面" : "Windows 跟随桌面失败 exit=" + code);
			return code == 0;
		} catch (Exception e) {
			System.err.println("win_follow_desktop: " + e);
			return false;
		}
	}

	private static String xdotoolStdout(String... args) {
		List<String> lines = xdotoolStdoutLines(args);
		return lines.isEmpty() ? "" : lines.get(0);
	}

	private static List<String> xdotoolStdoutLines(String... args) {
		List<String> out = new ArrayList<String>();
		if (Tools.which("xdotool") == null) {
			return out;
		}
		List<String> cmd = new ArrayList<String>();
		cmd.add("xdotool");
		cmd.addAll(Arrays.asList(args));
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			Process p = pb.start();
			try (BufferedReader r = new BufferedReader(
					new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					line = line.trim();
					if (!line.isEmpty()) {
						out.add(line);
					}
				}
			}
			p.waitFor();
		} catch (Exception e) {
			System.err.println("xdotool 读取失败: " + e);
		}
		return out;
	}

	private static boolean run(String... cmd) {
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			Process p = pb.start();
			if (cmd.length > 0 && ("wmctrl".equals(cmd[0]) || "xdotool".equals(cmd[0]) || "gsettings".equals(cmd[0]))) {
				return p.waitFor() == 0;
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
