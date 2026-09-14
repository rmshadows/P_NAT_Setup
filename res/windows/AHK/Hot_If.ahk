#HotIf WinActive("ahk_class Notepad")
^a::MsgBox "你在记事本中按下了 Ctrl-A. 而在其他窗口中按下 Ctrl-A 将原样发送."
#c::MsgBox "你在记事本中按下了 Win-C."

#HotIf
#c::MsgBox "你在非记事本程序中按下了 Win-C."

#HotIf MouseIsOver("ahk_class Shell_TrayWnd") ; 有关 MouseIsOver, 请参阅 #HotIf 的示例 1.
WheelUp::Send "{Volume_Up}"     ; 在任务栏上滚动滚轮: 增加/减小音量.
WheelDown::Send "{Volume_Down}" ;