; 下面这个例子表示按下一个键的时候再按下另一个键(或多个键)..
; 如果其中一个方法不奏效, 试试另一个.
Send "^s"                     ; 表示发送 CTRL+S
Send "{Ctrl down}s{Ctrl up}"  ; 表示发送 CTRL+S
Send "{Ctrl down}c{Ctrl up}"
Send "{b down}{b up}"
Send "{Tab down}{Tab up}"
Send "{Up down}"  ; 按下向上键.
Sleep 1000        ; 保持 1 秒.
Send "{Up up}"    ; 然后松开向上键.