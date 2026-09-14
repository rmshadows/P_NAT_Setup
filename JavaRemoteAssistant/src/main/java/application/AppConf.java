package application;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读/写仓库 conf/app.conf（与 deploy 脚本同一份）。
 */
public final class AppConf {
	private final Map<String, String> kv = new LinkedHashMap<String, String>();
	private Path file;

	public static AppConf load() {
		AppConf c = new AppConf();
		c.kv.put("role", "assisted");
		c.kv.put("host", "127.0.0.1");
		c.kv.put("port", "8080");
		c.kv.put("auto_accept", "1");
		c.kv.put("desktop", "auto");
		c.kv.put("tightvnc_mode", "portable");
		c.kv.put("vnc_display", "0");
		c.kv.put("vnc_impl", "auto");
		c.kv.put("vnc_connect", "reverse");
		c.kv.put("vnc_encrypt", "0");
		c.kv.put("vnc_passwd", "");
		c.kv.put("monitor", "all");
		c.kv.put("network", "none");
		c.kv.put("always_on_top", "1");
		c.kv.put("dry_run", "0");
		c.kv.put("simulate_os", "auto");
		c.kv.put("use_shell_env", "0");
		c.kv.put("shell_source", "auto");
		c.kv.put("tray", "1");
		c.kv.put("close_to_tray", "1");
		c.kv.put("client_ip", "");
		c.kv.put("delay_system_monitor", "0");
		c.kv.put("delay_show_desktop", "0");
		c.kv.put("delay_desktops", "0");
		c.kv.put("delay_ime", "2");
		c.kv.put("delay_keyboard", "0");
		c.kv.put("delay_ahk", "5");
		c.kv.put("ahk_dir", "");
		c.kv.put("ahk_exe", "");
		c.kv.put("ahk_script", "sample");
		c.kv.put("hotkey_show_desktop", "auto");
		c.kv.put("hotkey_switch_desktop", "auto");
		c.kv.put("hotkey_show_custom", "");
		c.kv.put("hotkey_switch_next", "");
		c.kv.put("hotkey_switch_prev", "");
		c.kv.put("hotkey_switch_new", "");
		Path f = PnatPaths.appConfFile();
		if (f != null && Files.isRegularFile(f)) {
			c.file = f;
			c.mergeFile(f);
		}
		return c;
	}

	public String get(String key) {
		String v = kv.get(key);
		return v == null ? "" : v;
	}

	public void set(String key, String value) {
		kv.put(key, value == null ? "" : value);
	}

	public String host() {
		return get("host");
	}

	public String port() {
		return get("port");
	}

	/** JRA 旧 DefaultIP：host 或 host:port */
	public String defaultRemoteHost() {
		String host = host();
		String port = port();
		if (host.isEmpty()) {
			return "";
		}
		if (port.isEmpty()) {
			return host;
		}
		return host + ":" + port;
	}

	public Path file() {
		return file;
	}

	public Path root() {
		return PnatPaths.root();
	}

	/** 写回 conf；保留原注释行，更新已有键，缺的键追加到末尾。 */
	public void save() throws IOException {
		Path f = file != null ? file : PnatPaths.appConfFile();
		if (f == null) {
			throw new IOException("找不到 conf/app.conf（设置 PNAT_ROOT）");
		}
		List<String> out = new ArrayList<String>();
		Map<String, String> pending = new LinkedHashMap<String, String>(kv);
		if (Files.isRegularFile(f)) {
			try (BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				String line;
				while ((line = r.readLine()) != null) {
					String raw = line.replace("\r", "");
					String trim = raw.trim();
					if (trim.isEmpty() || trim.startsWith("#")) {
						out.add(raw);
						continue;
					}
					int eq = trim.indexOf('=');
					if (eq <= 0) {
						out.add(raw);
						continue;
					}
					String key = trim.substring(0, eq).trim();
					if (pending.containsKey(key)) {
						out.add(key + "=" + pending.remove(key));
					} else {
						out.add(raw);
					}
				}
			}
		}
		for (Map.Entry<String, String> e : pending.entrySet()) {
			out.add(e.getKey() + "=" + e.getValue());
		}
		Files.createDirectories(f.getParent());
		try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
			for (String line : out) {
				w.write(line);
				w.write('\n');
			}
		}
		file = f;
	}

	private void mergeFile(Path f) {
		try (BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			String line;
			while ((line = r.readLine()) != null) {
				line = line.replace("\r", "").trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				int eq = line.indexOf('=');
				if (eq <= 0) {
					continue;
				}
				kv.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
			}
		} catch (IOException e) {
			System.err.println("读配置失败: " + f + " " + e);
		}
	}
}
