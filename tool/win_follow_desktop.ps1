# Move JRA windows onto the CURRENT Windows virtual desktop.
# Finds by PID and by title. Includes hidden/owned top-level HWNDs.
param(
  [int]$ProcId = 0,
  [string]$Title = "远程协助",
  [switch]$QueryId,
  [switch]$QueryPos
)

# 读资源管理器记下的桌面列表（1 起算）。不编译 C#。当前 GUID 对不上时仍输出总数（当前当作 1）。
if ($QueryPos) {
  function Emit-Pos($ids, $cur) {
    if ($null -eq $ids -or $ids -isnot [byte[]] -or $ids.Length -lt 16) { return $false }
    $num = [int]($ids.Length / 16)
    $idx = -1
    if ($cur -is [byte[]] -and $cur.Length -ge 16) {
      for ($i = 0; $i -lt $num; $i++) {
        $ok = $true
        for ($j = 0; $j -lt 16; $j++) {
          if ($ids[$i * 16 + $j] -ne $cur[$j]) { $ok = $false; break }
        }
        if ($ok) { $idx = $i; break }
      }
    } elseif ($cur -is [string] -and $cur) {
      try {
        $g = [guid]$cur
        for ($i = 0; $i -lt $num; $i++) {
          $slice = New-Object byte[] 16
          [Array]::Copy($ids, $i * 16, $slice, 0, 16)
          if ((New-Object Guid (,$slice)) -eq $g) { $idx = $i; break }
        }
      } catch { }
    }
    if ($idx -lt 0) { $idx = 0 }
    Write-Output ("{0} {1}" -f ($idx + 1), $num)
    return $true
  }
  $paths = @(
    'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\VirtualDesktops'
  )
  $sid = [System.Diagnostics.Process]::GetCurrentProcess().SessionId
  $paths += "HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\SessionInfo\$sid\VirtualDesktops"
  foreach ($p in $paths) {
    if (-not (Test-Path $p)) { continue }
    $prop = Get-ItemProperty $p -ErrorAction SilentlyContinue
    if (-not $prop) { continue }
    if (Emit-Pos $prop.VirtualDesktopIDs $prop.CurrentVirtualDesktop) { exit 0 }
  }
  $deskRoot = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\VirtualDesktops\Desktops'
  if (Test-Path $deskRoot) {
    $ks = @(Get-ChildItem $deskRoot)
    if ($ks.Count -gt 0) {
      $idx = 0
      $prop = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\VirtualDesktops' -ErrorAction SilentlyContinue
      $cur = $null
      if ($prop) { $cur = $prop.CurrentVirtualDesktop }
      if ($cur -is [byte[]] -and $cur.Length -ge 16) {
        try {
          $g = New-Object Guid (,$cur)
          for ($i = 0; $i -lt $ks.Count; $i++) {
            try { if ([guid]$ks[$i].PSChildName -eq $g) { $idx = $i; break } } catch { }
          }
        } catch { }
      }
      Write-Output ("{0} {1}" -f ($idx + 1), $ks.Count)
      exit 0
    }
  }
  exit 2
}

$src = @"
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Forms;

public static class JraDesk {
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
  [Guid("a5cd92ff-29be-454c-8d04-d82879fb3f1b")]
  interface IVirtualDesktopManager {
    int IsWindowOnCurrentVirtualDesktop(IntPtr w, out int onCurrent);
    int GetWindowDesktopId(IntPtr w, out Guid desktopId);
    int MoveWindowToDesktop(IntPtr w, ref Guid desktopId);
  }
  [ComImport, Guid("aa509086-5ca9-4c25-8f95-589d3c07b48a")]
  class VirtualDesktopManager {}

  public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
  [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc cb, IntPtr lp);
  [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] static extern IntPtr GetAncestor(IntPtr h, uint ga);
  [DllImport("user32.dll")] static extern bool IsWindow(IntPtr h);

  public static int MoveToCurrentDesktop(int pid, string titlePart) {
    var vdm = (IVirtualDesktopManager)new VirtualDesktopManager();
    var probe = new Form();
    probe.FormBorderStyle = FormBorderStyle.None;
    probe.ShowInTaskbar = false;
    probe.Opacity = 0.01;
    probe.Width = 8;
    probe.Height = 8;
    probe.StartPosition = FormStartPosition.Manual;
    probe.Left = 40;
    probe.Top = 40;
    probe.Show();
    Application.DoEvents();
    System.Threading.Thread.Sleep(80);
    Guid desk;
    int hr = vdm.GetWindowDesktopId(probe.Handle, out desk);
    if (hr != 0 || desk == Guid.Empty) {
      probe.Close();
      return 2;
    }
    int moved = 0;
    EnumWindows((h, l) => {
      IntPtr root = GetAncestor(h, 2);
      if (root == IntPtr.Zero) root = h;
      if (!IsWindow(root)) return true;
      uint wpid;
      GetWindowThreadProcessId(root, out wpid);
      bool pidOk = pid > 0 && (int)wpid == pid;
      bool titleOk = false;
      if (!string.IsNullOrEmpty(titlePart)) {
        var sb = new StringBuilder(512);
        GetWindowText(root, sb, sb.Capacity);
        titleOk = sb.ToString().IndexOf(titlePart, StringComparison.OrdinalIgnoreCase) >= 0;
      }
      if (!pidOk && !titleOk) return true;
      Guid g = desk;
      if (vdm.MoveWindowToDesktop(root, ref g) == 0) moved++;
      return true;
    }, IntPtr.Zero);
    probe.Close();
    return moved > 0 ? 0 : 3;
  }

  public static string CurrentId() {
    var vdm = (IVirtualDesktopManager)new VirtualDesktopManager();
    var probe = new Form();
    probe.FormBorderStyle = FormBorderStyle.None;
    probe.ShowInTaskbar = false;
    probe.Opacity = 0.01;
    probe.Width = 8;
    probe.Height = 8;
    probe.StartPosition = FormStartPosition.Manual;
    probe.Left = 40;
    probe.Top = 40;
    probe.Show();
    Application.DoEvents();
    System.Threading.Thread.Sleep(80);
    Guid desk;
    int hr = vdm.GetWindowDesktopId(probe.Handle, out desk);
    probe.Close();
    if (hr != 0 || desk == Guid.Empty) return "";
    return desk.ToString();
  }
}
"@

Add-Type -TypeDefinition $src -ReferencedAssemblies System.Windows.Forms,System.Drawing -ErrorAction Stop | Out-Null
if ($QueryId) {
  $id = [JraDesk]::CurrentId()
  if ([string]::IsNullOrEmpty($id)) { exit 2 }
  Write-Output $id
  exit 0
}
$code = [JraDesk]::MoveToCurrentDesktop($ProcId, $Title)
if ($code -eq 0) { Write-Output "ok" } else { Write-Error "follow-desktop exit $code" }
exit $code
