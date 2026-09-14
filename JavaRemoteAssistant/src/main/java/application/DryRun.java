package application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 体验版：只生成「会跑什么」的说明，与 tool/dry_run.py 对齐。
 */
public final class DryRun {
	private DryRun() {
	}

	public static String plan(String action) {
		AppConf conf = AppConf.load();
		String os = Session.logicalOsName();
		String desk = resolveDesktop(os, conf.get("desktop"));
		return plan(os, desk, action, conf);
	}

	public static String plan(String os, String desktop, String action, AppConf conf) {
		desktop = resolveDesktop(os, desktop);
		String role = conf.get("role");
		if (role.isEmpty()) {
			role = "assisted";
		}
		String host = conf.host().isEmpty() ? "127.0.0.1" : conf.host();
		String port = conf.port().isEmpty() ? "8080" : conf.port();
		Path root = PnatPaths.root();
		Path res = PnatPaths.resDir();
		Path rt = PnatPaths.runtimeDir();
		List<String> lines = new ArrayList<String>();
		lines.add("【体验版】仅控制台记录，主界面已模拟状态");
		lines.add("平台=" + os + "  引擎=" + desktop + "  动作=" + action);
		lines.add("conf: role=" + role + " host=" + host + " port=" + port
				+ " monitor=" + conf.get("monitor") + " auto_accept=" + conf.get("auto_accept")
				+ " tightvnc_mode=" + conf.get("tightvnc_mode") + " client_ip=" + conf.get("client_ip"));
		lines.add("PNAT_ROOT=" + root);

		if ("dayon".equals(desktop)) {
			Path script = root == null ? null : root.resolve("deploy").resolve("dayon").resolve("install.py");
			Path archive;
			if ("linux".equals(os)) {
				archive = res == null ? null : res.resolve("linux").resolve("Dayon").resolve("dayon.sh");
			} else {
				String exe = "assisted".equals(role) ? "assisted.exe" : "assistant.exe";
				archive = res == null ? null : res.resolve("windows").resolve("Dayon").resolve(exe);
			}
			lines.add("脚本: " + note(script));
			lines.add("资源: " + note(archive));
			lines.add("运行时: " + (rt == null ? "?" : rt.resolve("dayon")));
			if ("load".equals(action)) {
				String cmd = "python3 deploy/dayon/install.py";
				if ("assistant".equals(role)) {
					cmd += " assistant";
				}
				if ("windows".equals(os)) {
					cmd = "（Windows Dayon 真部署未接）" + note(archive);
				}
				lines.add("将执行: " + cmd);
				lines.add("效果: " + Tools.roleLabel(role) + " " + host + ":" + port
						+ " auto_accept=" + conf.get("auto_accept"));
			} else if ("stop".equals(action)) {
				lines.add("将执行: python3 deploy/dayon/install.py --stop");
			} else if ("advanced".equals(action)) {
				lines.add("Dayon 无系统服务档；高级部署会提示改用加载/停止");
				lines.add("体验预演: --keep / --uninstall / 改 app.conf");
			} else {
				lines.add("动作: " + action);
			}
		} else if ("tightvnc".equals(desktop)) {
			if (!"windows".equals(os)) {
				lines.add("注意: TightVNC 仅 Windows；当前在模拟 Win 行为");
			}
			Path exe = res == null ? null
					: res.resolve("windows").resolve("TightVNC").resolve("RAServer").resolve("start_server.exe");
			Path bat = root == null ? null : root.resolve("deploy").resolve("tightvnc").resolve("install.bat");
			Path tvn = root == null ? null : root.resolve("conf").resolve("tightvnc.conf");
			String mode = conf.get("tightvnc_mode");
			if (mode == null || mode.isEmpty()) {
				mode = "portable";
			}
			lines.add("部署方式 tightvnc_mode=" + mode);
			lines.add("便携端: " + note(exe));
			lines.add("高级脚本: " + note(bat));
			lines.add("高级配置: " + note(tvn));
			lines.add("选屏 monitor=" + conf.get("monitor") + "（多显示器选屏，无虚拟二屏）");
			if ("load".equals(action)) {
				lines.add("将执行: start_server -reinstall/-start");
				lines.add("  start_server -controlservice -connect " + host + ":" + port);
			} else if ("stop".equals(action)) {
				lines.add("将执行: start_server -stop/-remove ; kill_server.bat");
			} else if ("advanced".equals(action)) {
				lines.add("将执行: deploy/tightvnc/install.bat（服务/密码/托盘，读 tightvnc.conf）");
				if (!"service".equalsIgnoreCase(mode)) {
					lines.add("提示: 当前 tightvnc_mode=" + mode + "；建议设为 service 再高级部署");
				}
			}
		} else if ("vnc".equals(desktop)) {
			int dpy = Tools.parseVncDisplay(conf.get("vnc_display"));
			lines.add("Linux VNC 显示: :" + dpy + (dpy <= 0 ? "（当前屏幕）" : "（虚拟桌面）"));
			String conn = conf.get("vnc_connect").trim();
			boolean reverse = conn.isEmpty() || "reverse".equalsIgnoreCase(conn);
			lines.add("实现: " + conf.get("vnc_impl") + "  连接: " + (reverse ? "反向" : "等待")
					+ "  加密: " + ("1".equals(conf.get("vnc_encrypt")) ? "TLS" : "否"));
			if (dpy > 0) {
				lines.add("状态: 本轮只做 :0，:" + dpy + " 会拒绝加载");
			} else {
				lines.add("将执行: python3 deploy/vnc/install.py");
				if (reverse) {
					lines.add("  反向连出 " + host + ":" + port);
				} else {
					lines.add("  等待连入 本机:5900");
				}
			}
		} else if ("rdp".equals(desktop)) {
			if ("linux".equals(os)) {
				lines.add("状态: Linux 已去掉 RDP，请改选 Dayon 或 VNC");
			} else {
				lines.add("状态: RDP 规划中（加载会被拒绝）");
				lines.add("将执行:（预告）启用远程桌面 / mstsc");
			}
		} else {
			lines.add("未知引擎: " + desktop);
		}
		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			sb.append(line).append('\n');
		}
		return sb.toString();
	}

	public static String resolveDesktop(String os, String raw) {
		String d = raw == null ? "auto" : raw.trim().toLowerCase();
		if (d.isEmpty() || "auto".equals(d)) {
			return "windows".equals(os) ? "tightvnc" : "dayon";
		}
		if ("vnc".equals(d)) {
			return "windows".equals(os) ? "tightvnc" : "vnc";
		}
		return d;
	}

	private static String note(Path p) {
		if (p == null) {
			return "(无路径)";
		}
		return p + " [" + (Files.exists(p) ? "存在" : "缺失") + "]";
	}
}
