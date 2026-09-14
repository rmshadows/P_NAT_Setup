#!/bin/sh
# 把本进程窗口迁到指定/当前 X11 工作区。
# 角色对照 tool/win_follow_desktop.ps1（那边是 IVirtualDesktopManager.MoveWindowToDesktop）。
# 这里用 EWMH _NET_WM_DESKTOP：优先 xdotool，其次 wmctrl。
#
#   linux_follow_desktop.sh current
#   linux_follow_desktop.sh count
#   linux_follow_desktop.sh move <pid> [title] [desktop]
# desktop 省略 = 当前工作区（0 起算）

action="${1:-move}"

if [ "${XDG_SESSION_TYPE}" = "wayland" ]; then
	echo "wayland: xdotool/wmctrl 不能切真工作区" >&2
	exit 5
fi

have() {
	command -v "$1" >/dev/null 2>&1
}

wm_current() {
	if have xdotool; then
		xdotool get_desktop
		return 0
	fi
	if have wmctrl; then
		wmctrl -d | awk '/\*/ { print $1; exit }'
		return 0
	fi
	return 1
}

wm_count() {
	if have xdotool; then
		xdotool get_num_desktops
		return 0
	fi
	if have wmctrl; then
		wmctrl -d | awk 'END { print NR }'
		return 0
	fi
	return 1
}

wm_ids() {
	pid="$1"
	title="$2"
	if have xdotool; then
		ids=$(xdotool search --pid "$pid" 2>/dev/null || true)
		if [ -z "$ids" ] && [ -n "$title" ]; then
			ids=$(xdotool search --name "$title" 2>/dev/null || true)
		fi
		printf '%s\n' "$ids"
		return 0
	fi
	if have wmctrl; then
		wmctrl -lp | awk -v pid="$pid" -v t="$title" '
			$3 == pid { print $1 }
			t != "" && index($0, t) { print $1 }
		'
		return 0
	fi
	return 1
}

wm_move() {
	id="$1"
	desk="$2"
	if have xdotool; then
		xdotool set_desktop_for_window "$id" "$desk"
		return $?
	fi
	if have wmctrl; then
		wmctrl -i -r "$id" -t "$desk"
		return $?
	fi
	return 1
}

case "$action" in
	current)
		wm_current
		;;
	count)
		wm_count
		;;
	goto)
		desk="$2"
		if [ -z "$desk" ]; then
			exit 2
		fi
		if have xdotool; then
			xdotool set_desktop "$desk"
			exit $?
		fi
		if have wmctrl; then
			wmctrl -s "$desk"
			exit $?
		fi
		exit 4
		;;
	move)
		pid="$2"
		title="${3:-远程协助}"
		desk="$4"
		if [ -z "$desk" ]; then
			desk=$(wm_current) || exit 4
		fi
		ids=$(wm_ids "$pid" "$title") || exit 4
		ok=1
		for id in $ids; do
			[ -n "$id" ] || continue
			if wm_move "$id" "$desk"; then
				ok=0
			fi
		done
		exit "$ok"
		;;
	*)
		echo "usage: linux_follow_desktop.sh current|count|goto <n>|move <pid> [title] [desktop]" >&2
		exit 2
		;;
esac
