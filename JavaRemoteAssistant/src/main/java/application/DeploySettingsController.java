package application;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * 右击加载按钮：当前方式高级设置。右击下拉某一项：该方式高级设置。
 */
public class DeploySettingsController implements Initializable {
	@FXML
	private Label engineHint;
	@FXML
	private Label hostHint;
	@FXML
	private ComboBox<String> role;
	@FXML
	private TextField host;
	@FXML
	private TextField port;
	@FXML
	private ComboBox<String> monitor;
	@FXML
	private CheckBox autoAccept;
	@FXML
	private ComboBox<String> tightvncMode;
	@FXML
	private PasswordField ctrlPasswd;
	@FXML
	private PasswordField vncPasswd;
	@FXML
	private PasswordField viewonlyPasswd;
	@FXML
	private CheckBox hideSystray;
	@FXML
	private TextField installTo;
	@FXML
	private VBox roleProps;
	@FXML
	private VBox connectProps;
	@FXML
	private VBox commonProps;
	@FXML
	private VBox dayonProps;
	@FXML
	private VBox tightvncProps;
	@FXML
	private VBox linuxVncProps;
	@FXML
	private ComboBox<String> vncDisplay;
	@FXML
	private ComboBox<String> vncImpl;
	@FXML
	private ComboBox<String> vncConnect;
	@FXML
	private ComboBox<String> vncEncrypt;
	@FXML
	private PasswordField linuxVncPasswd;
	@FXML
	private VBox rdpProps;

	private static final String[] ROLE_LABELS = new String[] { "受助端", "协助端" };
	private static final String[] ROLE_VALUES = new String[] { "assisted", "assistant" };
	private static final String[] MODE_LABELS = new String[] { "portable（便携加载）", "service（系统服务/高级）" };
	private static final String[] MODE_VALUES = new String[] { "portable", "service" };
	private static final String[] VNC_IMPL_LABELS = new String[] {
			"自动（推荐 x11vnc）", "x11vnc（:0，可反向连出）", "TigerVNC（x0vncserver，只监听）" };
	private static final String[] VNC_IMPL_VALUES = new String[] { "auto", "x11vnc", "tigervnc" };
	private static final String[] VNC_CONN_LABELS = new String[] { "反向连出（连协助端 Viewer）", "等待连入（本机 :5900）" };
	private static final String[] VNC_CONN_VALUES = new String[] { "reverse", "listen" };
	private static final String[] VNC_ENC_LABELS = new String[] { "不加密（TightVNC Viewer 能连）", "加密 TLS（需支持 TLS 的 Viewer）" };
	private static final String[] VNC_ENC_VALUES = new String[] { "0", "1" };

	/** 当前对话框针对的引擎（dayon / vnc / rdp）。 */
	private String engineMenuId;

	/** 右击加载按钮：当前方式。 */
	public static void show() {
		showFor(Tools.desktopMenuId());
	}

