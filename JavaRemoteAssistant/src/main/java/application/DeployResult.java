package application;

/** 部署/启停结果，给 GUI 提示用。 */
public final class DeployResult {
	public final boolean ok;
	public final int code;
	public final String message;

	public DeployResult(boolean ok, int code, String message) {
		this.ok = ok;
		this.code = code;
		this.message = message == null ? "" : message;
	}

	public static DeployResult ok() {
		return new DeployResult(true, 0, "");
	}

	public static DeployResult fail(int code, String message) {
		return new DeployResult(false, code, message);
	}

	public static DeployResult fail(String message) {
		return fail(1, message);
	}
}
