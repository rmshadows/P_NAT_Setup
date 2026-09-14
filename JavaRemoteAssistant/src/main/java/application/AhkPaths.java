package application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * AHK 资源走包根共用 res/（与 TightVNC/Dayon 同一套）。
 * 默认：res/windows/AHK/（*.ahk + AHK.exe）；ahk_dir / ahk_exe 可覆盖。
 */
public final class AhkPaths {
	private AhkPaths() {
	}

	/** 共用资源目录：res/windows/AHK */
	public static Path sharedDir() {
		Path res = PnatPaths.resDir();
		if (res == null) {
			return null;
		}
		return res.resolve("windows").resolve("AHK");
	}

	public static Path scriptDir() {
		AppConf c = AppConf.load();
		String conf = c.get("ahk_dir").trim();
		if (!conf.isEmpty()) {
			Path p = Paths.get(conf).toAbsolutePath().normalize();
			if (Files.isDirectory(p)) {
				return p;
			}
		}
		for (Path cand : defaultScriptCandidates()) {
			if (cand != null && Files.isDirectory(cand)) {
				return cand;
			}
		}
		Path shared = sharedDir();
		return shared != null ? shared : Paths.get("res", "windows", "AHK").toAbsolutePath().normalize();
	}

	public static Path exe() {
		AppConf c = AppConf.load();
		String conf = c.get("ahk_exe").trim();
		if (!conf.isEmpty()) {
			Path p = Paths.get(conf).toAbsolutePath().normalize();
			if (Files.isRegularFile(p)) {
				return p;
			}
		}
		Path dir = scriptDir();
		List<Path> cands = new ArrayList<Path>();
		if (dir != null) {
			cands.add(dir.resolve("AHK.exe"));
		}
		Path shared = sharedDir();
		if (shared != null) {
			cands.add(shared.resolve("AHK.exe"));
		}
		for (Path p : cands) {
			if (p != null && Files.isRegularFile(p)) {
				return p;
			}
		}
		return shared != null ? shared.resolve("AHK.exe") : Paths.get("AHK.exe");
	}

	public static void saveScriptDir(Path dir) throws java.io.IOException {
		if (dir == null) {
			return;
		}
		AppConf c = AppConf.load();
		c.set("ahk_dir", dir.toAbsolutePath().normalize().toString());
		c.save();
	}

	public static Path scriptFile(String nameOrFile) {
		String n = nameOrFile == null ? "" : nameOrFile.trim();
		if (n.isEmpty()) {
			n = "sample";
		}
		if (n.toLowerCase().endsWith(".ahk")) {
			Path asIs = Paths.get(n);
			if (asIs.isAbsolute() && Files.isRegularFile(asIs)) {
				return asIs;
			}
		} else {
			n = n + ".ahk";
		}
		return scriptDir().resolve(n).toAbsolutePath().normalize();
	}

	public static List<String> listScriptNames() {
		List<String> out = new ArrayList<String>();
		Path dir = scriptDir();
		if (dir == null || !Files.isDirectory(dir)) {
			return out;
		}
		try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.ahk")) {
			for (Path p : ds) {
				String name = p.getFileName().toString();
				if (name.toLowerCase().endsWith(".ahk")) {
					out.add(name.substring(0, name.length() - 4));
				}
			}
		} catch (Exception ignored) {
		}
		java.util.Collections.sort(out);
		return out;
	}

	/** 相对包根的短路径，方便界面显示。 */
	public static String displayDir() {
		Path dir = scriptDir();
		Path root = PnatPaths.root();
		if (root != null) {
			try {
				Path rel = root.relativize(dir.toAbsolutePath().normalize());
				if (!rel.toString().startsWith("..")) {
					return rel.toString().replace('\\', '/');
				}
			} catch (Exception ignored) {
			}
		}
		return dir.toAbsolutePath().normalize().toString();
	}

	private static List<Path> defaultScriptCandidates() {
		List<Path> list = new ArrayList<Path>();
		Path shared = sharedDir();
		if (shared != null) {
			list.add(shared);
		}
		Path root = PnatPaths.root();
		if (root != null) {
			list.add(root.resolve("res").resolve("windows").resolve("AHK"));
		}
		list.add(Paths.get("res", "windows", "AHK").toAbsolutePath().normalize());
		return list;
	}
}
