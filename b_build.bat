REM 请先在Linux运行build.sh后，再在Windows上运行此脚本
cd RELEASE
:: frp
cd FRP_AUTO
CALL PyinstallerFRP.bat
cd ..
:: Tight VNC
cd TIGHTVNC_AUTO
CALL PyinstallerTightVNC.bat
cd ..
:: ZeroTier
cd ZEROTIER_AUTO
CALL PyinstallerZeroTier.bat
cd ..
cd ..
echo ""
echo ""
echo "Success."
pause


