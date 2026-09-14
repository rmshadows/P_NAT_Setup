package application;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

/**
 * JRA设置：体验版 / 托盘 / AHK。引擎与部署参数在加载按钮右击。
 */
public class SettingsController implements Initializable {
	@FXML
	private CheckBox dryRun;
	@FXML
	private ComboBox<String> simulateOs;
	@FXML
	private CheckBox tray;
	@FXML
	private CheckBox closeExit;
	@FXML
	private CheckBox useShellEnv;
	@FXML
	private Label useShellEnvHint;
	@FXML
	private ComboBox<Tools.SourceChoice> shellSource;
	@FXML
	private Button pickShellSource;
	@FXML
	private Label shellEnvSection;
	@FXML
	private Label ahkDirLabel;
	@FXML
	private TextArea previewOut;
	@FXML
	private Button linuxDepsBtn;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		simulateOs.setItems(FXCollections.observableArrayList("auto", "linux", "windows"));

		AppConf c = AppConf.load();
		dryRun.setSelected("1".equals(c.get("dry_run")));
		select(simulateOs, c.get("simulate_os"), "auto");
		tray.setSelected(!"0".equals(c.get("tray")));
		if (closeExit != null) {
			closeExit.setSelected("0".equals(c.get("close_to_tray")));
		}
		if (useShellEnv != null) {
			useShellEnv.setSelected("1".equals(c.get("use_shell_env")));
			useShellEnv.selectedProperty().addListener((obs, o, n) -> refreshShellSourceEnabled());
		}
		fillShellSource(c.get("shell_source"));
		refreshShellSourceEnabled();
		if (Tools.isWindows()) {
			hide(shellEnvSection);
			hide(useShellEnv);
			hide(useShellEnvHint);
			hide(shellSource);
			hide(pickShellSource);
		}
		refreshAhkLabel();
		if (linuxDepsBtn != null) {
			if (Tools.isWindows()) {
				linuxDepsBtn.setVisible(false);
				linuxDepsBtn.setManaged(false);
			} else {
				linuxDepsBtn.setText(LinuxDeps.buttonLabel());
			}
		}
		previewOut.setText("引擎与部署：主界面加载按钮右箭头选方式，右击打开高级设置。");
	}

	private void refreshAhkLabel() {
		if (ahkDirLabel == null) {
			return;
		}
		ahkDirLabel.setText("当前：" + AhkPaths.displayDir() + "\nAHK.exe：" + AhkPaths.exe());
	}

	private static void select(ComboBox<String> box, String value, String fallback) {
		String v = value == null || value.isEmpty() ? fallback : value;
		if (!box.getItems().contains(v)) {
			box.getItems().add(v);
		}
		box.getSelectionModel().select(v);
	}

	@FXML
	void openLinuxDeps(ActionEvent e) {
		Dialogs.showLinuxDeps();
		if (linuxDepsBtn != null && !Tools.isWindows()) {
			linuxDepsBtn.setText(LinuxDeps.buttonLabel());
		}
	}

	@FXML
	void pickAhkDir(ActionEvent e) throws IOException {
		Path picked = Dialogs.chooseDirectory("选择 AHK 脚本文件夹", AhkPaths.scriptDir());
		if (picked == null) {
			return;
		}
		AhkPaths.saveScriptDir(picked);
		refreshAhkLabel();
		previewOut.setText("已记住 ahk_dir=\n" + picked);
	}

	@FXML
	void preview(ActionEvent e) {
		previewOut.setText(DryRun.plan("load"));
		System.out.println(previewOut.getText());
	}

	@FXML
	void previewAdvanced(ActionEvent e) {
		previewOut.setText(DryRun.plan("advanced"));
		System.out.println(previewOut.getText());
	}

	private void applyFormTo(AppConf c) {
		c.set("dry_run", dryRun.isSelected() ? "1" : "0");
		c.set("simulate_os", value(simulateOs, "auto"));
		c.set("tray", tray.isSelected() ? "1" : "0");
		if (closeExit != null) {
			c.set("close_to_tray", closeExit.isSelected() ? "0" : "1");
		}
		if (useShellEnv != null && !Tools.isWindows()) {
			c.set("use_shell_env", useShellEnv.isSelected() ? "1" : "0");
			c.set("shell_source", selectedShellSourceValue());
		}
	}

	private void fillShellSource(String conf) {
		if (shellSource == null || Tools.isWindows()) {
			return;
		}
		String want = conf == null || conf.trim().isEmpty() ? Tools.SHELL_SOURCE_AUTO : conf.trim();
		List<Tools.SourceChoice> items = Tools.shellSourceChoices();
		boolean found = false;
		for (Tools.SourceChoice sc : items) {
			if (Tools.sameShellSource(sc.value, want)) {
				found = true;
				break;
			}
		}
		if (!found && !Tools.SHELL_SOURCE_AUTO.equalsIgnoreCase(want)) {
			Path p = Tools.expandShellSource(want).toAbsolutePath().normalize();
			String label = Tools.tildeHome(p);
			if (label == null || label.isEmpty()) {
				label = want;
			}
			items.add(new Tools.SourceChoice(p.toString(), label + "  〔自选〕"));
		}
		shellSource.setItems(FXCollections.observableArrayList(items));
		Tools.SourceChoice pick = items.get(0);
		for (Tools.SourceChoice sc : items) {
			if (Tools.sameShellSource(sc.value, want)) {
				pick = sc;
				break;
			}
		}
		shellSource.getSelectionModel().select(pick);
	}

	private void refreshShellSourceEnabled() {
		boolean on = useShellEnv != null && useShellEnv.isSelected() && !Tools.isWindows();
		if (shellSource != null) {
			shellSource.setDisable(!on);
		}
		if (pickShellSource != null) {
			pickShellSource.setDisable(!on);
		}
	}

	private String selectedShellSourceValue() {
		if (shellSource == null) {
			return Tools.SHELL_SOURCE_AUTO;
		}
		Tools.SourceChoice sc = shellSource.getSelectionModel().getSelectedItem();
		if (sc == null || sc.value == null || sc.value.isEmpty()) {
			return Tools.SHELL_SOURCE_AUTO;
		}
		if (Tools.SHELL_SOURCE_AUTO.equalsIgnoreCase(sc.value)) {
			return Tools.SHELL_SOURCE_AUTO;
		}
		return Tools.tildeHome(Tools.expandShellSource(sc.value));
	}

	@FXML
	void pickShellSource(ActionEvent e) {
		Path initial = Tools.resolvedShellSource();
		Path picked = Dialogs.chooseFile("选择要 source 的文件", initial);
		if (picked == null || shellSource == null) {
			return;
		}
		String value = picked.toAbsolutePath().normalize().toString();
		Tools.SourceChoice existing = null;
		for (Tools.SourceChoice sc : shellSource.getItems()) {
			if (Tools.sameShellSource(sc.value, value)) {
				existing = sc;
				break;
			}
		}
		if (existing == null) {
			existing = new Tools.SourceChoice(value, Tools.tildeHome(picked) + "  〔自选〕");
			shellSource.getItems().add(existing);
		}
		shellSource.getSelectionModel().select(existing);
	}

	private static void hide(javafx.scene.Node n) {
		if (n == null) {
			return;
		}
		n.setVisible(false);
		n.setManaged(false);
	}

	@FXML
	void save(ActionEvent e) {
		try {
			AppConf c = AppConf.load();
			applyFormTo(c);
			c.save();
			Tools.invalidatePythonCache();
			App.refreshTitle();
			App.setRoot("primary");
		} catch (Exception ex) {
			ex.printStackTrace();
			Dialogs.error("保存失败", ex.getMessage() == null ? ex.toString() : ex.getMessage());
		}
	}

	private static String value(ComboBox<String> box, String fallback) {
		String v = box.getSelectionModel().getSelectedItem();
		return v == null || v.isEmpty() ? fallback : v;
	}

	@FXML
	void back(ActionEvent e) throws IOException {
		App.setRoot("primary");
	}
}
