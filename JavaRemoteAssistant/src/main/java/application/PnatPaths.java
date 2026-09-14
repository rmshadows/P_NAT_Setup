package application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 与 Python lib_paths 同一套：包根 / conf / res / runtime。
 * GUI 与 CLI 共用一份，导出时只带仓库里的 conf/ 和 res/。
 */
public final class PnatPaths {
	private PnatPaths() {
	}

	public static Path root() {
		String env = System.getenv("PNAT_ROOT");
		if (env != null && !env.isEmpty()) {
			Path p = Paths.get(env).toAbsolutePath().normalize();
			if (isRoot(p)) {
				return p;
			}
		}
		Path[] seeds = new Path[] { Paths.get("").toAbsolutePath(), jarDir(), Paths.get(System.getProperty("user.dir")) };
		for (Path seed : seeds) {
			if (seed == null) {
				continue;
			}
			Path cur = seed.toAbsolutePath().normalize();
			for (int i = 0; i < 8; i++) {
				if (isRoot(cur)) {
					return cur;
				}
				Path parent = cur.getParent();
				if (parent == null || parent.equals(cur)) {
					break;
				}
				cur = parent;
			}
		}
		return null;
	}

	public static Path confDir() {
		String env = System.getenv("PNAT_CONF");
		if (env != null && !env.isEmpty()) {
			return Paths.get(env).toAbsolutePath().normalize();
		}
		Path r = root();
		return r == null ? null : r.resolve("conf");
	}

	public static Path resDir() {
		String env = System.getenv("PNAT_RES");
		if (env != null && !env.isEmpty()) {
			return Paths.get(env).toAbsolutePath().normalize();
		}
		Path r = root();
		return r == null ? null : r.resolve("res");
	}

	public static Path runtimeDir() {
		String env = System.getenv("PNAT_RUNTIME");
		if (env != null && !env.isEmpty()) {
			return Paths.get(env).toAbsolutePath().normalize();
		}
		Path r = root();
		return r == null ? null : r.resolve(".runtime");
	}

	public static Path appConfFile() {
		Path c = confDir();
		return c == null ? null : c.resolve("app.conf");
	}

	private static boolean isRoot(Path path) {
		if (path == null || !Files.isDirectory(path)) {
			return false;
		}
		boolean hasConf = Files.isDirectory(path.resolve("conf"));
		boolean hasDeploy = Files.isDirectory(path.resolve("deploy"));
		boolean hasRes = Files.isDirectory(path.resolve("res"));
		return hasConf && (hasDeploy || hasRes);
	}

	private static Path jarDir() {
		try {
			Path p = Paths.get(PnatPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (Files.isRegularFile(p)) {
				p = p.getParent();
			}
			return p;
		} catch (Exception e) {
			return null;
		}
	}
}
