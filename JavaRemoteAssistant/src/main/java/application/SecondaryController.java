package application;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * 帮助界面的控制器
 * @author Ryan Yim
 *
 */
public class SecondaryController implements Initializable{

	@FXML
	private Label Help;

	@FXML
	private Button Back2main;
	
	/**
	 * 返回上级（主菜单）
	 * @throws IOException
	 */
	@FXML
	private void switchToPrimary() throws IOException {
		App.setRoot("primary");
	}
	
	/**
	 * 初始化
	 */
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		// TODO 自动生成的方法存根
		final String HELP = "版本 " + App.version + "\n\n"
				+ "右箭头选方式，左键「加载远程桌面」。\n"
				+ "Dayon 才分受助端 / 协助端（两套程序）。VNC 是本机 Server，对方 Viewer。\n"
				+ "Linux：Dayon / VNC。Windows 另有 RDP 占位。\n"
				+ "右击加载按钮：当前方式的高级设置。\n"
				+ "下拉里右击某一项：该方式的高级设置。\n"
				+ "VNC（Linux）：:0 当前屏；高级设置选反向连出或等待连入，以及加密。\n"
				+ "默认 Linux→Dayon，Windows→TightVNC（VNC）。\n"
				+ "Linux VNC :1 虚拟桌面尚未接入。\n"
				+ "Windows「高级部署…」：TightVNC 装系统服务。\n\n"
				+ "本机辅助：系统监视器 / 显示桌面 / 工作区等，\n"
				+ "默认秒数可滚轮改。Linux 会调本机程序。\n\n"
				+ "按住修饰键：远程传不好 Ctrl 等时，\n"
				+ "在被控端点一下代替按住；用「松开全部」复位。\n\n"
				+ "AHK 脚本：仅 Windows（AutoHotkey），Linux 不显示。\n"
				+ "鼠标停在按钮上有说明。";
		Help.setText(HELP);
	}
}