	/** 右击下拉某一项：该方式。 */
	public static void showFor(String engineId) {
		final String id = (engineId == null || engineId.trim().isEmpty())
				? Tools.desktopMenuId()
				: engineId.trim().toLowerCase();
		try {
			Dialog<ButtonType> d = new Dialog<ButtonType>();
			d.setTitle(Tools.desktopLabelFor(id) + " 高级设置");
			d.setHeaderText(null);
			ButtonType save = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
			ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
			d.getDialogPane().getButtonTypes().addAll(save, cancel);
			FXMLLoader loader = new FXMLLoader(App.class.getResource("deploy.fxml"));
			d.getDialogPane().setContent(loader.load());
			final DeploySettingsController ctl = loader.getController();
			ctl.applyEngine(id);
			Dialogs.preparePublic(d);
			Optional<ButtonType> r = d.showAndWait();
			if (r.isPresent() && r.get() == save) {
				ctl.save();
			}
		} catch (Exception e) {
			Dialogs.error("高级设置", e.getMessage() == null ? e.toString() : e.getMessage());
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		role.setItems(FXCollections.observableArrayList(ROLE_LABELS));
		monitor.setItems(FXCollections.observableArrayList("all", "primary", "1", "2"));
		if (tightvncMode != null) {
			tightvncMode.setItems(FXCollections.observableArrayList(MODE_LABELS));
		}
		if (vncDisplay != null) {
			vncDisplay.setEditable(true);
			vncDisplay.setItems(FXCollections.observableArrayList(
					Tools.vncDisplayChoice(0),
					Tools.vncDisplayChoice(1),
					Tools.vncDisplayChoice(2),
					Tools.vncDisplayChoice(3),
					Tools.vncDisplayChoice(4)));
		}
		if (vncImpl != null) {
			vncImpl.setItems(FXCollections.observableArrayList(VNC_IMPL_LABELS));
		}
		if (vncConnect != null) {
			vncConnect.setItems(FXCollections.observableArrayList(VNC_CONN_LABELS));
			vncConnect.getSelectionModel().selectedItemProperty()
					.addListener((obs, o, n) -> refreshPanels());
		}
		if (vncEncrypt != null) {
			vncEncrypt.setItems(FXCollections.observableArrayList(VNC_ENC_LABELS));
		}

		AppConf c = AppConf.load();
		TightVncConf tvn = TightVncConf.load();
		selectLabeled(role, ROLE_LABELS, ROLE_VALUES, c.get("role"), "assisted");
		select(monitor, c.get("monitor"), "all");
		host.setText(c.host().isEmpty() ? "127.0.0.1" : c.host());
		port.setText(c.port().isEmpty() ? "8080" : c.port());
		if (autoAccept != null) {
			autoAccept.setSelected(!"0".equals(c.get("auto_accept")));
		}
		if (tightvncMode != null) {
			selectLabeled(tightvncMode, MODE_LABELS, MODE_VALUES, c.get("tightvnc_mode"), "portable");
		}
		if (vncDisplay != null) {
			select(vncDisplay, Tools.vncDisplayChoice(Tools.parseVncDisplay(c.get("vnc_display"))),
					Tools.vncDisplayChoice(0));
		}
		if (vncImpl != null) {
			selectLabeled(vncImpl, VNC_IMPL_LABELS, VNC_IMPL_VALUES, c.get("vnc_impl"), "auto");
		}
		if (vncConnect != null) {
			selectLabeled(vncConnect, VNC_CONN_LABELS, VNC_CONN_VALUES, c.get("vnc_connect"), "reverse");
		}
		if (vncEncrypt != null) {
			selectLabeled(vncEncrypt, VNC_ENC_LABELS, VNC_ENC_VALUES, c.get("vnc_encrypt"), "0");
		}
		if (linuxVncPasswd != null) {
			linuxVncPasswd.setText(c.get("vnc_passwd"));
		}
		if (ctrlPasswd != null) {
			ctrlPasswd.setText(tvn.get("ctrl_passwd"));
			vncPasswd.setText(tvn.get("vnc_passwd"));
			viewonlyPasswd.setText(tvn.get("viewonly_passwd"));
			hideSystray.setSelected("1".equals(tvn.get("hide_systray")));
			installTo.setText(tvn.get("install_to").isEmpty() ? "C:\\Windows\\" : tvn.get("install_to"));
		}
		applyEngine(Tools.desktopMenuId());
	}

	private void applyEngine(String menuId) {
		engineMenuId = menuId == null || menuId.isEmpty() ? Tools.desktopMenuId() : menuId;
		refreshPanels();
	}

	private void refreshPanels() {
		String menu = engineMenuId == null ? Tools.desktopMenuId() : engineMenuId;
		boolean dayon = "dayon".equals(menu);
		boolean rdp = "rdp".equals(menu);
		boolean vnc = "vnc".equals(menu);
		boolean win = "windows".equals(Session.logicalOsName());
		boolean tight = vnc && win;
		boolean linuxVnc = vnc && !win;
		boolean showRdp = rdp && win;
		if (engineHint != null) {
			if (rdp && !win) {
				engineHint.setText("Linux 不提供 RDP。请改选 Dayon 或 VNC。");
			} else if (linuxVnc) {
				engineHint.setText("VNC :0。连接方式选反向或等待。反向需要 x11vnc，协助端 Viewer 先监听。");
			} else if (tight) {
				engineHint.setText("VNC（TightVNC）：本机是 Server。协助端用 Viewer；地址用于反向连出。没有 Dayon 那种双角色。");
			} else if (dayon) {
				engineHint.setText("Dayon 才分受助端 / 协助端（两套程序）。保存后当前方式会设为 Dayon。");
			} else {
				engineHint.setText(Tools.desktopLabelFor(menu) + " 的高级设置。保存后当前方式会设为这项。");
			}
		}
		if (hostHint != null) {
			if (dayon) {
				hostHint.setText("协助端地址（受助端连出时用）");
			} else if (linuxVnc) {
				hostHint.setText("协助端 Viewer 监听地址（反向连出时用；等待连入不需要）");
			} else if (tight) {
				hostHint.setText("协助端 Viewer 地址（本机 Server 反向连出）");
			} else {
				hostHint.setText("协助端地址");
			}
		}
		setPanel(roleProps, dayon);
		setPanel(connectProps, dayon || tight || (linuxVnc && linuxVncReverseSelected()));
		setPanel(commonProps, !linuxVnc);
		setPanel(dayonProps, dayon);
		setPanel(tightvncProps, tight);
		setPanel(linuxVncProps, linuxVnc);
		setPanel(rdpProps, showRdp);
	}

	private boolean linuxVncReverseSelected() {
		if (vncConnect == null || vncConnect.getSelectionModel().getSelectedItem() == null) {
			return Tools.vncReverse();
		}
		return "reverse".equals(labeledValue(vncConnect, VNC_CONN_LABELS, VNC_CONN_VALUES, "reverse"));
	}

	private boolean linuxVncSaving() {
		return "vnc".equals(engineMenuId) && !"windows".equals(Session.logicalOsName());
	}

	private boolean dayonSaving() {
		return "dayon".equals(engineMenuId);
	}

	private boolean connectSaving() {
		return dayonSaving() || "vnc".equals(engineMenuId);
	}

	private static String comboText(ComboBox<String> box) {
		if (box == null) {
			return "";
		}
		String editor = null;
		if (box.isEditable() && box.getEditor() != null) {
			editor = box.getEditor().getText();
		}
		if (editor != null && !editor.trim().isEmpty()) {
			return editor.trim();
		}
		return value(box, "");
	}

	private static void setPanel(VBox box, boolean on) {
		if (box == null) {
			return;
		}
		box.setVisible(on);
		box.setManaged(on);
	}

	void save() throws IOException {
		AppConf c = AppConf.load();
		boolean win = "windows".equals(Session.logicalOsName());
		if (engineMenuId != null && !engineMenuId.isEmpty()) {
			if (!("rdp".equals(engineMenuId) && !win)) {
				c.set("desktop", engineMenuId);
			}
		}
		if (dayonSaving() && role != null) {
			c.set("role", labeledValue(role, ROLE_LABELS, ROLE_VALUES, "assisted"));
		}
		if (connectSaving()) {
			c.set("host", textOr(host, "127.0.0.1"));
			c.set("port", textOr(port, "8080"));
		}
		if (monitor != null && monitor.isVisible()) {
			c.set("monitor", value(monitor, "all"));
		}
		if (autoAccept != null) {
			c.set("auto_accept", autoAccept.isSelected() ? "1" : "0");
		}
		if (tightvncMode != null && tightvncMode.getSelectionModel().getSelectedItem() != null) {
			c.set("tightvnc_mode", labeledValue(tightvncMode, MODE_LABELS, MODE_VALUES, "portable"));
		}
		if (vncDisplay != null && linuxVncSaving()) {
			c.set("vnc_display", String.valueOf(Tools.parseVncDisplay(comboText(vncDisplay))));
		}
		if (linuxVncSaving()) {
			if (vncImpl != null) {
				c.set("vnc_impl", labeledValue(vncImpl, VNC_IMPL_LABELS, VNC_IMPL_VALUES, "auto"));
			}
			if (vncConnect != null) {
				c.set("vnc_connect", labeledValue(vncConnect, VNC_CONN_LABELS, VNC_CONN_VALUES, "reverse"));
			}
			if (vncEncrypt != null) {
				c.set("vnc_encrypt", labeledValue(vncEncrypt, VNC_ENC_LABELS, VNC_ENC_VALUES, "0"));
			}
			if (linuxVncPasswd != null) {
				c.set("vnc_passwd", linuxVncPasswd.getText() == null ? "" : linuxVncPasswd.getText());
			}
		}
		c.save();

		boolean saveTvn = "vnc".equals(engineMenuId) && win;
		if (saveTvn && ctrlPasswd != null) {
			TightVncConf tvn = TightVncConf.load();
			tvn.set("ctrl_passwd", ctrlPasswd.getText() == null ? "" : ctrlPasswd.getText());
			tvn.set("vnc_passwd", vncPasswd.getText() == null ? "" : vncPasswd.getText());
			tvn.set("viewonly_passwd", viewonlyPasswd.getText() == null ? "" : viewonlyPasswd.getText());
			tvn.set("hide_systray", hideSystray.isSelected() ? "1" : "0");
			tvn.set("install_to", textOr(installTo, "C:\\Windows\\"));
			tvn.save();
		}
	}

	private static void select(ComboBox<String> box, String value, String fallback) {
		String v = value == null || value.isEmpty() ? fallback : value;
		if (!box.getItems().contains(v)) {
			box.getItems().add(v);
		}
		box.getSelectionModel().select(v);
	}

	private static void selectLabeled(ComboBox<String> box, String[] labels, String[] values, String value,
			String fallback) {
		String v = value == null || value.isEmpty() ? fallback : value.trim().toLowerCase();
		for (int i = 0; i < values.length; i++) {
			if (values[i].equalsIgnoreCase(v)) {
				box.getSelectionModel().select(labels[i]);
				return;
			}
		}
		box.getSelectionModel().select(labels[0]);
	}

	private static String labeledValue(ComboBox<String> box, String[] labels, String[] values, String fallback) {
		String sel = box.getSelectionModel().getSelectedItem();
		if (sel == null) {
			return fallback;
		}
		for (int i = 0; i < labels.length; i++) {
			if (labels[i].equals(sel)) {
				return values[i];
			}
		}
		return fallback;
	}

	private static String value(ComboBox<String> box, String fallback) {
		String v = box.getSelectionModel().getSelectedItem();
		return v == null || v.isEmpty() ? fallback : v;
	}

	private static String textOr(TextField field, String fallback) {
		if (field == null || field.getText() == null) {
			return fallback;
		}
		String t = field.getText().trim();
		return t.isEmpty() ? fallback : t;
	}
}
