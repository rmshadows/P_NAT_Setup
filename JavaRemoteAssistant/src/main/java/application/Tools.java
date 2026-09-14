package application;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * GUI 找共用 res/、调 deploy 脚本。不把安装包再拷进 JRA。
 */
public final class Tools {
	private Tools() {
	}

	public static boolean isWindows() {
		return File.separator.equals("\\");
	}

	public static String env(String name) {
		String v = System.getenv(name);
		return v == null ? "" : v.trim();
	}

	private static String cachedPython;

	public static final String SHELL_SOURCE_AUTO = "auto";

	/** 下拉一项：value=auto 或绝对路径。 */
	public static final class SourceChoice {
		public final String value;
		public final String label;

		public SourceChoice(String value, String label) {
			this.value = value;
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** 比较 conf 里的 auto / ~/路径 / 绝对路径是否指向同一项。 */
	public static boolean sameShellSource(String a, String b) {
		if (a == null || b == null) {
			return false;
		}
		String x = a.trim();
		String y = b.trim();
		if (x.equalsIgnoreCase(y)) {
			return true;
		}
		if (SHELL_SOURCE_AUTO.equalsIgnoreCase(x) || SHELL_SOURCE_AUTO.equalsIgnoreCase(y)) {
			return false;
		}
		return expandUser(x).toAbsolutePath().normalize().equals(expandUser(y).toAbsolutePath().normalize());
	}

	/** 保存设置后清掉，下次重新探测。 */
	public static void invalidatePythonCache() {
		cachedPython = null;
	}

	/** 仅 Linux：source 用户选的文件再找 python3。Windows 忽略。 */
	public static boolean useShellEnv() {
		if (isWindows()) {
			return false;
		}
		return "1".equals(AppConf.load().get("use_shell_env").trim());
	}

	public static String shellSourceConf() {
		String v = AppConf.load().get("shell_source").trim();
		return v.isEmpty() ? SHELL_SOURCE_AUTO : v;
	}

	/** 实际会 source 的文件；auto 时为推荐项。找不到返回 null。 */
	public static Path resolvedShellSource() {
		String conf = shellSourceConf();
		if (!SHELL_SOURCE_AUTO.equalsIgnoreCase(conf)) {
			Path p = expandUser(conf);
			return Files.isRegularFile(p) ? p.toAbsolutePath().normalize() : null;
		}
		return recommendedShellSource();
	}

	public static Path recommendedShellSource() {
		Path home = userHome();
		String sh = shellName();
		List<Path> rc = new ArrayList<Path>();
		if ("zsh".equals(sh)) {
			rc.add(home.resolve(".zshrc"));
			rc.add(home.resolve(".zprofile"));
			rc.add(home.resolve(".zshenv"));
		} else if ("bash".equals(sh)) {
			rc.add(home.resolve(".bashrc"));
			rc.add(home.resolve(".bash_profile"));
			rc.add(home.resolve(".profile"));
		} else if ("fish".equals(sh)) {
			rc.add(home.resolve(".config").resolve("fish").resolve("config.fish"));
		} else {
			rc.add(home.resolve(".bashrc"));
			rc.add(home.resolve(".zshrc"));
			rc.add(home.resolve(".profile"));
		}
		Path bestRc = null;
		for (Path p : rc) {
			if (!Files.isRegularFile(p)) {
				continue;
			}
			if (fileLooksLikeVenvHook(p)) {
				return p;
			}
			if (bestRc == null) {
				bestRc = p;
			}
		}
		Path act = firstExistingActivate();
		if (act != null) {
			return act;
		}
		if (bestRc != null) {
			return bestRc;
		}
		Path profile = home.resolve(".profile");
		return Files.isRegularFile(profile) ? profile : null;
	}

	/** 磁盘上找得到、可供下拉选择的 source 文件。 */
	public static List<Path> shellSourceCandidates() {
		LinkedHashSet<Path> out = new LinkedHashSet<Path>();
		Path home = userHome();
		Path[] rc = new Path[] {
				home.resolve(".zshrc"), home.resolve(".zprofile"), home.resolve(".zshenv"), home.resolve(".zlogin"),
				home.resolve(".bashrc"), home.resolve(".bash_profile"), home.resolve(".bash_login"),
				home.resolve(".profile"),
				home.resolve(".config").resolve("fish").resolve("config.fish")
		};
		for (Path p : rc) {
			if (Files.isRegularFile(p)) {
				out.add(p.toAbsolutePath().normalize());
			}
		}
		for (Path p : activateCandidates()) {
			if (Files.isRegularFile(p)) {
				out.add(p.toAbsolutePath().normalize());
			}
		}
		Path cur = resolvedShellSource();
		if (cur != null) {
			out.add(cur);
		}
		return new ArrayList<Path>(out);
	}

	public static List<SourceChoice> shellSourceChoices() {
		List<SourceChoice> list = new ArrayList<SourceChoice>();
		Path rec = recommendedShellSource();
		String recLabel = rec == null ? "未找到常见文件" : tildeHome(rec);
		list.add(new SourceChoice(SHELL_SOURCE_AUTO, "自动（推荐 " + recLabel + "）"));
		for (Path p : shellSourceCandidates()) {
			String extra = rec != null && p.equals(rec.toAbsolutePath().normalize()) ? "  〔推荐〕" : "";
			list.add(new SourceChoice(p.toString(), tildeHome(p) + extra));
		}
		return list;
	}

	public static String tildeHome(Path p) {
		if (p == null) {
			return "";
		}
		String abs = p.toAbsolutePath().normalize().toString();
		String home = userHome().toString();
		if (abs.equals(home)) {
			return "~";
		}
		if (abs.startsWith(home + "/")) {
			return "~" + abs.substring(home.length());
		}
		return abs;
	}

	private static Path userHome() {
		String h = System.getProperty("user.home");
		if (h == null || h.isEmpty()) {
			h = env("HOME");
		}
		return Paths.get(h == null || h.isEmpty() ? "." : h).toAbsolutePath().normalize();
	}

	private static String shellName() {
		String s = env("SHELL");
		if (s.isEmpty()) {
			return "bash";
		}
		s = s.replace('\\', '/');
		int slash = s.lastIndexOf('/');
		return slash >= 0 ? s.substring(slash + 1).toLowerCase() : s.toLowerCase();
	}

	private static Path expandUser(String raw) {
		String t = raw == null ? "" : raw.trim();
		if (t.startsWith("~/")) {
			return userHome().resolve(t.substring(2));
		}
		if ("~".equals(t)) {
			return userHome();
		}
		return Paths.get(t);
	}

	public static Path expandShellSource(String raw) {
		return expandUser(raw);
	}

	private static List<Path> activateCandidates() {
		List<Path> out = new ArrayList<Path>();
		String virt = env("VIRTUAL_ENV");
		if (!virt.isEmpty()) {
			out.add(Paths.get(virt, "bin", "activate"));
		}
		Path home = userHome();
		String[] names = new String[] { ".PythonVenv", "PythonVenv", ".venv", "venv", ".virtualenv", "env" };
		for (String n : names) {
			out.add(home.resolve(n).resolve("bin").resolve("activate"));
		}
		return out;
	}

	private static Path firstExistingActivate() {
		for (Path p : activateCandidates()) {
			if (Files.isRegularFile(p)) {
				return p.toAbsolutePath().normalize();
			}
		}
		return null;
	}

	private static boolean fileLooksLikeVenvHook(Path p) {
		try {
			byte[] buf = Files.readAllBytes(p);
			int n = Math.min(buf.length, 8000);
			String s = new String(buf, 0, n, StandardCharsets.UTF_8).toLowerCase();
			return s.contains("activate") || s.contains("virtual_env") || s.contains("venv")
					|| s.contains("virtualenv") || s.contains("pythonvenv");
		} catch (Exception e) {
			return false;
		}
	}

	/** JRA 调用脚本用的 python3 路径；找不到则返回 "python3"。 */
	public static String python3() {
		String p = python3OrNull();
		return p == null ? "python3" : p;
	}

	public static String python3OrNull() {
		if (cachedPython != null) {
			return cachedPython.isEmpty() ? null : cachedPython;
		}
		String found = null;
		if (useShellEnv()) {
			found = resolveShellPython();
		}
		if (found == null) {
			found = which("python3");
		}
		if (found == null) {
			Path sys = Paths.get("/usr/bin/python3");
			if (Files.isExecutable(sys)) {
				found = sys.toString();
			}
		}
		cachedPython = found == null ? "" : found;
		return found;
	}

	private static String resolveShellPython() {
		Path src = resolvedShellSource();
		if (src != null) {
			String fromFile = pythonAfterSource(src);
			if (fromFile != null) {
				return fromFile;
			}
		}
		String shell = env("SHELL");
		if (shell.isEmpty()) {
			shell = "/bin/bash";
		}
		String ic = shellCommandPath(shell, "-ic");
		String lc = shellCommandPath(shell, "-lc");
		if (looksLikeVenvPython(ic)) {
			return ic;
		}
		if (looksLikeVenvPython(lc)) {
			return lc;
		}
		if (ic != null) {
			return ic;
		}
		return lc;
	}

	private static String pythonAfterSource(Path file) {
		String path = file.toAbsolutePath().normalize().toString();
		String q = path.replace("'", "'\\''");
		String name = file.getFileName() == null ? "" : file.getFileName().toString();
		String lower = path.toLowerCase();
		String[] cmd;
		if (lower.endsWith("/activate") || "activate".equals(name)) {
			cmd = new String[] { "/bin/bash", "-c", ". '" + q + "' && command -v python3" };
		} else if (lower.contains("fish") || name.endsWith(".fish")) {
			String fish = which("fish");
			if (fish == null) {
				fish = "/usr/bin/fish";
			}
			cmd = new String[] { fish, "-c", "source '" + q + "'; command -v python3" };
		} else if (name.startsWith(".zsh") || name.contains("zshrc") || name.contains("zprofile")
				|| name.contains("zshenv") || name.contains("zlogin") || lower.contains("/.zsh")) {
			String zsh = which("zsh");
			if (zsh == null) {
				zsh = env("SHELL");
				if (!zsh.contains("zsh")) {
					zsh = "/usr/bin/zsh";
				}
			}
			cmd = new String[] { zsh, "-c", "source '" + q + "' && command -v python3" };
		} else {
			cmd = new String[] { "/bin/bash", "-c", ". '" + q + "' && command -v python3" };
		}
		return runAndLastPythonPath(cmd);
	}

	private static String runAndLastPythonPath(String[] cmd) {
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectError(ProcessBuilder.Redirect.DISCARD);
			File devNull = new File("/dev/null");
			if (devNull.exists()) {
				pb.redirectInput(ProcessBuilder.Redirect.from(devNull));
			}
			Process p = pb.start();
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
			StringBuilder out = new StringBuilder();
			String line;
			while ((line = r.readLine()) != null) {
				if (out.length() > 0) {
					out.append('\n');
				}
				out.append(line);
			}
			boolean done = p.waitFor(8, TimeUnit.SECONDS);
			if (!done) {
				p.destroyForcibly();
				return null;
			}
			if (p.exitValue() != 0) {
				return null;
			}
			String[] lines = out.toString().split("\n");
			for (int i = lines.length - 1; i >= 0; i--) {
				String s = lines[i].trim();
				if (s.startsWith("/") && s.contains("python")) {
					return s;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static boolean looksLikeVenvPython(String path) {
		if (path == null || path.isEmpty()) {
			return false;
		}
		String p = path.toLowerCase();
		if (p.contains("/venv/") || p.contains("/.venv/") || p.contains("virtualenv")
				|| p.contains("pythonvenv") || p.contains(".pythonvenv")) {
			return true;
		}
		try {
			Path bin = Paths.get(path).getParent();
			if (bin == null) {
				return false;
			}
			if (Files.isRegularFile(bin.resolve("pyvenv.cfg"))) {
				return true;
			}
			return bin.getParent() != null && Files.isRegularFile(bin.getParent().resolve("pyvenv.cfg"));
		} catch (Exception e) {
			return false;
		}
	}

	/** 用 SHELL -lc / -ic source 后 command -v python3。 */
	private static String shellCommandPath(String shell, String flag) {
		try {
			ProcessBuilder pb = new ProcessBuilder(shell, flag, "command -v python3");
			pb.redirectError(ProcessBuilder.Redirect.DISCARD);
			File devNull = new File("/dev/null");
			if (devNull.exists()) {
				pb.redirectInput(ProcessBuilder.Redirect.from(devNull));
			}
			Process p = pb.start();
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
			StringBuilder out = new StringBuilder();
			String line;
			while ((line = r.readLine()) != null) {
				if (out.length() > 0) {
					out.append('\n');
				}
				out.append(line);
			}
			boolean done = p.waitFor(8, TimeUnit.SECONDS);
			if (!done) {
				p.destroyForcibly();
				return null;
			}
			if (p.exitValue() != 0) {
				return null;
			}
			String[] lines = out.toString().split("\n");
			for (int i = lines.length - 1; i >= 0; i--) {
				String s = lines[i].trim();
				if (s.startsWith("/") && s.contains("python")) {
					return s;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	/** Wayland 会话（xdotool/wmctrl 对真 Wayland 工作区无效，不能当主路径）。 */
	public static boolean isWayland() {
		if (isWindows()) {
			return false;
		}
		String t = env("XDG_SESSION_TYPE").toLowerCase();
		if (t.contains("wayland")) {
			return true;
		}
		return !env("WAYLAND_DISPLAY").isEmpty() && !t.contains("x11");
	}

	public static boolean isX11Session() {
		if (isWindows()) {
			return false;
		}
		if (isWayland()) {
			return false;
		}
		String t = env("XDG_SESSION_TYPE").toLowerCase();
		return t.contains("x11") || !env("DISPLAY").isEmpty();
	}

	/** 仅 X11 上 xdotool/wmctrl 才能改工作区编号。 */
	public static boolean linuxX11DesktopToolsOk() {
		return isX11Session() && (which("xdotool") != null || which("wmctrl") != null);
	}

	public static String linuxDisplayKind() {
		if (isWindows()) {
			return "windows";
		}
		if (isWayland()) {
			return "wayland";
		}
		if (isX11Session()) {
			return "x11";
		}
		return "unknown";
	}

	/** 解析后的桌面引擎：dayon | tightvnc | vnc | rdp（体验版按 simulate_os） */
	public static String resolveDesktop() {
		AppConf c = AppConf.load();
		String d = c.get("desktop").trim().toLowerCase();
		String os = Session.logicalOsName();
		return DryRun.resolveDesktop(os, d);
	}

	/** 下拉菜单 id：dayon | vnc | rdp（auto / tightvnc 归并到对应项）。 */
	public static String desktopMenuId() {
		String d = resolveDesktop();
		if ("rdp".equals(d)) {
			return "rdp";
		}
		if ("vnc".equals(d) || "tightvnc".equals(d)) {
			return "vnc";
		}
		return "dayon";
	}

	/** 主界面旁白用的产品名。 */
	public static String desktopLabel() {
		return desktopLabelFor(desktopMenuId());
	}

	public static String desktopLabelFor(String menuId) {
		if ("rdp".equals(menuId)) {
			return "RDP";
		}
		if ("vnc".equals(menuId)) {
			return "VNC";
		}
		return "Dayon";
	}

	/**
	 * Linux VNC 显示号：0=当前屏(:0)，≥1=虚拟桌面(:1/:2/…)。
	 * conf 可写 0、1、:0、:3（当前屏幕）等。
	 */
	public static int vncDisplay() {
		return parseVncDisplay(AppConf.load().get("vnc_display"));
	}

	public static int parseVncDisplay(String raw) {
		if (raw == null) {
			return 0;
		}
		String v = raw.trim();
		if (v.startsWith(":")) {
			v = v.substring(1).trim();
		}
		int i = 0;
		while (i < v.length() && Character.isDigit(v.charAt(i))) {
			i++;
		}
		if (i == 0) {
			return 0;
		}
		try {
			int n = Integer.parseInt(v.substring(0, i));
			return n < 0 ? 0 : n;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public static String vncDisplayChoice(int n) {
		if (n <= 0) {
			return ":0（当前屏幕）";
		}
		return ":" + n + "（虚拟桌面）";
	}

	public static String vncDisplayTag() {
		return ":" + vncDisplay();
	}

	public static boolean useLinuxVnc() {
		return "vnc".equals(resolveDesktop()) && !"windows".equals(Session.logicalOsName());
	}

	public static String vncImpl() {
		String v = AppConf.load().get("vnc_impl").trim().toLowerCase();
		if ("x11vnc".equals(v) || "tigervnc".equals(v)) {
			return v;
		}
		return "auto";
	}

	public static boolean vncEncrypt() {
		return "1".equals(AppConf.load().get("vnc_encrypt").trim());
	}

	public static String vncImplTag() {
		String v = vncImpl();
		if ("x11vnc".equals(v)) {
			return "x11vnc";
		}
		if ("tigervnc".equals(v)) {
			return "TigerVNC";
		}
		return "自动";
	}

	public static String vncEncryptTag() {
		return vncEncrypt() ? "加密" : "不加密";
	}

	/** reverse=反向连出 Viewer；listen=等待连入 :5900。默认 reverse。 */
	public static boolean vncReverse() {
		String v = AppConf.load().get("vnc_connect").trim().toLowerCase();
		if ("listen".equals(v) || "wait".equals(v) || "in".equals(v)) {
			return false;
		}
		return true;
	}

	public static String vncConnectTag() {
		return vncReverse() ? "反向" : "等待";
	}

	/** 受助端 / 协助端。 */
	public static String roleLabel() {
		return roleLabel(AppConf.load().get("role"));
	}

	public static String roleLabel(String role) {
		if (role != null && "assistant".equalsIgnoreCase(role.trim())) {
			return "协助端";
		}
		return "受助端";
	}

	/** 当前是否走 TightVNC（含 Windows auto / vnc）。 */
	public static boolean useTightVnc() {
		return "tightvnc".equals(resolveDesktop());
	}

	public static boolean useDayon() {
		return "dayon".equals(resolveDesktop());
	}

	public static boolean useRdp() {
		return "rdp".equals(resolveDesktop());
	}

	/** TightVNC：portable | service（默认 portable）。 */
	public static String tightvncMode() {
		String m = AppConf.load().get("tightvnc_mode").trim().toLowerCase();
		return "service".equals(m) ? "service" : "portable";
	}

	public static boolean tightvncServiceMode() {
		return useTightVnc() && "service".equals(tightvncMode());
	}

	public static Path tvnDir() {
		Path res = PnatPaths.resDir();
		if (res != null) {
			Path d = res.resolve("windows").resolve("TightVNC").resolve("RAServer");
			if (Files.isDirectory(d)) {
				return d;
			}
		}
		return Paths.get("res", "windows", "TightVNC", "RAServer");
	}

	public static Path tvnExe() {
		return tvnDir().resolve("start_server.exe");
	}

	public static Path tvnKillBat() {
		return tvnDir().resolve("kill_server.bat");
	}

	public static Path tvnMsi() {
		Path res = PnatPaths.resDir();
		if (res == null) {
			return Paths.get("res", "windows", "TightVNC", "tightvnc.msi");
		}
		return res.resolve("windows").resolve("TightVNC").resolve("tightvnc.msi");
	}

	public static Path tvnInstallBat() {
		Path root = PnatPaths.root();
		if (root == null) {
			return Paths.get("deploy", "tightvnc", "install.bat");
		}
		return root.resolve("deploy").resolve("tightvnc").resolve("install.bat");
	}

	public static String tvnCmd(String args) {
		return quote(tvnExe().toAbsolutePath().toString()) + " " + args;
	}

	public static String tvnKillCmd() {
		return quote(tvnKillBat().toAbsolutePath().toString());
	}

	public static Path dayonScript() {
		Path root = PnatPaths.root();
		if (root == null) {
			return Paths.get("deploy", "dayon", "install.py");
		}
		return root.resolve("deploy").resolve("dayon").resolve("install.py");
	}

	public static Path dayonArchive() {
		Path res = PnatPaths.resDir();
		if (res == null) {
			return Paths.get("res", "linux", "Dayon", "dayon.sh");
		}
		return res.resolve("linux").resolve("Dayon").resolve("dayon.sh");
	}

	public static Path vncScript() {
		Path root = PnatPaths.root();
		if (root == null) {
			return Paths.get("deploy", "vnc", "install.py");
		}
		return root.resolve("deploy").resolve("vnc").resolve("install.py");
	}

	public static Path gsudoExe() {
		Path res = PnatPaths.resDir();
		if (res == null) {
			return null;
		}
		Path p = res.resolve("windows").resolve("helper").resolve("gsudo").resolve("gsudo.exe");
		return Files.isRegularFile(p) ? p : null;
	}

	/** 普通部署预检：缺文件直接失败，避免半吊子执行。 */
	public static DeployResult preflightSimple() {
		String desk = resolveDesktop();
		if ("rdp".equals(desk)) {
			if (!"windows".equals(Session.logicalOsName())) {
				return DeployResult.fail("Linux 不提供 RDP。请改选 Dayon 或 VNC。");
			}
			return DeployResult.fail("RDP 规划中。请用加载按钮右侧箭头改选 Dayon 或 VNC，或右击打开高级设置。");
		}
		if ("vnc".equals(desk)) {
			if (isWindows() || "windows".equals(Session.logicalOsName())) {
				return DeployResult.fail("Linux VNC 仅 Linux。Windows 请用 TightVNC（下拉里的 VNC）。");
			}
			if (vncDisplay() > 0) {
				return DeployResult.fail("本轮 Linux VNC 先做当前屏（:0）。虚拟桌面 " + vncDisplayTag()
						+ " 稍后接 TigerVNC vncserver。请在高级设置改回 :0。");
			}
			if (isWayland()) {
				return DeployResult.fail("当前是 Wayland，x11vnc 不能可靠共享整屏。请用 Dayon，或登录 X11 会话。");
			}
			Path root = PnatPaths.root();
			if (root == null) {
				return DeployResult.fail("找不到包根。请从仓库目录启动，或设置 PNAT_ROOT。");
			}
			if (!Files.isRegularFile(vncScript())) {
				return DeployResult.fail("找不到 VNC 脚本：\n" + vncScript());
			}
			if (python3OrNull() == null) {
				return DeployResult.fail("找不到 python3，无法调用 deploy/vnc。");
			}
			if (env("DISPLAY").isEmpty()) {
				return DeployResult.fail("未检测到 DISPLAY，VNC 需要图形界面。");
			}
			String impl = vncImpl();
			boolean hasX11 = which("x11vnc") != null || Files.isExecutable(Paths.get("/usr/bin/x11vnc"));
			boolean hasTiger = which("x0vncserver") != null || Files.isExecutable(Paths.get("/usr/bin/x0vncserver"));
			if (vncReverse()) {
				String host = AppConf.load().host().trim();
				if (host.isEmpty()) {
					return DeployResult.fail("已选反向连出，请在 VNC 高级设置填写协助端 Viewer 地址。");
				}
				if ("tigervnc".equals(impl)) {
					return DeployResult.fail("TigerVNC 的 :0（x0vncserver）不能反向连出。\n请改选 x11vnc，或把连接方式改成「等待连入」。");
				}
				if (!hasX11) {
					return DeployResult.fail("反向连出需要 x11vnc。在「Linux 依赖」勾选，或：\nsudo apt install x11vnc");
				}
			} else {
				if ("x11vnc".equals(impl) && !hasX11) {
					return DeployResult.fail("未找到 x11vnc。在「Linux 依赖」勾选安装，或：\nsudo apt install x11vnc");
				}
				if ("tigervnc".equals(impl) && !hasTiger) {
					return DeployResult.fail("未找到 x0vncserver。安装：\nsudo apt install tigervnc-scraping-server");
				}
				if ("auto".equals(impl) && !hasX11 && !hasTiger) {
					return DeployResult.fail("未找到 VNC 实现。:0 推荐：\nsudo apt install x11vnc\n或在「Linux 依赖」勾选 x11vnc。");
				}
			}
			return DeployResult.ok();
		}
		if ("tightvnc".equals(desk)) {
			if (!isWindows() && !Session.dryRun()) {
				return DeployResult.fail("TightVNC 仅 Windows。请用加载按钮右侧箭头改选 Dayon。");
			}
			if (!Files.isRegularFile(tvnExe())) {
				return DeployResult.fail("找不到 TightVNC 便携端：\n" + tvnExe()
						+ "\n请确认包根下有 res/windows/TightVNC/RAServer，或设置 PNAT_RES。");
			}
			return DeployResult.ok();
		}
		if ("dayon".equals(desk)) {
			if (isWindows() && !Session.dryRun()) {
				return DeployResult.fail("Windows 上 Dayon 真部署尚未接入。请改选 VNC，或开体验版预演。");
			}
			Path root = PnatPaths.root();
			if (root == null) {
				return DeployResult.fail("找不到包根（需要 conf/ 和 deploy/ 或 res/）。请从仓库目录启动，或设置 PNAT_ROOT。");
			}
			if (!Files.isRegularFile(dayonScript())) {
				return DeployResult.fail("找不到 Dayon 脚本：\n" + dayonScript());
			}
			if (!Files.isRegularFile(dayonArchive())) {
				return DeployResult.fail("找不到 Dayon 资源：\n" + dayonArchive());
			}
			if (python3OrNull() == null) {
				return DeployResult.fail("找不到 python3，无法调用 deploy/dayon。");
			}
			if (System.getenv("DISPLAY") == null || System.getenv("DISPLAY").isEmpty()) {
				return DeployResult.fail("未检测到 DISPLAY，Dayon 需要图形界面。");
			}
			return DeployResult.ok();
		}
		return DeployResult.fail("未知远程桌面方式：" + desk + "\n请用加载按钮右侧箭头选择 Dayon / VNC。");
	}

	/** 高级部署预检：走 MSI 脚本（服务 / 密码 / 托盘）。 */
	public static DeployResult preflightAdvanced() {
		String desk = resolveDesktop();
		if ("dayon".equals(desk)) {
			return DeployResult.fail("Dayon 无系统服务档。请用「加载远程桌面 / 停止远程桌面」，或右击加载按钮改高级设置。");
		}
		if ("vnc".equals(desk)) {
			return DeployResult.fail("Linux VNC 无系统服务档。请用「加载远程桌面 / 停止远程桌面」，或右击改高级设置（实现 / 加密 / 密码）。");
		}
		if ("rdp".equals(desk)) {
			if (!"windows".equals(Session.logicalOsName())) {
				return DeployResult.fail("Linux 不提供 RDP。");
			}
			return DeployResult.fail("RDP 规划中，高级部署不可用。");
		}
		if (!"tightvnc".equals(desk)) {
			return DeployResult.fail("高级部署仅 TightVNC。当前方式=" + desk);
		}
		if (!isWindows()) {
			return DeployResult.fail("高级部署（服务/密码/托盘）仅 Windows TightVNC。Linux 用普通部署即可。");
		}
		Path root = PnatPaths.root();
		if (root == null) {
			return DeployResult.fail("找不到包根。请从仓库目录启动，或设置 PNAT_ROOT。");
		}
		if (!Files.isRegularFile(tvnMsi())) {
			return DeployResult.fail("找不到 TightVNC 安装包：\n" + tvnMsi());
		}
		if (!Files.isRegularFile(tvnInstallBat())) {
			return DeployResult.fail("找不到安装脚本：\n" + tvnInstallBat());
		}
		Path conf = root.resolve("conf").resolve("tightvnc.conf");
		if (!Files.isRegularFile(conf)) {
			return DeployResult.fail("找不到 conf/tightvnc.conf（密码、托盘、安装路径）。请右击加载按钮填写并保存。");
		}
		return DeployResult.ok();
	}

	public static DeployResult runDayon(String... args) {
		DeployResult pre = preflightSimple();
		if (!pre.ok) {
			return pre;
		}
		return runProcess(PnatPaths.root(), 1, concat(python3(), dayonScript().toAbsolutePath().toString(), args));
	}

	public static DeployResult runLinuxVnc(String... args) {
		if (args != null && args.length > 0 && "--stop".equals(args[0])) {
			if (!Files.isRegularFile(vncScript()) || python3OrNull() == null) {
				return DeployResult.fail("找不到 VNC 停止脚本。");
			}
			return runProcess(PnatPaths.root(), 0, concat(python3(), vncScript().toAbsolutePath().toString(), args));
		}
		DeployResult pre = preflightSimple();
		if (!pre.ok) {
			return pre;
		}
		return runProcess(PnatPaths.root(), 1, concat(python3(), vncScript().toAbsolutePath().toString(), args));
	}

	/** 高级：调用现有 install.bat（服务、密码、托盘），失败重试 1 次。 */
	public static DeployResult runTightVncAdvanced() {
		DeployResult pre = preflightAdvanced();
		if (!pre.ok) {
			return pre;
		}
		String bat = tvnInstallBat().toAbsolutePath().toString();
		Path gsudo = gsudoExe();
		if (gsudo != null) {
			return runProcess(PnatPaths.root(), 1, gsudo.toAbsolutePath().toString(), bat);
		}
		return runProcess(PnatPaths.root(), 1, "cmd.exe", "/c", bat);
	}

	public static DeployResult runProcess(Path cwd, int extraTries, String... cmd) {
		int tries = 1 + Math.max(0, extraTries);
		DeployResult last = DeployResult.fail("未执行");
		for (int i = 1; i <= tries; i++) {
			last = runProcessOnce(cwd, cmd);
			if (last.ok) {
				return last;
			}
			System.err.println("第 " + i + "/" + tries + " 次失败：" + last.message);
		}
		return DeployResult.fail(last.code, last.message + "\n已重试 " + extraTries + " 次仍失败。");
	}

	private static DeployResult runProcessOnce(Path cwd, String... cmd) {
		ProcessBuilder pb = new ProcessBuilder(cmd);
		if (cwd != null) {
			pb.directory(cwd.toFile());
		}
		pb.redirectErrorStream(true);
		StringBuilder out = new StringBuilder();
		Charset cs = Charset.defaultCharset();
		try {
			Process p = pb.start();
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), cs));
			String line;
			while ((line = r.readLine()) != null) {
				out.append(line).append('\n');
				System.out.println(line);
			}
			int code = p.waitFor();
			if (code == 0) {
				return DeployResult.ok();
			}
			return DeployResult.fail(code, "命令失败（退出码 " + code + "）：" + String.join(" ", cmd) + "\n" + tail(out, 800));
		} catch (Exception e) {
			return DeployResult.fail("无法执行：" + String.join(" ", cmd) + "\n" + e);
		}
	}

	/** 桌面层是否在跑：Linux Dayon / Linux VNC / Windows TightVNC。 */
	public static boolean isDesktopRunning() {
		if (useTightVnc()) {
			return windowsHasImage("start_server.exe") || windowsHasImage("tvnserver.exe");
		}
		Path rt = PnatPaths.runtimeDir();
		if (useLinuxVnc()) {
			final String mark = rt == null ? null : rt.resolve("vnc").toAbsolutePath().normalize().toString();
			return linuxHasVnc(mark);
		}
		final String mark = rt == null ? null : rt.resolve("dayon").toAbsolutePath().normalize().toString();
		return linuxHasDayon(mark);
	}

	private static boolean windowsHasImage(String exe) {
		try {
			Process p = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq " + exe, "/NH").redirectErrorStream(true)
					.start();
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.defaultCharset()));
			String line;
			while ((line = r.readLine()) != null) {
				if (line.toLowerCase().indexOf(exe.toLowerCase()) >= 0) {
					p.destroy();
					return true;
				}
			}
			p.waitFor();
		} catch (Exception ignored) {
		}
		return false;
	}

	private static boolean linuxHasVnc(String mark) {
		try {
			Process p = new ProcessBuilder("ps", "-eo", "args=").redirectErrorStream(true).start();
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.defaultCharset()));
			String line;
			while ((line = r.readLine()) != null) {
				boolean bin = line.indexOf("x11vnc") >= 0 || line.indexOf("x0vncserver") >= 0;
				if (!bin) {
					continue;
				}
				if (mark == null || line.indexOf(mark) >= 0) {
					p.destroy();
					return true;
				}
			}
			p.waitFor();
		} catch (Exception ignored) {
		}
		return false;
	}

	private static boolean linuxHasDayon(String mark) {
		try {
			Process p = new ProcessBuilder("ps", "-eo", "args=").redirectErrorStream(true).start();
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.defaultCharset()));
			String line;
			while ((line = r.readLine()) != null) {
				if (line.indexOf("dayon.jar") < 0) {
					continue;
				}
				if (mark == null || line.indexOf(mark) >= 0) {
					p.destroy();
					return true;
				}
			}
			p.waitFor();
		} catch (Exception ignored) {
		}
		return false;
	}

	public static String which(String name) {
		String path = System.getenv("PATH");
		if (path == null) {
			return null;
		}
		for (String dir : path.split(File.pathSeparator)) {
			Path p = Paths.get(dir, name);
			if (Files.isRegularFile(p) && Files.isExecutable(p)) {
				return p.toString();
			}
			if (isWindows()) {
				Path exe = Paths.get(dir, name + ".exe");
				if (Files.isRegularFile(exe)) {
					return exe.toString();
				}
			}
		}
		return null;
	}

	private static String[] concat(String a, String b, String[] rest) {
		ArrayList<String> all = new ArrayList<String>();
		all.add(a);
		all.add(b);
		if (rest != null) {
			all.addAll(Arrays.asList(rest));
		}
		return all.toArray(new String[0]);
	}

	private static String tail(StringBuilder sb, int max) {
		if (sb.length() <= max) {
			return sb.toString();
		}
		return sb.substring(sb.length() - max);
	}

	private static String quote(String p) {
		if (p.indexOf(' ') < 0) {
			return p;
		}
		return "\"" + p + "\"";
	}
}
