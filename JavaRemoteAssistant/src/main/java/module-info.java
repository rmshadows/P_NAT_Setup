/**
 * Java远程桌面助手
 * 基于TightVNC的远程协作服务助手，JRA部署于被控端。控制端VNC_Viewer请自行到TightVNC下载。
 * @author Ryan Yim
 */
module application {
	requires java.desktop;
	requires javafx.controls;
	requires javafx.fxml;
	requires transitive javafx.graphics;

	opens application to javafx.fxml;
	
	exports application;
}