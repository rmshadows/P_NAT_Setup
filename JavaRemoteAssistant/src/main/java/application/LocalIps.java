package application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 本机全部 IPv4/IPv6（各网卡都列）；默认仍智能猜主 IPv4。
 */
public final class LocalIps {
	private LocalIps() {
	}

	/** 下拉显示项：完整地址 + 网卡名。 */
	public static final class Item {
		public final String ip;
		public final String iface;
		public final boolean ipv6;
		public final int score;

		Item(String ip, String iface, boolean ipv6, int score) {
			this.ip = ip;
			this.iface = iface == null ? "" : iface;
			this.ipv6 = ipv6;
			this.score = score;
		}

		/** 写入配置用的纯地址。 */
		public String address() {
			return ip;
		}

		@Override
		public String toString() {
			String tag = ipv6 ? "IPv6" : "IPv4";
			if (iface.isEmpty()) {
				return ip + "  (" + tag + ")";
			}
			return ip + "  [" + iface + " · " + tag + "]";
		}
	}

	/** 全部地址：主网卡 IPv4 靠前，其它网卡 / IPv6 / 回环靠后。 */
	public static List<Item> allItems() {
		Map<String, Item> best = new LinkedHashMap<String, Item>();
		String routeIp = defaultRouteIpv4();
		try {
			Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
			if (nis != null) {
				while (nis.hasMoreElements()) {
					NetworkInterface ni = nis.nextElement();
					String name;
					boolean up;
					boolean loop;
					boolean virt;
					try {
						name = ni.getName() == null ? "" : ni.getName();
						up = ni.isUp();
						loop = ni.isLoopback();
						virt = ni.isVirtual();
					} catch (Exception ignored) {
						continue;
					}
					// 未启用的网卡也列出（低分），避免「其它网卡没了」
					Enumeration<InetAddress> addrs = ni.getInetAddresses();
					while (addrs.hasMoreElements()) {
						InetAddress a = addrs.nextElement();
						boolean v6 = a instanceof Inet6Address;
						boolean v4 = a instanceof Inet4Address;
						if (!v4 && !v6) {
							continue;
						}
						String ip = stripZone(a.getHostAddress());
						if (ip.isEmpty()) {
							continue;
						}
						int score = score(ip, name.toLowerCase(Locale.ROOT), loop, virt, !up, v6, routeIp);
						String key = ip + "@" + name;
						Item cur = best.get(key);
						if (cur == null || score > cur.score) {
							best.put(key, new Item(ip, name, v6, score));
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("列举本机 IP 失败: " + e);
		}
		List<Item> list = new ArrayList<Item>(best.values());
		Collections.sort(list, new Comparator<Item>() {
			@Override
			public int compare(Item a, Item b) {
				if (a.score != b.score) {
					return Integer.compare(b.score, a.score);
				}
				if (a.ipv6 != b.ipv6) {
					return a.ipv6 ? 1 : -1;
				}
				int c = a.iface.compareToIgnoreCase(b.iface);
				if (c != 0) {
					return c;
				}
				return a.ip.compareTo(b.ip);
			}
		});
		if (list.isEmpty()) {
			list.add(new Item("127.0.0.1", "lo", false, -100));
		}
		return list;
	}

	/** 兼容旧调用：仅地址字符串，顺序与 allItems 一致。 */
	public static List<String> ipv4List() {
		List<String> out = new ArrayList<String>();
		for (Item it : allItems()) {
			if (!out.contains(it.ip)) {
				out.add(it.ip);
			}
		}
		return out;
	}

	/** 猜主地址：优先默认路由 IPv4，再高分非回环 IPv4。 */
	public static String guessPrimary(List<Item> items) {
		if (items == null || items.isEmpty()) {
			return "127.0.0.1";
		}
		String viaRoute = defaultRouteIpv4();
		if (viaRoute != null) {
			for (Item it : items) {
				if (viaRoute.equals(it.ip)) {
					return it.ip;
				}
			}
		}
		for (Item it : items) {
			if (!it.ipv6 && !it.ip.startsWith("127.")) {
				return it.ip;
			}
		}
		return items.get(0).ip;
	}

	public static String guessPrimaryFromStrings(List<String> ips) {
		List<Item> items = allItems();
		String g = guessPrimary(items);
		if (ips != null && ips.contains(g)) {
			return g;
		}
		if (ips == null || ips.isEmpty()) {
			return g;
		}
		return ips.get(0);
	}

	public static Item findItem(List<Item> items, String ip) {
		if (ip == null || ip.isEmpty() || items == null) {
			return null;
		}
		for (Item it : items) {
			if (ip.equals(it.ip)) {
				return it;
			}
		}
		return null;
	}

	private static String stripZone(String host) {
		if (host == null) {
			return "";
		}
		int pct = host.indexOf('%');
		return pct >= 0 ? host.substring(0, pct) : host;
	}

	private static boolean isNoiseIface(String name) {
		return name.startsWith("docker") || name.startsWith("br-") || name.startsWith("veth")
				|| name.startsWith("virbr") || name.startsWith("vmnet") || name.startsWith("vbox")
				|| name.startsWith("tun") || name.startsWith("tap");
	}

	private static int score(String ip, String iface, boolean loopback, boolean virt, boolean down, boolean v6,
			String routeIp) {
		int s = 0;
			if (loopback || ip.startsWith("127.") || "::1".equals(ip)) {
			s -= 100;
		}
		if (down) {
			s -= 40;
		}
		if (virt) {
			s -= 15;
		}
		if (isNoiseIface(iface)) {
			s -= 25;
		}
		if (v6) {
			s -= 5; // IPv4 略优先作默认，但仍全部列出
			if (ip.toLowerCase(Locale.ROOT).startsWith("fe80:")) {
				s -= 20; // 链路本地靠后
			} else {
				s += 15;
			}
		} else {
			if (ip.equals(routeIp)) {
				s += 100;
			}
			if (ip.startsWith("192.168.")) {
				s += 50;
			} else if (ip.startsWith("10.")) {
				s += 40;
			} else if (is172Private(ip)) {
				s += 35;
			} else if (!ip.startsWith("127.")) {
				s += 10;
			}
		}
		if (iface.startsWith("wl") || iface.startsWith("wlan") || iface.startsWith("wifi")) {
			s += 20;
		} else if (iface.startsWith("en") || iface.startsWith("eth") || iface.startsWith("em")) {
			s += 25;
		} else if (iface.startsWith("zt") || iface.startsWith("wg")) {
			s += 5; // ZeroTier / WireGuard 仍显示，略低于物理网卡
		}
		return s;
	}

	private static boolean is172Private(String ip) {
		if (!ip.startsWith("172.")) {
			return false;
		}
		String[] p = ip.split("\\.");
		if (p.length < 2) {
			return false;
		}
		try {
			int second = Integer.parseInt(p[1]);
			return second >= 16 && second <= 31;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static String defaultRouteIpv4() {
		try {
			Process p = new ProcessBuilder("ip", "-4", "route", "get", "1.1.1.1").redirectErrorStream(true).start();
			try (BufferedReader r = new BufferedReader(
					new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line = r.readLine();
				p.waitFor();
				if (line == null) {
					return null;
				}
				String[] tok = line.split("\\s+");
				for (int i = 0; i < tok.length - 1; i++) {
					if ("src".equals(tok[i])) {
						return tok[i + 1];
					}
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	public static String applyClientIp(String ip) throws IOException {
		if (ip == null || ip.trim().isEmpty()) {
			throw new IOException("IP 为空");
		}
		ip = ip.trim();
		AppConf c = AppConf.load();
		c.set("client_ip", ip);
		c.save();
		StringBuilder note = new StringBuilder();
		note.append("app.conf client_ip=").append(ip);
		// frpc local_ip 通常要 IPv4；选了 IPv6 只记 client_ip，不硬改 frpc
		if (ip.indexOf(':') >= 0) {
			note.append("\n(IPv6：未改 frpc local_ip)");
			return note.toString();
		}
		Path root = PnatPaths.root();
		if (root == null) {
			return note.toString();
		}
		Path frpcDir = root.resolve("conf").resolve("frpc");
		if (!Files.isDirectory(frpcDir)) {
			return note.toString();
		}
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(frpcDir, "*.ini")) {
			for (Path ini : ds) {
				if (patchIniLocalIp(ini, ip)) {
					note.append('\n').append(ini.getFileName()).append(" local_ip=").append(ip);
				}
			}
		}
		return note.toString();
	}

	private static boolean patchIniLocalIp(Path ini, String ip) throws IOException {
		List<String> lines = Files.readAllLines(ini, StandardCharsets.UTF_8);
		boolean changed = false;
		List<String> out = new ArrayList<String>(lines.size());
		for (String line : lines) {
			String trim = line.trim();
			if (trim.toLowerCase(Locale.ROOT).startsWith("local_ip") && trim.contains("=")) {
				int eq = line.indexOf('=');
				String left = line.substring(0, eq + 1);
				String rebuilt = (eq + 1 < line.length() && line.charAt(eq + 1) == ' ')
						? left + " " + ip
						: left + ip;
				if (!rebuilt.equals(line)) {
					changed = true;
				}
				out.add(rebuilt);
			} else {
				out.add(line);
			}
		}
		if (changed) {
			Files.write(ini, out, StandardCharsets.UTF_8);
		}
		return changed;
	}
}
