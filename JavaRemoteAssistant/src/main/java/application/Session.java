package application;

/**
 * 运行会话：正式 / 体验(dry-run)，以及体验时模拟的目标平台。
 */
public final class Session {
	private Session() {
	}

	public static boolean dryRun() {
		String env = System.getenv("PNAT_DRY_RUN");
		if (env != null && !env.isEmpty()) {
			return !"0".equals(env);
		}
		return "1".equals(AppConf.load().get("dry_run"));
	}

	/** auto | windows | linux —— 体验版模拟哪边的桌面引擎与按钮可见性。 */
	public static String simulateOs() {
		String s = AppConf.load().get("simulate_os").trim().toLowerCase();
		if (s.isEmpty()) {
			s = "auto";
		}
		return s;
	}

	/** 解析引擎 / 显隐 AHK 时用的「逻辑 Windows」。 */
	public static boolean logicalWindows() {
		if (!dryRun()) {
			return Tools.isWindows();
		}
		String s = simulateOs();
		if ("windows".equals(s)) {
			return true;
		}
		if ("linux".equals(s)) {
			return false;
		}
		return Tools.isWindows();
	}

	public static String logicalOsName() {
		return logicalWindows() ? "windows" : "linux";
	}

	public static String modeLabel() {
		if (!dryRun()) {
			return "正式";
		}
		return "体验·" + logicalOsName();
	}
}
