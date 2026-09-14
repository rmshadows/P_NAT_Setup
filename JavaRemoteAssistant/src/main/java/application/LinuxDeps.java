package application;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Linux 依赖探测。Python 库看 JRA 实际调用的 python3（PATH 上的那个，可能是 venv）。
 */
public final class LinuxDeps {
	private LinuxDeps() {
	}

	/** JRA 跑 Dayon / 录热键时用的 python3。 */
	public static final class PythonEnv {
		public final String path;
		public final boolean venv;
		public final String venvRoot;
		public final boolean xlibOk;
		public final boolean cryptoOk;
		public final boolean aptXlib;
		public final boolean aptCrypto;

		PythonEnv(String path, boolean venv, String venvRoot, boolean xlibOk, boolean cryptoOk,
				boolean aptXlib, boolean aptCrypto) {
			this.path = path;
			this.venv = venv;
			this.venvRoot = venvRoot == null ? "" : venvRoot;
			this.xlibOk = xlibOk;
			this.cryptoOk = cryptoOk;
			this.aptXlib = aptXlib;
			this.aptCrypto = aptCrypto;
		}
	}

	public static final class Item {
		public final String apt;
		public final boolean required;
		public final String why;
		public final boolean ok;

		Item(String apt, boolean required, String why, boolean ok) {
			this.apt = apt;
			this.required = required;
			this.why = why;
			this.ok = ok;
		}
	}

	public static List<Item> probe() {
		return probe(pythonEnv());
	}

	static List<Item> probe(PythonEnv env) {
		List<Item> out = new ArrayList<Item>();
		out.add(item("python3", true, "加载 Dayon / Linux VNC（deploy 脚本）",
				env.path != null && !env.path.isEmpty()));
		out.add(item("xdotool", false, "X11 发键 / 跟窗（没有则热键+重建窗口）", Tools.which("xdotool") != null));
		out.add(item("wmctrl", false, "X11 工作区编号（可选）", Tools.which("wmctrl") != null));
		out.add(item("onboard", false, "屏幕键盘", Tools.which("onboard") != null));
		out.add(item("gnome-system-monitor", false, "系统监视器（已有其它监视器也算齐）", hasSystemMonitor()));
		out.add(item("x11vnc", false, "Linux VNC :0 当前屏（推荐，可反向连出）",
				Tools.which("x11vnc") != null || Files.isExecutable(Paths.get("/usr/bin/x11vnc"))));
		out.add(item("tigervnc-scraping-server", false, "Linux VNC 备选（x0vncserver，只监听）",
				Tools.which("x0vncserver") != null || Files.isExecutable(Paths.get("/usr/bin/x0vncserver"))));
		out.add(item("python3-xlib", false, "录热键：要装进当前这个 python3", env.xlibOk));
		out.add(item("python3-pycryptodome", false, "加密 conf：要装进当前这个 python3", env.cryptoOk));
		return out;
	}

	private static Item item(String apt, boolean required, String why, boolean ok) {
		return new Item(apt, required, why, ok);
	}

	public static List<String> missingApt() {
		PythonEnv env = pythonEnv();
		List<String> miss = new ArrayList<String>();
		for (Item i : probe(env)) {
			if (i.ok) {
				continue;
			}
			if (env.venv && isPythonLib(i.apt)) {
				continue;
			}
			miss.add(i.apt);
		}
		return miss;
	}

	private static boolean isPythonLib(String apt) {
		return "python3-xlib".equals(apt) || "python3-pycryptodome".equals(apt);
	}

	public static int missingCount() {
		int n = 0;
		for (Item i : probe()) {
			if (!i.ok) {
				n++;
			}
		}
		return n;
	}

