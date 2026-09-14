package application;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.stage.Popup;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 统一弹窗：挂到主窗口、先取消置顶，避免 Linux 上对话框被挡住、关不掉。
 */
public final class Dialogs {
	private Dialogs() {
	}

	public static void error(String title, String message) {
		show(AlertType.ERROR, title, null, message);
	}

	public static void warn(String message) {
		show(AlertType.WARNING, "提示", null, message);
	}

	public static void info(String title, String header, String message) {
		show(AlertType.INFORMATION, title, header, message);
	}

	private static Popup toastPopup;
	private static Label toastLabel;
	private static PauseTransition toastHide;

	/** 底部短提示，约 2 秒后消失。不抢鼠标，切桌面按钮可连点。可从后台线程调用。 */
	public static void toast(final String message) {
		if (message == null || message.isEmpty()) {
			return;
		}
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					toast(message);
				}
			});
			return;
		}
		Stage owner = App.getStage();
		if (owner != null && owner.isShowing()) {
			if (toastLabel == null) {
				toastLabel = new Label();
				toastLabel.setMouseTransparent(true);
				toastLabel.setStyle("-fx-background-color: #202020; -fx-text-fill: white; "
						+ "-fx-padding: 10 16 10 16; -fx-background-radius: 6; -fx-font-size: 13px;");
			}
			toastLabel.setText(message);
			if (toastPopup == null) {
				toastPopup = new Popup();
				toastPopup.getContent().add(toastLabel);
				toastPopup.setAutoHide(false);
				toastPopup.setAutoFix(true);
				toastPopup.setConsumeAutoHidingEvents(false);
			}
			double x = owner.getX() + Math.max(8, (owner.getWidth() - 240) / 2);
			double y = owner.getY() + owner.getHeight() - 56;
			if (!toastPopup.isShowing()) {
				toastPopup.show(owner, x, y);
			} else {
				toastPopup.setX(x);
				toastPopup.setY(y);
			}
			if (toastHide != null) {
				toastHide.stop();
			}
			toastHide = new PauseTransition(Duration.seconds(2.2));
			toastHide.setOnFinished(e -> toastPopup.hide());
			toastHide.play();
			return;
		}
		Label l = new Label(message);
		l.setMouseTransparent(true);
		l.setStyle("-fx-background-color: #202020; -fx-text-fill: white; "
				+ "-fx-padding: 10 16 10 16; -fx-background-radius: 6; -fx-font-size: 13px;");
		Stage t = new Stage(StageStyle.UNDECORATED);
		t.initModality(Modality.NONE);
		t.setAlwaysOnTop(true);
		t.setResizable(false);
		VBox box = new VBox(l);
		box.setAlignment(Pos.CENTER);
		box.setMouseTransparent(true);
		box.setStyle("-fx-background-color: #202020;");
		Scene sc = new Scene(box);
		sc.setFill(javafx.scene.paint.Color.rgb(32, 32, 32));
		t.setScene(sc);
		t.show();
		PauseTransition hide = new PauseTransition(Duration.seconds(2.2));
		hide.setOnFinished(e -> t.close());
		hide.play();
	}

	public static boolean confirm(String title, String header, String message) {
		Alert a = new Alert(AlertType.CONFIRMATION);
		a.setTitle(title);
		a.setHeaderText(header);
		a.setContentText(message);
		prepare(a);
		Optional<ButtonType> r = a.showAndWait();
		return r.isPresent() && r.get() == ButtonType.OK;
	}

	/** 取消返回 null；空输入返回 defaultValue。 */
	public static String input(String message, String defaultValue) {
		TextInputDialog d = new TextInputDialog(defaultValue == null ? "" : defaultValue);
		d.setTitle("输入");
		d.setHeaderText(null);
		d.setContentText(message);
		prepare(d);
		Optional<String> r = d.showAndWait();
		if (!r.isPresent()) {
			return null;
		}
		String v = r.get();
		if (v == null || v.trim().isEmpty()) {
			return defaultValue;
		}
		return v.trim();
	}

	/** 选目录；取消返回 null。 */
	public static Path chooseDirectory(String title, Path initial) {
		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle(title == null ? "选择文件夹" : title);
		if (initial != null && java.nio.file.Files.isDirectory(initial)) {
			chooser.setInitialDirectory(initial.toFile());
		} else {
			File home = new File(System.getProperty("user.home", "."));
			if (home.isDirectory()) {
				chooser.setInitialDirectory(home);
			}
		}
		App.setTop(false);
		try {
			Stage owner = App.getStage();
			File picked = owner == null ? chooser.showDialog(null) : chooser.showDialog(owner);
			if (picked == null) {
				return null;
			}
			return picked.toPath().toAbsolutePath().normalize();
		} finally {
			App.restorePreferredTop();
		}
	}

	/** 选文件；取消返回 null。 */
	public static Path chooseFile(String title, Path initialDir) {
		FileChooser chooser = new FileChooser();
		chooser.setTitle(title == null ? "选择文件" : title);
		Path dir = initialDir;
		if (dir != null && Files.isRegularFile(dir)) {
			dir = dir.getParent();
		}
		if (dir != null && Files.isDirectory(dir)) {
			chooser.setInitialDirectory(dir.toFile());
		} else {
			File home = new File(System.getProperty("user.home", "."));
			if (home.isDirectory()) {
				chooser.setInitialDirectory(home);
			}
		}
		App.setTop(false);
		try {
			Stage owner = App.getStage();
			File picked = owner == null ? chooser.showOpenDialog(null) : chooser.showOpenDialog(owner);
			if (picked == null) {
				return null;
			}
			return picked.toPath().toAbsolutePath().normalize();
		} finally {
			App.restorePreferredTop();
		}
	}

	/**
	 * 录制组合键。取消返回 null；确定返回 xdotool 风格如 ctrl+alt+Right。
	 * 系统热键会被 DE 抢走，因此：
	 * 1) 可直接手输组合；
	 * 2) Linux 用 tool/capture_hotkey.py（XGrabKeyboard）抓取；
	 * 3) 仍尝试 JavaFX 捕获未绑定热键。
	 */
	public static String captureHotkey(String title, String current) {
		Dialog<String> d = new Dialog<String>();
		d.setTitle(title == null ? "录制热键" : title);
		d.setHeaderText(null);
		ButtonType ok = new ButtonType("使用此组合", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
		d.getDialogPane().getButtonTypes().addAll(ok, cancel);

		final boolean win = Tools.isWindows();
		final Label tip = new Label(win
				? "点「Win 抓取」后按组合键；或手输 / 粘贴（Ctrl+C / Ctrl+V）。\n"
						+ "例：win+d 、 ctrl+win+Right 、 ctrl+shift+Escape"
				: "点「X11 抓取」后按组合键；或手输 / 粘贴（Ctrl+C / Ctrl+V）。\n"
						+ "系统热键会被桌面抢走，不要只对着窗口干按。");
		tip.setWrapText(true);

		final TextField field = new TextField(current == null ? "" : current.trim());
		field.setPromptText(win ? "例如：win+d  或  ctrl+win+Right" : "例如：super+d  或  ctrl+alt+Right");
		field.setEditable(true);

		final Button copyCombo = new Button("复制组合");
		copyCombo.setOnAction(e -> copyText(field.getText()));
		final String grabIdle = win ? "Win 抓取（推荐）" : "X11 抓取（推荐）";
		final String grabBusy = "抓取中… 请按组合键（Esc 取消）";
		final Button grabBtn = new Button(grabIdle);
		HBox actions = new HBox(8, grabBtn, copyCombo);
		HBox.setHgrow(grabBtn, Priority.ALWAYS);
		grabBtn.setMaxWidth(Double.MAX_VALUE);

		final TextArea notes = new TextArea();
		notes.setEditable(true);
		notes.setWrapText(true);
		notes.setPrefRowCount(7);
		notes.setPromptText("状态（可全选复制）");
		notes.setText(buildCaptureHelp());

		final Button copyInstall = new Button(win ? "复制示例组合" : "复制安装命令");
		copyInstall.setOnAction(e -> {
			String cmd = extractInstallCommand(notes.getText());
			copyText(cmd);
		});

		final AtomicBoolean grabbing = new AtomicBoolean(false);
		final Set<KeyCode> down = new LinkedHashSet<KeyCode>();

		grabBtn.setOnAction(ev -> {
			if (!grabbing.compareAndSet(false, true)) {
				return;
			}
			setGrabbingUi(d, grabBtn, notes, true, grabBusy);
			notes.setText("抓取中：请按下目标组合键（Esc 取消）\n\n" + buildCaptureHelp());
			new Thread(new Runnable() {
				@Override
				public void run() {
					final String[] out = new String[2];
					try {
						out[0] = runCaptureHotkeyScript();
					} catch (Exception e) {
						out[1] = e.getMessage();
					}
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							grabbing.set(false);
							setGrabbingUi(d, grabBtn, notes, false, grabIdle);
							if (out[0] != null && !out[0].isEmpty()) {
								field.setText(out[0]);
								notes.setText("已捕获：" + out[0] + "\n可点「复制组合」。\n\n" + buildCaptureHelp());
							} else {
								notes.setText("抓取失败：" + (out[1] == null ? "未知" : out[1])
										+ "\n\n" + buildCaptureHelp());
							}
						}
					});
				}
			}, "jra-capture-hotkey").start();
		});

		VBox box = new VBox(10, tip, field, actions, notes, copyInstall);
		box.setPadding(new Insets(12));
		VBox.setVgrow(notes, Priority.ALWAYS);
		d.getDialogPane().setContent(box);
		prepare(d);
		d.getDialogPane().setPrefWidth(500);

		d.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
			if (e.getTarget() instanceof TextInputControl) {
				return; // 手输、复制、粘贴不要抢
			}
			if (e.isControlDown() && (e.getCode() == KeyCode.C || e.getCode() == KeyCode.V
					|| e.getCode() == KeyCode.A || e.getCode() == KeyCode.X)) {
				return;
			}
			e.consume();
			if (e.getCode() == KeyCode.ESCAPE) {
				down.clear();
				return;
			}
			down.add(e.getCode());
			if (!DesktopHotkeys.isModifier(e.getCode())) {
				String combo = DesktopHotkeys.formatCombo(down, e.getCode());
				field.setText(combo);
				notes.setText("Java 捕获：" + combo + "（系统热键请用「"
						+ (Tools.isWindows() ? "Win" : "X11") + " 抓取」）\n\n" + buildCaptureHelp());
			}
		});
		d.getDialogPane().addEventFilter(KeyEvent.KEY_RELEASED, e -> {
			if (e.getTarget() instanceof TextInputControl) {
				return;
			}
			e.consume();
			down.remove(e.getCode());
		});

		d.setResultConverter(btn -> {
			if (btn != ok) {
				return null;
			}
			String v = field.getText();
			return v == null || v.trim().isEmpty() ? null : v.trim();
		});
		Optional<String> r = d.showAndWait();
		return r.orElse(null);
	}

	private static void copyText(String text) {
		if (text == null) {
			return;
		}
		ClipboardContent c = new ClipboardContent();
		c.putString(text);
		Clipboard.getSystemClipboard().setContent(c);
	}

	/** 探测缺的包，给出可复制的安装命令（按平台）。 */
	private static String buildCaptureHelp() {
		if (Tools.isWindows()) {
			return "Windows 用系统 PowerShell 抓取，不用装 xdotool / apt。\n"
					+ "点「Win 抓取」再按键。示例：\n"
					+ "win+d\n"
					+ "ctrl+win+Right\n"
					+ "ctrl+win+Left\n"
					+ "ctrl+win+d\n"
					+ "ctrl+shift+Escape";
		}
		return LinuxDeps.captureHelp();
	}

	/** Linux 依赖：勾选要装的包，复制对应 apt 命令。Windows 不应进来。 */
	public static void showLinuxDeps() {
		if (Tools.isWindows()) {
			return;
		}
		Dialog<ButtonType> d = new Dialog<ButtonType>();
		d.setTitle("Linux 依赖");
		d.setHeaderText(null);
		ButtonType close = new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE);
		d.getDialogPane().getButtonTypes().add(close);

		Label tip = new Label("勾选要 apt 安装的包。缺的默认勾上。复制后到终端粘贴（要 sudo）。");
		tip.setWrapText(true);
		tip.getStyleClass().add("hint");

		final LinuxDeps.PythonEnv env = LinuxDeps.pythonEnv();
		Label pyHint = new Label(LinuxDeps.pythonHint(env));
		pyHint.setWrapText(true);
		pyHint.getStyleClass().add("hint");

		final List<CheckBox> boxes = new ArrayList<CheckBox>();
		VBox list = new VBox(6);
		for (LinuxDeps.Item item : LinuxDeps.probe(env)) {
			String mark = item.ok ? "已装" : "缺少";
			String need = item.required ? "必需" : "可选";
			CheckBox cb = new CheckBox(item.apt + "  〔" + need + " · " + mark + "〕  " + item.why);
			cb.setWrapText(true);
			cb.setMaxWidth(Double.MAX_VALUE);
			cb.setUserData(item.apt);
			boolean pythonLib = "python3-xlib".equals(item.apt) || "python3-pycryptodome".equals(item.apt);
			if (env.venv && pythonLib) {
				cb.setSelected(false);
				cb.setDisable(true);
			} else {
				cb.setSelected(!item.ok);
			}
			boxes.add(cb);
			list.getChildren().add(cb);
		}

		CheckBox withInit = new CheckBox(env.venv
				? "附带 init/install.py（会在当前 venv 里 pip，Debian 13 系统 Python 别用）"
				: "附带 init/install.py（Debian 13 对系统 pip 常失败，一般不要勾）");
		withInit.setWrapText(true);
		withInit.setMaxWidth(Double.MAX_VALUE);
		withInit.setSelected(false);

		Label cmdCaption = new Label("将复制：");
		cmdCaption.getStyleClass().add("hint");
		final TextArea preview = new TextArea();
		preview.setEditable(false);
		preview.setWrapText(true);
		preview.setPrefRowCount(4);
		preview.setPromptText("先勾选包");

		Runnable refresh = () -> {
			List<String> pkgs = new ArrayList<String>();
			for (CheckBox cb : boxes) {
				if (cb.isSelected() && !cb.isDisable()) {
					pkgs.add(String.valueOf(cb.getUserData()));
				}
			}
			preview.setText(LinuxDeps.installCommand(pkgs, withInit.isSelected()));
		};
		for (CheckBox cb : boxes) {
			cb.setOnAction(e -> refresh.run());
		}
		withInit.setOnAction(e -> refresh.run());
		refresh.run();

		Button copy = new Button("复制 apt 命令");
		copy.setMaxWidth(Double.MAX_VALUE);
		copy.getStyleClass().add("btn-primary");
		copy.setOnAction(e -> {
			String cmd = preview.getText();
			if (cmd == null || cmd.trim().isEmpty()) {
				toast("先勾选要安装的包");
				return;
			}
			copyText(cmd);
			copy.setText("已复制 apt");
			toast("apt 命令已复制到剪贴板");
		});

		Button copyPip = new Button("复制 venv pip 命令");
		copyPip.setMaxWidth(Double.MAX_VALUE);
		String pipCmd = LinuxDeps.venvPipCommand(env);
		boolean showPip = env.venv && pipCmd != null && !pipCmd.isEmpty();
		copyPip.setVisible(showPip);
		copyPip.setManaged(showPip);
		copyPip.setOnAction(e -> {
			copyText(pipCmd);
			copyPip.setText("已复制 pip");
			toast("pip 命令已复制到剪贴板");
		});

		VBox box = new VBox(10, pyHint, tip, list, withInit, cmdCaption, preview, copy, copyPip);
		box.setPadding(new Insets(12));
		VBox.setVgrow(preview, Priority.NEVER);
		d.getDialogPane().setContent(box);
		d.getDialogPane().setPrefWidth(460);
		prepare(d);
		d.showAndWait();
	}

	private static void setGrabbingUi(Dialog<?> d, Button grabBtn, TextArea notes, boolean on, String text) {
		grabBtn.setText(text);
		grabBtn.getStyleClass().remove("grabbing");
		notes.getStyleClass().remove("grabbing-notes");
		d.getDialogPane().getStyleClass().remove("grabbing-dialog");
		if (on) {
			grabBtn.getStyleClass().add("grabbing");
			notes.getStyleClass().add("grabbing-notes");
			d.getDialogPane().getStyleClass().add("grabbing-dialog");
		}
	}

	private static String extractInstallCommand(String notes) {
		if (Tools.isWindows()) {
			return "win+d";
		}
		return LinuxDeps.installCommand();
	}

	/** Linux: tool/capture_hotkey.py；Windows: tool/capture_hotkey.ps1 */
	private static String runCaptureHotkeyScript() throws Exception {
		Path root = PnatPaths.root();
		if (root == null) {
			throw new Exception("找不到包根（PNAT_ROOT）");
		}
		ProcessBuilder pb;
		if (Tools.isWindows()) {
			Path script = root.resolve("tool").resolve("capture_hotkey.ps1");
			if (!Files.isRegularFile(script)) {
				throw new Exception("缺少 " + script);
			}
			pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
					"-File", script.toAbsolutePath().toString());
		} else {
			Path script = root.resolve("tool").resolve("capture_hotkey.py");
			if (!Files.isRegularFile(script)) {
				throw new Exception("缺少 " + script);
			}
			pb = new ProcessBuilder(Tools.python3(), script.toAbsolutePath().toString());
		}
		pb.directory(root.toFile());
		pb.redirectErrorStream(false);
		Process p = pb.start();
		StringBuilder stdout = new StringBuilder();
		StringBuilder stderr = new StringBuilder();
		try (BufferedReader so = new BufferedReader(
				new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
				BufferedReader se = new BufferedReader(
						new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = so.readLine()) != null) {
				if (stdout.length() > 0) {
					stdout.append('\n');
				}
				stdout.append(line);
			}
			while ((line = se.readLine()) != null) {
				if (stderr.length() > 0) {
					stderr.append('\n');
				}
				stderr.append(line);
			}
		}
		int code = p.waitFor();
		String out = stdout.toString().trim();
		if (code == 0 && !out.isEmpty()) {
			// 只取最后一行有效组合
			String[] lines = out.split("\n");
			return lines[lines.length - 1].trim();
		}
		String err = stderr.toString().trim();
		if (err.isEmpty()) {
			err = out.isEmpty() ? ("exit " + code) : out;
		}
		throw new Exception(err);
	}

	/** 供其它类挂统一样式的对话框。 */
	public static void preparePublic(Dialog<?> d) {
		prepare(d);
	}

	/**
	 * 选 AHK 脚本：下拉现有脚本 + 可改目录。取消返回 null。
	 * @return 不含扩展名的脚本名
	 */
	public static String pickAhkScript(String title, String defaultName) {
		Dialog<String> d = new Dialog<String>();
		d.setTitle(title == null ? "AHK 脚本" : title);
		d.setHeaderText(null);
		ButtonType ok = new ButtonType("运行", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
		d.getDialogPane().getButtonTypes().addAll(ok, cancel);

		Label dirCaption = new Label("脚本目录（包根共用 res）");
		dirCaption.getStyleClass().add("hint");
		final Label dirValue = new Label(AhkPaths.displayDir());
		dirValue.setWrapText(true);
		dirValue.getStyleClass().add("hint");

		final ComboBox<String> scripts = new ComboBox<String>();
		scripts.setMaxWidth(Double.MAX_VALUE);
		scripts.setEditable(true);
		refreshScriptCombo(scripts, defaultName);

		Button changeDir = new Button("更换目录…");
		changeDir.setMaxWidth(Double.MAX_VALUE);
		changeDir.setOnAction(e -> {
			Path picked = chooseDirectory("选择 AHK 脚本文件夹", AhkPaths.scriptDir());
			if (picked == null) {
				return;
			}
			try {
				AhkPaths.saveScriptDir(picked);
				dirValue.setText(AhkPaths.displayDir());
				refreshScriptCombo(scripts, scripts.getEditor().getText());
			} catch (Exception ex) {
				warn("保存目录失败：\n" + ex.getMessage());
			}
		});

		GridPane grid = new GridPane();
		grid.setHgap(8);
		grid.setVgap(8);
		grid.setPadding(new Insets(8, 4, 4, 4));
		grid.add(dirCaption, 0, 0, 2, 1);
		grid.add(dirValue, 0, 1, 2, 1);
		grid.add(new Label("脚本"), 0, 2);
		grid.add(scripts, 1, 2);
		grid.add(changeDir, 0, 3, 2, 1);
		GridPane.setHgrow(scripts, Priority.ALWAYS);
		GridPane.setHgrow(dirValue, Priority.ALWAYS);

		d.getDialogPane().setContent(grid);
		prepare(d);
		d.getDialogPane().setPrefWidth(460);

		d.setResultConverter(btn -> {
			if (btn != ok) {
				return null;
			}
			String v = scripts.getValue();
			if (v == null || v.trim().isEmpty()) {
				v = scripts.getEditor().getText();
			}
			if (v == null || v.trim().isEmpty()) {
				return defaultName;
			}
			v = v.trim();
			if (v.toLowerCase().endsWith(".ahk")) {
				v = v.substring(0, v.length() - 4);
			}
			return v;
		});

		Optional<String> r = d.showAndWait();
		return r.orElse(null);
	}

	private static void refreshScriptCombo(ComboBox<String> box, String prefer) {
		List<String> names = AhkPaths.listScriptNames();
		box.setItems(FXCollections.observableArrayList(names));
		String pick = prefer == null ? "" : prefer.trim();
		if (pick.toLowerCase().endsWith(".ahk")) {
			pick = pick.substring(0, pick.length() - 4);
		}
		if (!pick.isEmpty() && (names.contains(pick) || box.isEditable())) {
			box.setValue(pick);
			box.getEditor().setText(pick);
		} else if (names.contains("sample")) {
			box.setValue("sample");
		} else if (!names.isEmpty()) {
			box.setValue(names.get(0));
		}
	}

	private static void show(AlertType type, String title, String header, String message) {
		Alert a = new Alert(type);
		a.setTitle(title == null ? "远程协助" : title);
		a.setHeaderText(header);
		String text = message == null ? "" : message;
		if (text.length() > 180 || text.indexOf('\n') >= 0) {
			a.setContentText(null);
			TextArea area = new TextArea(text);
			area.setEditable(false);
			area.setWrapText(true);
			area.setPrefRowCount(Math.min(14, Math.max(4, text.split("\n", -1).length + 1)));
			area.setPrefWidth(420);
			area.getStyleClass().add("dialog-body");
			a.getDialogPane().setContent(area);
		} else {
			a.setContentText(text);
		}
		prepare(a);
		a.showAndWait();
	}

	private static void prepare(Dialog<?> d) {
		Stage owner = App.getStage();
		App.setTop(false);
		if (owner != null) {
			d.initOwner(owner);
			d.initModality(Modality.WINDOW_MODAL);
		}
		DialogPane pane = d.getDialogPane();
		if (App.class.getResource("sample.css") != null) {
			pane.getStylesheets().add(App.class.getResource("sample.css").toExternalForm());
		}
		pane.getStyleClass().add("jra-dialog");
		pane.setMinWidth(360);
		pane.setPrefWidth(420);
		labelButtons(pane);
		d.setOnShown(e -> {
			Window w = pane.getScene() == null ? null : pane.getScene().getWindow();
			if (w instanceof Stage) {
				((Stage) w).setAlwaysOnTop(true);
				w.requestFocus();
			}
		});
		d.setOnHidden(e -> App.restorePreferredTop());
	}

	private static void labelButtons(DialogPane pane) {
		setBtn(pane, ButtonType.OK, "确定");
		setBtn(pane, ButtonType.CANCEL, "取消");
		setBtn(pane, ButtonType.CLOSE, "关闭");
		setBtn(pane, ButtonType.YES, "是");
		setBtn(pane, ButtonType.NO, "否");
	}

	private static void setBtn(DialogPane pane, ButtonType type, String text) {
		Button b = (Button) pane.lookupButton(type);
		if (b != null) {
			b.setText(text);
			b.setDefaultButton(type == ButtonType.OK || type == ButtonType.YES);
		}
	}
}
