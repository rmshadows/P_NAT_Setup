# Low-level keyboard hook: print one combo (win+d / ctrl+win+Right) then exit.
# Esc cancels (exit 2). Timeout 20s (exit 3).
$src = @"
using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Forms;

public class JraGrab : IDisposable {
  const int WH_KEYBOARD_LL = 13, WM_KEYDOWN = 0x0100, WM_SYSKEYDOWN = 0x0104;
  const int VK_SHIFT = 0x10, VK_CONTROL = 0x11, VK_MENU = 0x12, VK_LWIN = 0x5B, VK_RWIN = 0x5C, VK_ESCAPE = 0x1B;
  delegate IntPtr HookProc(int nCode, IntPtr wParam, IntPtr lParam);
  [DllImport("user32.dll")] static extern IntPtr SetWindowsHookEx(int id, HookProc fn, IntPtr mod, uint thr);
  [DllImport("user32.dll")] static extern bool UnhookWindowsHookEx(IntPtr hh);
  [DllImport("user32.dll")] static extern IntPtr CallNextHookEx(IntPtr hh, int n, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] static extern short GetAsyncKeyState(int v);
  [DllImport("kernel32.dll")] static extern IntPtr GetModuleHandle(string n);
  [StructLayout(LayoutKind.Sequential)] struct KBDLLHOOKSTRUCT { public uint vk; public uint scan; public uint flags; public uint time; public UIntPtr extra; }

  static IntPtr _hook;
  static HookProc _proc;
  static string _result;

  public static string Run(int timeoutMs) {
    _result = null;
    _proc = Callback;
    using (Process p = Process.GetCurrentProcess())
    using (ProcessModule m = p.MainModule) {
      _hook = SetWindowsHookEx(WH_KEYBOARD_LL, _proc, GetModuleHandle(m.ModuleName), 0);
    }
    if (_hook == IntPtr.Zero) return null;
    var t = new Timer();
    t.Interval = timeoutMs;
    t.Tick += (s, e) => { t.Stop(); Application.Exit(); };
    t.Start();
    Application.Run();
    if (_hook != IntPtr.Zero) UnhookWindowsHookEx(_hook);
    return _result;
  }

  static IntPtr Callback(int nCode, IntPtr wParam, IntPtr lParam) {
    if (nCode >= 0 && (wParam == (IntPtr)WM_KEYDOWN || wParam == (IntPtr)WM_SYSKEYDOWN)) {
      var info = (KBDLLHOOKSTRUCT)Marshal.PtrToStructure(lParam, typeof(KBDLLHOOKSTRUCT));
      int vk = (int)info.vk;
      if (vk == VK_SHIFT || vk == VK_CONTROL || vk == VK_MENU || vk == VK_LWIN || vk == VK_RWIN) {
        return CallNextHookEx(_hook, nCode, wParam, lParam);
      }
      if (vk == VK_ESCAPE) {
        _result = "";
        Application.Exit();
        return (IntPtr)1;
      }
      var sb = new StringBuilder();
      if ((GetAsyncKeyState(VK_CONTROL) & 0x8000) != 0) sb.Append("ctrl+");
      if ((GetAsyncKeyState(VK_MENU) & 0x8000) != 0) sb.Append("alt+");
      if ((GetAsyncKeyState(VK_SHIFT) & 0x8000) != 0) sb.Append("shift+");
      if ((GetAsyncKeyState(VK_LWIN) & 0x8000) != 0 || (GetAsyncKeyState(VK_RWIN) & 0x8000) != 0) sb.Append("win+");
      sb.Append(VkName(vk));
      _result = sb.ToString();
      Application.Exit();
      return (IntPtr)1;
    }
    return CallNextHookEx(_hook, nCode, wParam, lParam);
  }

  static string VkName(int vk) {
    if (vk == 0x25) return "Left";
    if (vk == 0x27) return "Right";
    if (vk == 0x26) return "Up";
    if (vk == 0x28) return "Down";
    if (vk == 0x20) return "space";
    if (vk == 0x0D) return "Return";
    if (vk == 0x09) return "Tab";
    if (vk >= 0x30 && vk <= 0x39) return ((char)vk).ToString();
    if (vk >= 0x41 && vk <= 0x5A) return ((char)('a' + (vk - 0x41))).ToString();
    if (vk >= 0x70 && vk <= 0x7B) return "F" + (vk - 0x6F);
    return ((Keys)vk).ToString();
  }

  public void Dispose() {}
}
"@

Add-Type -TypeDefinition $src -ReferencedAssemblies System.Windows.Forms | Out-Null
Write-Error "Grabbing keyboard... press a combo (Esc cancel)"
$combo = [JraGrab]::Run(20000)
if ([string]::IsNullOrWhiteSpace($combo)) {
  Write-Error "cancelled or timeout"
  exit 2
}
Write-Output $combo
exit 0
