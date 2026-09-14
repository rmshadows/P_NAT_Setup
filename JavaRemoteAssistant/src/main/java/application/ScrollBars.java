package application;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

/**
 * 滚动条：空闲隐藏，滚动 / 悬停 / 拖动时短暂显示。
 */
public final class ScrollBars {
	private ScrollBars() {
	}

	public static void installAutoHide(Parent root) {
		if (root == null) {
			return;
		}
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				installRecursive(root);
			}
		});
	}

	private static void installRecursive(Node node) {
		if (node instanceof ScrollPane) {
			wire((ScrollPane) node);
		}
		if (node instanceof Parent) {
			for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
				installRecursive(child);
			}
		}
	}

	private static void wire(ScrollPane pane) {
		if (pane.getProperties().containsKey("jra.scroll.autohide")) {
			return;
		}
		pane.getProperties().put("jra.scroll.autohide", Boolean.TRUE);
		pane.getStyleClass().add("scroll-autohide");

		final PauseTransition hide = new PauseTransition(Duration.millis(900));
		hide.setOnFinished(e -> setBarsVisible(pane, false));

		Runnable showBriefly = new Runnable() {
			@Override
			public void run() {
				setBarsVisible(pane, true);
				hide.stop();
				hide.playFromStart();
			}
		};

		setBarsVisible(pane, false);
		pane.addEventFilter(ScrollEvent.SCROLL, e -> showBriefly.run());
		pane.vvalueProperty().addListener((o, a, b) -> showBriefly.run());
		pane.hvalueProperty().addListener((o, a, b) -> showBriefly.run());

		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				for (Node n : pane.lookupAll(".scroll-bar")) {
					if (!(n instanceof ScrollBar)) {
						continue;
					}
					ScrollBar bar = (ScrollBar) n;
					bar.setOpacity(0);
					bar.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
						hide.stop();
						setBarsVisible(pane, true);
					});
					bar.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
						if (!bar.isPressed()) {
							hide.playFromStart();
						}
					});
					bar.pressedProperty().addListener((obs, was, now) -> {
						if (Boolean.TRUE.equals(now)) {
							hide.stop();
							setBarsVisible(pane, true);
						} else {
							hide.playFromStart();
						}
					});
				}
			}
		});
	}

	private static void setBarsVisible(ScrollPane pane, boolean on) {
		for (Node n : pane.lookupAll(".scroll-bar")) {
			n.setOpacity(on ? 1 : 0);
			// 隐藏时仍可命中，方便鼠标移到右边条再唤出
			n.setMouseTransparent(false);
		}
	}
}
