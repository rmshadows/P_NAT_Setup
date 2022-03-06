'拖动文件到vbs上打开可以后台运行
Dim WshShell
Set WshShell = WScript.CreateObject("Wscript.Shell")
WshShell.CurrentDirectory = WScript.Arguments(0) & "\.."
For Each ar In WScript.Arguments
filename=ar
Next
'Msgbox filename
'0表示后台 true表示等待运行结束
a = WshShell.run (filename,0,true)

'运行后写入文件
Dim fso,tf
Set fso =  CreateObject("Scripting.FileSystemObject")
'True-代表可以被下次写入覆盖
Set tf = fso.CreateTextFile("r.txt", True) 
tf.WriteLine(a)
tf.close
