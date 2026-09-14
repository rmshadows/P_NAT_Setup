package application;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 读/写 conf/tightvnc.conf（批处理 SET key=value，与 install.bat 共用）。
 * 文件可能是 UTF-8 / GBK / 乱码注释；读时按字节容错，写回干净 UTF-8。
 */
public final class TightVncConf {
	private final Map<String, String> kv = new LinkedHashMap<String, String>();
	private Path file;

	public static TightVncConf load() {
		TightVncConf c = new TightVncConf();
		c.kv.put("ctrl_passwd", "");
		c.kv.put("vnc_passwd", "");
		c.kv.put("viewonly_passwd", "");
		c.kv.put("hide_systray", "0");
		c.kv.put("install_to", "C:\\Windows\\");
		Path f = filePath();
		if (f != null && Files.isRegularFile(f)) {
			c.file = f;
			c.mergeFile(f);
		}
		return c;
	}

	public static Path filePath() {
		Path conf = PnatPaths.confDir();
		return conf == null ? null : conf.resolve("tightvnc.conf");
	}

	public String get(String key) {
		String v = kv.get(key);
		return v == null ? "" : v;
	}

	public void set(String key, String value) {
		kv.put(key, value == null ? "" : value);
	}

	public Path file() {
		return file;
	}

	/** 摘要：密码是否已设、托盘、安装路径（高级部署确认框用）。 */
	public String summary() {
		return "控制密码: " + (get("ctrl_passwd").isEmpty() ? "未设" : "已设")
				+ "\nVNC 密码: " + (get("vnc_passwd").isEmpty() ? "未设" : "已设")
				+ "\n只看密码: " + (get("viewonly_passwd").isEmpty() ? "未设" : "已设")
				+ "\n隐藏托盘: " + ("1".equals(get("hide_systray")) ? "是" : "否")
				+ "\n安装路径: " + unquote(get("install_to"));
	}

	public void save() throws IOException {
		Path f = file != null ? file : filePath();
		if (f == null) {
			throw new IOException("找不到 conf/tightvnc.conf（设置 PNAT_ROOT）");
		}
		// 直接写干净模板，避免原文件编码/乱码注释导致读失败
		List<String> out = new ArrayList<String>();
		out.add("REM TightVNC config (CRLF). GUI and install.bat share this file.");
		out.add("REM Control password");
		out.add(formatSet("ctrl_passwd", get("ctrl_passwd")));
		out.add("REM VNC password");
		out.add(formatSet("vnc_passwd", get("vnc_passwd")));
		out.add("REM View-only password");
		out.add(formatSet("viewonly_passwd", get("viewonly_passwd")));
		out.add("REM Hide systray: 1=yes 0=no");
		out.add(formatSet("hide_systray", get("hide_systray").isEmpty() ? "0" : get("hide_systray")));
		out.add("REM Install path");
		out.add(formatSet("install_to", get("install_to").isEmpty() ? "C:\\Windows\\" : get("install_to")));
		Files.createDirectories(f.getParent());
		try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
			for (String line : out) {
				w.write(line);
				w.write("\r\n");
			}
		}
		file = f;
	}

	private void mergeFile(Path f) {
		try {
			String text = readTextLenient(f);
			String[] lines = text.split("\\r?\\n");
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i].trim();
				if (line.isEmpty() || startsRem(line)) {
					continue;
				}
				String[] parsed = parseSet(line);
				if (parsed != null) {
					kv.put(parsed[0], unquote(parsed[1]));
				}
			}
		} catch (IOException e) {
			System.err.println("读 TightVNC 配置失败: " + f + " " + e);
		}
	}

	/** 先试 UTF-8，再 GBK，最后按 ISO-8859-1 原样，绝不抛 MalformedInput。 */
	private static String readTextLenient(Path f) throws IOException {
		byte[] raw = Files.readAllBytes(f);
		if (raw.length >= 2 && raw[0] == (byte) 0xFF && raw[1] == (byte) 0xFE) {
			return new String(raw, StandardCharsets.UTF_16LE);
		}
		String utf8 = decodeReplace(raw, StandardCharsets.UTF_8);
		if (!utf8.contains("\uFFFD") && looksMostlyAsciiKeys(utf8)) {
			return utf8;
		}
		try {
			String gbk = decodeReplace(raw, Charset.forName("GBK"));
			if (!gbk.contains("\uFFFD")) {
				return gbk;
			}
		} catch (Exception ignored) {
		}
		return decodeReplace(raw, StandardCharsets.ISO_8859_1);
	}

	private static String decodeReplace(byte[] raw, Charset cs) {
		return new String(raw, cs);
	}

	private static boolean looksMostlyAsciiKeys(String text) {
		return text.toUpperCase(Locale.ROOT).contains("SET ")
				|| text.contains("ctrl_passwd")
				|| text.contains("vnc_passwd");
	}

	private static boolean startsRem(String trim) {
		String u = trim.toUpperCase(Locale.ROOT);
		return u.startsWith("REM") || u.startsWith("::") || u.startsWith("#");
	}

	/** 返回 [key, value]；无法解析返回 null。 */
	private static String[] parseSet(String trim) {
		String u = trim.toUpperCase(Locale.ROOT);
		if (!u.startsWith("SET ")) {
			int eq = trim.indexOf('=');
			if (eq <= 0) {
				return null;
			}
			return new String[] { trim.substring(0, eq).trim(), trim.substring(eq + 1).trim() };
		}
		String rest = trim.substring(4).trim();
		int eq = rest.indexOf('=');
		if (eq <= 0) {
			return null;
		}
		return new String[] { rest.substring(0, eq).trim(), rest.substring(eq + 1).trim() };
	}

	private static String formatSet(String key, String value) {
		String v = value == null ? "" : value;
		if ("install_to".equals(key) && !v.isEmpty() && !v.startsWith("\"")) {
			v = "\"" + v + "\"";
		}
		return "SET " + key + "=" + v;
	}

	private static String unquote(String v) {
		if (v == null) {
			return "";
		}
		String t = v.trim();
		if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
			return t.substring(1, t.length() - 1);
		}
		return t;
	}
}