	/** 按勾选的包生成 apt 命令。空列表返回空字符串。 */
	public static String installCommand(List<String> pkgs, boolean withInit) {
		if (pkgs == null || pkgs.isEmpty()) {
			return "";
		}
		List<String> aptPkgs = new ArrayList<String>();
		for (String p : pkgs) {
			if (p == null || p.trim().isEmpty()) {
				continue;
			}
			aptPkgs.add(p.trim());
		}
		if (aptPkgs.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("sudo apt install -y");
		for (String p : aptPkgs) {
			sb.append(' ').append(p);
		}
		if (withInit) {
			sb.append('\n');
			Path root = PnatPaths.root();
			if (root != null) {
				sb.append("cd ").append(root.toAbsolutePath()).append(" && python3 init/install.py");
			} else {
				sb.append("python3 init/install.py");
			}
		}
		return sb.toString();
	}

	public static String installCommand() {
		List<String> miss = missingApt();
		if (miss.isEmpty()) {
			return "";
		}
		return installCommand(miss, false);
	}

	public static String captureHelp() {
		PythonEnv env = pythonEnv();
		StringBuilder sb = new StringBuilder();
		sb.append(pythonHint(env)).append('\n');
		List<String> miss = missingApt();
		if (miss.isEmpty() && env.xlibOk) {
			sb.append("依赖已齐。");
		} else if (!miss.isEmpty()) {
			sb.append("缺少 apt：").append(String.join(" ", miss));
		}
		return sb.toString();
	}

	public static String buttonLabel() {
		int n = missingCount();
		if (n <= 0) {
			return "Linux 依赖…";
		}
		return "Linux 依赖（缺 " + n + "）…";
	}

	public static PythonEnv pythonEnv() {
		String path = resolveJraPython();
		boolean aptXlib = aptInstalled("python3-xlib");
		boolean aptCrypto = aptInstalled("python3-pycryptodome");
		if (path == null) {
			return new PythonEnv(null, false, "", false, false, aptXlib, aptCrypto);
		}
		boolean venv = false;
		String venvRoot = "";
		String virt = Tools.env("VIRTUAL_ENV");
		if (!virt.isEmpty()) {
			venv = true;
			venvRoot = virt;
		}
		String[] info = pythonSysInfo(path);
		if (info != null) {
			if (info[0] != null && !info[0].isEmpty()) {
				path = info[0];
			}
			if (!venv && info[1] != null && info[2] != null && !info[1].isEmpty() && !info[1].equals(info[2])) {
				venv = true;
				venvRoot = info[1];
			}
			if (info[3] != null && !info[3].isEmpty()) {
				venv = true;
				venvRoot = info[3];
			}
		}
		if (!venv && looksLikeVenv(path)) {
			venv = true;
			Path p = Paths.get(path).toAbsolutePath().normalize();
			Path parent = p.getParent();
			if (parent != null && "bin".equals(parent.getFileName().toString()) && parent.getParent() != null) {
				venvRoot = parent.getParent().toString();
			}
		}
		boolean xlib = pythonImportWith(path, "Xlib");
		boolean crypto = pythonImportWith(path, "Crypto") || pythonImportWith(path, "Cryptodome");
		return new PythonEnv(path, venv, venvRoot, xlib, crypto, aptXlib, aptCrypto);
	}

	public static String pythonHint(PythonEnv env) {
		StringBuilder sb = new StringBuilder();
		if (env.path == null || env.path.isEmpty()) {
			return "JRA 找不到 python3（PATH 里没有）。加载 Dayon / 录热键都会失败。";
		}
		sb.append("JRA 实际调用的 python3（Dayon、录热键都走它）：\n");
		sb.append(env.path).append('\n');
		if (Tools.useShellEnv()) {
			sb.append("已启用「使用终端环境」");
			String conf = Tools.shellSourceConf();
			if (Tools.SHELL_SOURCE_AUTO.equalsIgnoreCase(conf)) {
				sb.append("（自动推荐）");
			}
			Path src = Tools.resolvedShellSource();
			if (src != null) {
				sb.append("：source ").append(Tools.tildeHome(src));
			} else {
				sb.append("：所选文件找不到，改走 SHELL -i/-l");
			}
			sb.append("。\n");
		} else {
			sb.append("未启用终端环境：用 JRA 进程的 PATH（桌面/IDE 启动通常是系统 Python）。可在 JRA设置勾选。\n");
		}
		if (env.venv) {
			sb.append("环境：虚拟环境");
			if (!env.venvRoot.isEmpty()) {
				sb.append('\n').append(env.venvRoot);
			}
			sb.append('\n');
		} else {
			sb.append("环境：系统 Python（不是 venv）\n");
		}
		sb.append("此环境  Xlib ").append(env.xlibOk ? "有" : "没有");
		sb.append("  ·  Crypto ").append(env.cryptoOk ? "有" : "没有").append('\n');
		sb.append("系统 apt  python3-xlib ").append(env.aptXlib ? "已装" : "未装");
		sb.append("  ·  pycryptodome ").append(env.aptCrypto ? "已装" : "未装");
		if (env.venv && (env.aptXlib || env.aptCrypto)) {
			sb.append("\n（apt 给 /usr/bin/python3 用，当前 venv 用不到）");
		}
		sb.append('\n');
		sb.append("Debian 13 默认不能对系统 pip。");
		if (env.venv) {
			sb.append(" 当前是 venv，Python 库用 pip；xdotool 等仍用 apt。");
		} else {
			sb.append(" 当前是系统 Python，Python 库用 apt，不要 pip。");
		}
		return sb.toString();
	}

	public static String venvPipCommand(PythonEnv env) {
		if (env == null || env.path == null || env.path.isEmpty() || !env.venv) {
			return "";
		}
		return env.path + " -m pip install python-xlib pycryptodome";
	}

	public static String resolveJraPython() {
		return Tools.python3OrNull();
	}

	private static boolean looksLikeVenv(String pythonPath) {
		try {
			Path p = Paths.get(pythonPath).toAbsolutePath().normalize();
			Path bin = p.getParent();
			if (bin == null) {
				return false;
			}
			if (Files.isRegularFile(bin.resolve("pyvenv.cfg"))) {
				return true;
			}
			return bin.getParent() != null && Files.isRegularFile(bin.getParent().resolve("pyvenv.cfg"));
		} catch (Exception ignored) {
			return false;
		}
	}

	/** [0] executable [1] prefix [2] base_prefix [3] VIRTUAL_ENV */
	private static String[] pythonSysInfo(String py) {
		try {
			ProcessBuilder pb = new ProcessBuilder(py, "-c",
					"import sys,os; print(sys.executable); print(sys.prefix); print(getattr(sys,'base_prefix',sys.prefix)); print(os.environ.get('VIRTUAL_ENV',''))");
			pb.redirectErrorStream(true);
			Process p = pb.start();
			String out = readAll(p.getInputStream());
			if (p.waitFor() != 0) {
				return null;
			}
			String[] lines = out.replace("\r", "").split("\n");
			String[] r = new String[] { "", "", "", "" };
			for (int i = 0; i < r.length && i < lines.length; i++) {
				r[i] = lines[i].trim();
			}
			return r;
		} catch (Exception e) {
			return null;
		}
	}

	static boolean pythonImportWith(String py, String mod) {
		if (py == null || py.isEmpty()) {
			return false;
		}
		try {
			ProcessBuilder pb = new ProcessBuilder(py, "-c", "import " + mod);
			pb.redirectErrorStream(true);
			Process p = pb.start();
			readAll(p.getInputStream());
			return p.waitFor() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	static boolean aptInstalled(String pkg) {
		try {
			ProcessBuilder pb = new ProcessBuilder("dpkg-query", "-W", "-f=${Status}", pkg);
			pb.redirectErrorStream(true);
			Process p = pb.start();
			String out = readAll(p.getInputStream());
			int code = p.waitFor();
			return code == 0 && out.contains("install ok installed");
		} catch (Exception e) {
			return false;
		}
	}

	private static String readAll(InputStream in) {
		try {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			byte[] chunk = new byte[4096];
			int n;
			while ((n = in.read(chunk)) >= 0) {
				buf.write(chunk, 0, n);
			}
			return buf.toString(StandardCharsets.UTF_8.name());
		} catch (Exception e) {
			return "";
		}
	}

	private static boolean hasSystemMonitor() {
		String[] names = new String[] {
				"gnome-system-monitor", "plasma-systemmonitor", "xfce4-taskmanager",
				"mate-system-monitor", "cinnamon-system-monitor", "deepin-system-monitor",
				"lxqt-task-manager", "qps", "lxtask", "ksysguard", "gnome-usage"
		};
		for (String n : names) {
			if (Tools.which(n) != null) {
				return true;
			}
		}
		return aptInstalled("gnome-system-monitor");
	}
}
